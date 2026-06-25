package com.fintech.rag.controller;

import com.fintech.rag.usage.TokenUsageRepository;
import com.fintech.rag.web.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Self-service token-usage lookup. Requires the same {@code X-API-Key} header as the
 * metered endpoints; returns the calling user's current-window consumption so clients
 * can see how close they are to their quota.
 */
@Tag(name = "Usage", description = "Per-user token consumption against the configured quotas")
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final TokenUsageRepository repository;

    public UsageController(TokenUsageRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Get my current token usage",
            description = "Returns the authenticated user's request count in the last minute and "
                    + "total tokens consumed in the last 24 hours. Authenticate with the X-API-Key header."
    )
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> myUsage() {
        // UserContext is populated by MeteringFilter and still set during controller execution.
        String userId = UserContext.getUserId().orElse("anonymous");
        return ResponseEntity.ok(repository.snapshot(userId));
    }
}
