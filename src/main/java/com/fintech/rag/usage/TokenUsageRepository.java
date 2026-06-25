package com.fintech.rag.usage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

/**
 * Persistence + aggregate queries for the {@code token_usage} table.
 * Uses JdbcTemplate to match the existing data-access style in this project.
 */
@Repository
public class TokenUsageRepository {

    private final JdbcTemplate jdbc;

    public TokenUsageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String userId, String feature,
                       int promptTokens, int completionTokens, int totalTokens, String model) {
        jdbc.update("""
                INSERT INTO fintech_app.token_usage
                    (user_id, feature, prompt_tokens, completion_tokens, total_tokens, model)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, feature, promptTokens, completionTokens, totalTokens, model);
    }

    /** Number of recorded calls for this user in the last rolling minute. */
    public int countRequestsLastMinute(String userId) {
        Integer c = jdbc.queryForObject("""
                SELECT COUNT(*) FROM fintech_app.token_usage
                WHERE user_id = ? AND created_at > now() - interval '1 minute'
                """, Integer.class, userId);
        return c == null ? 0 : c;
    }

    /** Sum of total tokens consumed by this user in the last rolling 24 hours. */
    public long sumTokensLast24h(String userId) {
        Long s = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_tokens), 0) FROM fintech_app.token_usage
                WHERE user_id = ? AND created_at > now() - interval '24 hours'
                """, Long.class, userId);
        return s == null ? 0 : s;
    }

    /** Snapshot of a user's current-window usage, for the self-service usage endpoint. */
    public Map<String, Object> snapshot(String userId) {
        return jdbc.queryForMap("""
                SELECT
                    ? AS user_id,
                    (SELECT COUNT(*) FROM fintech_app.token_usage
                       WHERE user_id = ? AND created_at > now() - interval '1 minute') AS requests_last_minute,
                    (SELECT COALESCE(SUM(total_tokens), 0) FROM fintech_app.token_usage
                       WHERE user_id = ? AND created_at > now() - interval '24 hours') AS tokens_last_24h
                """, userId, userId, userId);
    }
}
