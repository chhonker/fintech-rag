package com.fintech.rag.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/policy/ask}.
 *
 * @param conversationId The session ID for this conversation.
 *                       Pass this back in subsequent requests to maintain context.
 *                       Use {@code DELETE /api/policy/session/{conversationId}} to clear it.
 * @param answer         The AI-generated answer grounded in the retrieved policy context.
 * @param sources        The policy document chunks that were retrieved and used to generate
 *                       the answer. Useful for transparency and citation.
 */
public record PolicyAskResponse(
        String conversationId,
        String answer,
        List<DocumentSource> sources
) {}
