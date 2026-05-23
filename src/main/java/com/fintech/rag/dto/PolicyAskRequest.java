package com.fintech.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/policy/ask}.
 *
 * @param question        The user's question about a banking policy. Must not be blank.
 * @param conversationId  Optional. Pass the ID returned by a previous response to continue
 *                        a multi-turn conversation. Omit (or pass null) to start a new session.
 * @param documentFilter  Optional. A Spring AI filter expression to restrict the vector search
 *                        to specific documents or categories. When null, the full vector store
 *                        is searched across all ingested documents.
 *
 *                        <p>Filter by exact filename:
 *                        <pre>file_name == 'UPI_TPAP_Roles_Responsibilities_Dispute_Redressal.pdf'</pre>
 *
 *                        <p>Filter by category tag (set at ingestion time via the {@code category} param):
 *                        <pre>category == 'upi'</pre>
 *
 *                        <p>Filter across multiple files:
 *                        <pre>file_name in ['UPI_TPAP_....pdf', 'Customer Rights....pdf']</pre>
 */
public record PolicyAskRequest(

        @NotBlank(message = "Question must not be blank")
        String question,

        String conversationId,

        String documentFilter
) {}
