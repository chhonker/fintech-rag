package com.fintech.rag.web;

import java.util.Optional;

/**
 * Thread-local holder for the authenticated userId of the current request.
 *
 * <p>Set by {@link MeteringFilter} at the start of a metered request and cleared in a
 * finally block when the request completes. Because the dispute/policy ChatClient calls
 * run synchronously on the same request thread, {@code TokenUsageAdvisor} can read the
 * userId here without it being threaded through every method signature.
 *
 * <p>For calls that don't originate from an HTTP request (e.g. MCP tool invocations),
 * the value is absent and callers fall back to a sentinel userId.
 */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {}

    public static void setUserId(String userId) {
        CURRENT_USER.set(userId);
    }

    public static Optional<String> getUserId() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
