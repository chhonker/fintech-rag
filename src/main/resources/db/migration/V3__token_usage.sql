-- Per-call LLM token accounting. One row is written by TokenUsageAdvisor after
-- every dispute/policy ChatClient call. Used for both monitoring and quota enforcement.
CREATE TABLE token_usage (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           VARCHAR(128) NOT NULL,
    feature           VARCHAR(32)  NOT NULL,            -- 'dispute' | 'policy'
    prompt_tokens     INTEGER      NOT NULL DEFAULT 0,
    completion_tokens INTEGER      NOT NULL DEFAULT 0,
    total_tokens      INTEGER      NOT NULL DEFAULT 0,
    model             VARCHAR(64),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Supports the quota queries: rows for a given user within a recent time window.
CREATE INDEX idx_token_usage_user_time ON token_usage (user_id, created_at);
