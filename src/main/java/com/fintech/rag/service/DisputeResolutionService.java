package com.fintech.rag.service;

import com.fintech.rag.dto.DocumentSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Evaluates customer grievances against ingested policy documents.
 *
 * <p>The ChatClient is injected pre-configured from {@link com.fintech.rag.config.AiConfig}
 * with the Grievance Officer persona, QuestionAnswerAdvisor (RAG), and DisputeTools
 * (TransactionTool, LedgerTool) already wired in.
 * This service only needs to call the client and map the response.
 */
@Slf4j
@Service
public class DisputeResolutionService {

    private final ChatClient chatClient;

    public DisputeResolutionService(@Qualifier("disputeChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * @param userGrievance  the customer's complaint in plain English
     * @param documentFilter optional Spring AI filter expression to restrict the vector search
     *                       e.g. {@code "category == 'upi'"} or {@code "file_name == 'foo.pdf'"}.
     *                       Pass null to search the full vector store.
     */
    public DisputeResponse resolveDispute(String userGrievance, String documentFilter) {
        log.info("Resolving dispute. filter={}", documentFilter);
        log.debug("Grievance: {}", userGrievance);

        var prompt = chatClient.prompt().user(userGrievance);

        // Apply document filter when provided — restricts vector search to matching chunks only
        if (documentFilter != null && !documentFilter.isBlank()) {
            log.debug("Applying document filter: {}", documentFilter);
            prompt = prompt.advisors(a ->
                    a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, documentFilter));
        }

        var chatClientResponse = prompt.call().chatClientResponse();

        assert chatClientResponse.chatResponse() != null;
        String answer = chatClientResponse.chatResponse().getResult().getOutput().getText();
        log.debug("AI response generated successfully.");

        @SuppressWarnings("unchecked")
        List<org.springframework.ai.document.Document> retrievedDocs =
                (List<org.springframework.ai.document.Document>)
                        chatClientResponse.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);

        List<DocumentSource> sources = null;
        if (retrievedDocs != null) {
            log.info("Found {} retrieved documents from vector store.", retrievedDocs.size());
            sources = retrievedDocs.stream()
                    .map(DocumentSource::from)
                    .toList();
        } else {
            log.info("No documents were retrieved from vector store.");
        }

        return new DisputeResponse(answer, sources);
    }

    // ── Response DTO ──────────────────────────────────────────────────────────

    @Getter
    @Setter
    @AllArgsConstructor
    public static class DisputeResponse {
        private String answer;
        private List<DocumentSource> sources;
    }
}
