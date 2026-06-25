package com.fintech.rag.usage;

import com.fintech.rag.web.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

/**
 * Records LLM token usage for every call on the ChatClient it is registered on.
 *
 * <p>One instance is created per feature ("dispute" / "policy") in {@link com.fintech.rag.config.AiConfig}
 * and added to that client's default advisors. As the outermost advisor (HIGHEST_PRECEDENCE),
 * it wraps the whole chain, lets the model call complete, then reads the {@link Usage} off the
 * final response and writes one {@code token_usage} row.
 *
 * <p>The userId comes from {@link UserContext} (set by MeteringFilter on the request thread).
 * Calls without an HTTP origin (e.g. MCP tool invocations) fall back to the "anonymous" userId —
 * they are still metered for visibility but are not subject to the HTTP quota gate.
 */
@Slf4j
public class TokenUsageAdvisor implements CallAdvisor {

    private static final String ANONYMOUS = "anonymous";

    private final TokenUsageRepository repository;
    private final String feature;

    public TokenUsageAdvisor(TokenUsageRepository repository, String feature) {
        this.repository = repository;
        this.feature = feature;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);

        try {
            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse != null && chatResponse.getMetadata() != null
                    && chatResponse.getMetadata().getUsage() != null) {

                Usage usage = chatResponse.getMetadata().getUsage();
                String userId = UserContext.getUserId().orElse(ANONYMOUS);
                String model = chatResponse.getMetadata().getModel();

                repository.record(userId, feature,
                        nz(usage.getPromptTokens()),
                        nz(usage.getCompletionTokens()),
                        nz(usage.getTotalTokens()),
                        model);

                log.debug("Recorded token usage. userId={} feature={} total={}",
                        userId, feature, usage.getTotalTokens());
            }
        } catch (Exception e) {
            // Metering must never break the user-facing call.
            log.warn("Failed to record token usage for feature={}: {}", feature, e.getMessage());
        }

        return response;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    @Override
    public String getName() {
        return "TokenUsageAdvisor-" + feature;
    }

    @Override
    public int getOrder() {
        // Outermost — wrap the entire advisor chain so we observe the final, complete response.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
