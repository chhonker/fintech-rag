package com.fintech.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Application-specific configuration bound from the {@code app.*} block in application.yml.
 *
 * <ul>
 *   <li>{@code app.security.api-keys} — map of API key → userId, used by MeteringFilter to
 *       authenticate metered endpoints and attribute token usage to a user.</li>
 *   <li>{@code app.quota.*} — per-user request-rate and daily token-budget limits enforced
 *       before each dispute/policy call.</li>
 * </ul>
 */
@Component
@ConfigurationProperties("app")
public class AppProperties {

    private Security security = new Security();
    private Quota quota = new Quota();

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Quota getQuota() { return quota; }
    public void setQuota(Quota quota) { this.quota = quota; }

    public static class Security {
        /** API key (header value) → userId. */
        private Map<String, String> apiKeys = new HashMap<>();

        public Map<String, String> getApiKeys() { return apiKeys; }
        public void setApiKeys(Map<String, String> apiKeys) { this.apiKeys = apiKeys; }
    }

    public static class Quota {
        /** Max requests a single user may make to metered endpoints within a rolling minute. */
        private int maxRequestsPerMinute = 20;

        /** Max total tokens a single user may consume within a rolling 24-hour window. */
        private long maxTokensPerDay = 100_000;

        public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
        public void setMaxRequestsPerMinute(int maxRequestsPerMinute) { this.maxRequestsPerMinute = maxRequestsPerMinute; }

        public long getMaxTokensPerDay() { return maxTokensPerDay; }
        public void setMaxTokensPerDay(long maxTokensPerDay) { this.maxTokensPerDay = maxTokensPerDay; }
    }
}
