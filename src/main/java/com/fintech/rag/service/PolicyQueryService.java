package com.fintech.rag.service;

import com.fintech.rag.dto.DocumentSource;
import com.fintech.rag.dto.PolicyAskRequest;
import com.fintech.rag.dto.PolicyAskResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Handles general policy Q&A with multi-turn conversation memory.
 *
 * <p>The injected {@code policyChatClient} (from {@link com.fintech.rag.config.AiConfig})
 * already has two advisors pre-wired:
 * <ol>
 *   <li>{@code MessageChatMemoryAdvisor} – injects prior turns for the given conversationId</li>
 *   <li>{@code QuestionAnswerAdvisor}    – retrieves the top-4 relevant policy chunks from pgvector</li>
 * </ol>
 *
 * <p>The {@code ChatMemory} bean is also injected here solely for the
 * {@link #clearSession(String)} operation, so callers can explicitly wipe a session.
 */
@Slf4j
@Service
public class PolicyQueryService {

    // "chat_memory_conversation_id" — the key MessageChatMemoryAdvisor reads
    // to look up (and store) messages for a specific conversation.
    private static final String CONVERSATION_ID_KEY = "chat_memory_conversation_id";

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public PolicyQueryService(@Qualifier("policyChatClient") ChatClient chatClient,
                              ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    /**
     * Answers a policy question, maintaining conversation history across calls.
     *
     * <p>If {@code request.conversationId()} is null or blank, a new UUID is generated
     * and returned in the response — the caller should store it and pass it back for
     * subsequent turns in the same conversation.
     */
    public PolicyAskResponse ask(PolicyAskRequest request) {
        String conversationId = resolveConversationId(request.conversationId());

        log.info("Processing policy question. conversationId={}", conversationId);
        log.debug("Question: {}", request.question());

        var chatClientResponse = chatClient.prompt()
                .user(request.question())
                .advisors(a -> {
                    // Always pass conversationId so MessageChatMemoryAdvisor loads
                    // and saves the correct message history for this session.
                    a.param(CONVERSATION_ID_KEY, conversationId);

                    // Optionally restrict the vector search to specific documents/categories.
                    // QuestionAnswerAdvisor reads this param and injects it into SearchRequest
                    // as a metadata filter — only matching chunks are retrieved.
                    if (request.documentFilter() != null && !request.documentFilter().isBlank()) {
                        log.debug("Applying document filter: {}", request.documentFilter());
                        a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, request.documentFilter());
                    }
                })
                .call()
                .chatClientResponse();

        assert chatClientResponse.chatResponse() != null;
        String answer = chatClientResponse.chatResponse().getResult().getOutput().getText();

        @SuppressWarnings("unchecked")
        List<org.springframework.ai.document.Document> retrievedDocs =
                (List<org.springframework.ai.document.Document>)
                        chatClientResponse.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);

        List<DocumentSource> sources = null;
        if (retrievedDocs != null) {
            log.info("Retrieved {} document chunks. conversationId={}", retrievedDocs.size(), conversationId);
            sources = retrievedDocs.stream()
                    .map(DocumentSource::from)
                    .toList();
        } else {
            log.info("No document chunks retrieved. conversationId={}", conversationId);
        }

        return new PolicyAskResponse(conversationId, answer, sources);
    }

    /**
     * Clears all conversation history for the given session.
     * After this call the session starts fresh on the next question.
     */
    public void clearSession(String conversationId) {
        log.info("Clearing conversation session. conversationId={}", conversationId);
        chatMemory.clear(conversationId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveConversationId(String requested) {
        return (requested != null && !requested.isBlank())
                ? requested
                : UUID.randomUUID().toString();
    }
}
