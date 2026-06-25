package com.fintech.rag.usage;

import com.fintech.rag.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Pre-call quota gate. Evaluates two dimensions against the durable {@code token_usage}
 * history before an LLM call is allowed:
 *
 * <ol>
 *   <li><b>Request rate</b> — max requests per rolling minute (cheap burst protection).</li>
 *   <li><b>Token budget</b> — max total tokens per rolling 24 hours (the real cost guard).</li>
 * </ol>
 *
 * <p>Both checks read prior recorded rows, so they gate based on usage up to (but not
 * including) the current in-flight request — the row for the current call is written
 * afterwards by TokenUsageAdvisor. Because token cost is only known post-call, the token
 * budget is enforced as "block the next request once the user is already over" rather than
 * pre-charging.
 */
@Slf4j
@Service
public class QuotaService {

    private final TokenUsageRepository repository;
    private final AppProperties props;

    public QuotaService(TokenUsageRepository repository, AppProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public Decision check(String userId) {
        int maxReqs = props.getQuota().getMaxRequestsPerMinute();
        int reqs = repository.countRequestsLastMinute(userId);
        if (reqs >= maxReqs) {
            log.warn("Rate limit hit. userId={} requestsLastMinute={} max={}", userId, reqs, maxReqs);
            return Decision.deny(
                    "Rate limit exceeded: max %d requests/minute (you have made %d).".formatted(maxReqs, reqs));
        }

        long maxTokens = props.getQuota().getMaxTokensPerDay();
        long tokens = repository.sumTokensLast24h(userId);
        if (tokens >= maxTokens) {
            log.warn("Token budget hit. userId={} tokensLast24h={} max={}", userId, tokens, maxTokens);
            return Decision.deny(
                    "Daily token budget exceeded: %d/%d tokens used in the last 24h.".formatted(tokens, maxTokens));
        }

        return Decision.permit();
    }

    public record Decision(boolean allowed, String reason) {
        static Decision permit() { return new Decision(true, null); }
        static Decision deny(String reason) { return new Decision(false, reason); }
    }
}
