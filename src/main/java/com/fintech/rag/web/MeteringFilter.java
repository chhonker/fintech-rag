package com.fintech.rag.web;

import com.fintech.rag.config.AppProperties;
import com.fintech.rag.usage.QuotaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates metered endpoints via the {@code X-API-Key} header and enforces the
 * per-user quota gate before the request reaches the controller.
 *
 * <p>Scope (see {@link #shouldNotFilter}):
 * <ul>
 *   <li>{@code /api/dispute/**}, {@code /api/policy/**} — authenticated AND quota-gated</li>
 *   <li>{@code /api/usage/**} — authenticated only (so users can always check their own usage)</li>
 *   <li>everything else (ingestion, swagger, actuator) — untouched</li>
 * </ul>
 */
@Slf4j
@Component
public class MeteringFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final AppProperties props;
    private final QuotaService quotaService;

    public MeteringFilter(AppProperties props, QuotaService quotaService) {
        this.props = props;
        this.quotaService = quotaService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean metered = path.startsWith("/api/dispute")
                || path.startsWith("/api/policy")
                || path.startsWith("/api/usage");
        return !metered;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);
        String userId = (apiKey == null) ? null : props.getSecurity().getApiKeys().get(apiKey);

        if (userId == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Missing or invalid " + API_KEY_HEADER + " header.");
            return;
        }

        UserContext.setUserId(userId);
        try {
            // Quota gate applies only to the LLM-backed endpoints, not the usage lookup.
            String path = request.getServletPath();
            boolean quotaGated = path.startsWith("/api/dispute") || path.startsWith("/api/policy");
            if (quotaGated) {
                QuotaService.Decision decision = quotaService.check(userId);
                if (!decision.allowed()) {
                    response.setHeader("Retry-After", "60");
                    writeError(response, HttpStatus.TOO_MANY_REQUESTS, decision.reason());
                    return;
                }
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"%s\", \"message\": \"%s\"}"
                .formatted(status.getReasonPhrase(), message.replace("\"", "'")));
    }
}
