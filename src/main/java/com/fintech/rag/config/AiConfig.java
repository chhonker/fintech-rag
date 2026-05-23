package com.fintech.rag.config;

import com.fintech.rag.tool.DisputeTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Central configuration for all AI clients in this application.
 *
 * <p>We build ChatClient beans here — not inside service classes — so that:
 * <ul>
 *   <li>Each client's persona, model, advisors, and tools are visible in one place.</li>
 *   <li>Services stay lean: they receive a ready-made client and focus on business logic.</li>
 *   <li>Clients are independently replaceable (e.g. swap the model, change topK) without
 *       touching any service class.</li>
 * </ul>
 *
 * <p>See application.yml for the equivalent YAML-driven configuration and an explanation
 * of what can vs. cannot be expressed in YAML.
 */
@Configuration
public class AiConfig {

    // ── System prompts ────────────────────────────────────────────────────────

    private static final String DISPUTE_SYSTEM_PROMPT = """
            You are an official Banking Grievance Redressal Officer.
            Use ONLY the provided policy context to evaluate the user's complaint.
            If the context is insufficient to reach a conclusion, state clearly that
            the case must be escalated to a human supervisor.
            Be formal, precise, and cite the relevant policy clause where possible.
            """;

    private static final String POLICY_SYSTEM_PROMPT = """
            You are a helpful banking assistant.
            Answer the user's questions about our policies clearly and concisely
            using only the provided policy context.
            If the answer is not found in the context, say so politely and suggest
            the user contact customer support for further help.
            """;

    // ── ChatMemory ────────────────────────────────────────────────────────────

    /**
     * Shared in-memory conversation store.
     * Keyed by conversationId; retains the last 20 messages per session.
     * Used by the policyChatClient's MessageChatMemoryAdvisor.
     * Injected into PolicyQueryService so sessions can be explicitly cleared.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    // ── ChatClient: Dispute Resolution ────────────────────────────────────────

    /**
     * Dispute-resolution client.
     *
     * <ul>
     *   <li>Persona  : formal Grievance Redressal Officer</li>
     *   <li>Model    : gemini-2.5-flash, temperature=0.2 (low creativity — precise answers)</li>
     *   <li>Advisor  : QuestionAnswerAdvisor — retrieves top-4 policy chunks via pgvector</li>
     *   <li>Tools    : all DisputeTool beans (TransactionTool, LedgerTool) for DB look-ups</li>
     *   <li>Memory   : none — each dispute is evaluated independently</li>
     * </ul>
     */
    @Bean
    @Qualifier("disputeChatClient")
    public ChatClient disputeChatClient(ChatModel chatModel,
                                        VectorStore vectorStore,
                                        List<DisputeTool> tools) {
        return ChatClient.builder(chatModel)
                .defaultSystem(DISPUTE_SYSTEM_PROMPT)
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model("gemini-2.5-flash")
                        .temperature(0.2)
                        .build())
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(4).build())
                                .build()
                )
                .defaultTools(tools.toArray(new Object[0]))
                .build();
    }

    // ── ChatClient: Policy Q&A ────────────────────────────────────────────────

    /**
     * General policy Q&A client.
     *
     * <ul>
     *   <li>Persona  : friendly banking assistant</li>
     *   <li>Model    : gemini-2.5-flash, temperature=0.3 (slightly more natural tone)</li>
     *   <li>Advisor 1: MessageChatMemoryAdvisor — injects prior turns for multi-turn chat</li>
     *   <li>Advisor 2: QuestionAnswerAdvisor — retrieves top-4 policy chunks via pgvector</li>
     *   <li>Tools    : none — policy Q&A does not need DB look-ups</li>
     * </ul>
     *
     * <p>Advisor ordering matters: memory runs first (order=1) so conversation history
     * is in the prompt before document context is appended (order=2).
     */
    @Bean
    @Qualifier("policyChatClient")
    public ChatClient policyChatClient(ChatModel chatModel,
                                       VectorStore vectorStore,
                                       ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(POLICY_SYSTEM_PROMPT)
                .defaultOptions(GoogleGenAiChatOptions.builder()
                        .model("gemini-2.5-flash")
                        .temperature(0.3)
                        .build())
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(4).build())
                                .build()
                )
                .build();
    }
}
