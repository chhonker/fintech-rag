package com.fintech.rag.controller;

import com.fintech.rag.dto.PolicyAskRequest;
import com.fintech.rag.dto.PolicyAskResponse;
import com.fintech.rag.service.PolicyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for general policy Q&A with multi-turn conversation memory.
 *
 * <p>Typical usage flow:
 * <ol>
 *   <li>POST /api/policy/ask  — omit conversationId to start a new session</li>
 *   <li>Copy the conversationId from the response</li>
 *   <li>POST /api/policy/ask  — pass the same conversationId for follow-up questions</li>
 *   <li>DELETE /api/policy/session/{id} — optionally clear history when done</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/policy")
@Tag(
        name = "Policy Q&A",
        description = """
                Conversational Q&A over the ingested banking policy documents.
                Backed by RAG (pgvector similarity search) + multi-turn conversation memory.
                Each session is identified by a conversationId — the model remembers previous
                questions and answers within the same session, enabling natural follow-up questions
                like "what about UPI?" after asking about refund timelines.
                Memory is stored in-memory (lost on restart); sessions can be explicitly cleared.
                """
)
public class PolicyController {

    private final PolicyQueryService policyQueryService;

    public PolicyController(PolicyQueryService policyQueryService) {
        this.policyQueryService = policyQueryService;
    }

    @PostMapping("/ask")
    @Operation(
            summary = "Ask a policy question",
            description = """
                    Answers questions about banking policies using RAG + conversation memory.

                    How it works:
                    1. MessageChatMemoryAdvisor loads prior turns for the given conversationId
                       and prepends them to the prompt (multi-turn context).
                    2. QuestionAnswerAdvisor embeds the question and retrieves the top-4
                       semantically matching policy chunks from pgvector.
                    3. Gemini 2.5 Flash generates an answer grounded in the retrieved chunks.
                    4. The new user message and AI response are saved back to memory.

                    Session behaviour:
                    - Omit conversationId (or send null) → a new UUID is generated and returned.
                    - Pass the returned conversationId in follow-up requests → model remembers context.
                    - Each session retains the last 20 messages (MessageWindowChatMemory).
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PolicyAskRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "New session — no filter",
                                            summary = "First question, searches all documents",
                                            value = """
                                                    {"question": "What is the refund timeline for UPI payments?"}
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Follow-up in same session",
                                            summary = "Continue a conversation — pass the conversationId from the previous response",
                                            value = """
                                                    {
                                                      "question": "What if the refund is delayed beyond that period?",
                                                      "conversationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Filter by category",
                                            summary = "Restrict search to UPI-category documents only",
                                            value = """
                                                    {
                                                      "question": "Who is responsible for resolving a failed UPI transaction?",
                                                      "documentFilter": "category == 'upi'"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Filter by exact filename",
                                            summary = "Restrict search to one specific PDF",
                                            value = """
                                                    {
                                                      "question": "What are my rights if a complaint is not resolved in time?",
                                                      "documentFilter": "file_name == 'Customer Rights,Grievance Redressal and Compensation Policy 2023.pdf'"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Answer generated — includes conversationId, answer text, and source policy chunks"),
                    @ApiResponse(responseCode = "400", description = "Validation failed — question is blank or missing"),
                    @ApiResponse(responseCode = "500", description = "AI call failed or vector store unavailable")
            }
    )
    public ResponseEntity<PolicyAskResponse> ask(@Valid @RequestBody PolicyAskRequest request) {
        log.info("Received policy question request.");
        PolicyAskResponse response = policyQueryService.ask(request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/session/{conversationId}")
    @Operation(
            summary = "Clear a conversation session",
            description = """
                    Wipes all stored message history for the given conversationId.
                    After this call, the next question sent with the same conversationId
                    will be treated as a brand new conversation with no prior context.

                    Use this when:
                    - A user explicitly ends their session.
                    - You want to free up memory for long-running applications.
                    - Testing: reset a session between test cases.
                    """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "Session cleared — no content returned"),
                    @ApiResponse(responseCode = "500", description = "Failed to clear session")
            }
    )
    public ResponseEntity<Void> clearSession(
            @Parameter(
                    description = "The conversationId returned by a previous /ask response.",
                    example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            )
            @PathVariable String conversationId) {
        log.info("Clearing session: {}", conversationId);
        policyQueryService.clearSession(conversationId);
        return ResponseEntity.noContent().build();
    }
}
