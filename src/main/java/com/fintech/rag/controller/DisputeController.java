package com.fintech.rag.controller;

import com.fintech.rag.service.DisputeResolutionService;
import com.fintech.rag.service.DisputeResolutionService.DisputeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(
        name = "Dispute Resolution",
        description = """
                Evaluates customer grievances against ingested policy documents using RAG + function-calling tools.
                The AI acts as a formal Grievance Redressal Officer — it retrieves the top-4 relevant policy
                chunks from pgvector, may invoke TransactionTool or LedgerTool to look up account data,
                and produces a policy-grounded resolution or escalation recommendation.
                An optional documentFilter restricts the vector search to specific PDFs or categories.
                """
)
@RestController
@RequestMapping("/api/dispute")
public class DisputeController {

    private final DisputeResolutionService disputeResolutionService;

    public DisputeController(DisputeResolutionService disputeResolutionService) {
        this.disputeResolutionService = disputeResolutionService;
    }

    @Operation(
            summary = "Evaluate a dispute (GET)",
            description = """
                    Convenience GET endpoint — pass the grievance as a query parameter.
                    Useful for quick testing from a browser or curl.
                    For production use, prefer the POST endpoint to avoid query-string length limits
                    and to prevent the grievance text from appearing in server access logs.
                    """,
            parameters = {
                    @Parameter(
                            name = "message",
                            description = "The customer's grievance in plain English.",
                            example = "I was charged twice for transaction TXN-4821 on 12th May.",
                            required = true
                    ),
                    @Parameter(
                            name = "documentFilter",
                            description = """
                                    Optional Spring AI filter expression to restrict the vector search.
                                    Filter by category: category == 'upi'
                                    Filter by filename: file_name == 'UPI_TPAP_....pdf'
                                    Multiple files:     file_name in ['a.pdf', 'b.pdf']
                                    Omit to search all ingested documents.
                                    """,
                            example = "category == 'upi'"
                    )
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "Grievance evaluated — returns AI resolution and source policy chunks"),
                    @ApiResponse(responseCode = "500", description = "AI call or tool invocation failed")
            }
    )
    @GetMapping("/evaluate")
    public DisputeResponse evaluateGet(
            @RequestParam("message") String message,
            @RequestParam(value = "documentFilter", required = false) String documentFilter) {
        log.info("Received GET dispute. filter={}", documentFilter);
        log.info("User input: {}", message);
        long startTime = System.currentTimeMillis();

        DisputeResponse response = disputeResolutionService.resolveDispute(message, documentFilter);

        log.info("Dispute evaluated via GET in {} ms.", System.currentTimeMillis() - startTime);
        return response;
    }

    @Operation(
            summary = "Evaluate a dispute (POST)",
            description = """
                    Primary endpoint for dispute resolution.
                    The grievance is sent in the request body so it does not appear in server logs or URLs.

                    Internally this triggers a multi-step AI pipeline:
                    1. QuestionAnswerAdvisor embeds the grievance and retrieves the top-4 matching policy
                       chunks from pgvector. If documentFilter is set, only matching chunks are searched.
                    2. The AI (Gemini 2.5 Flash, acting as Grievance Redressal Officer) may invoke
                       TransactionTool or LedgerTool to look up transaction/account data.
                    3. A formal resolution is generated citing the applicable policy clause,
                       or an escalation to a human supervisor is recommended if context is insufficient.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object", requiredProperties = {"message"}),
                            examples = {
                                    @ExampleObject(
                                            name = "Double charge — no filter",
                                            summary = "Searches all ingested policy documents",
                                            value = """
                                                    {
                                                      "message": "I was charged twice for transaction TXN-4821 on 12th May. Please investigate and process a refund."
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "UPI dispute — filtered",
                                            summary = "Restricts search to UPI policy documents only",
                                            value = """
                                                    {
                                                      "message": "My UPI payment of ₹2000 failed but money was debited. Who is responsible for the refund?",
                                                      "documentFilter": "category == 'upi'"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Unauthorised transaction — filter by filename",
                                            summary = "Restricts search to a specific PDF by exact filename",
                                            value = """
                                                    {
                                                      "message": "There is an unauthorised debit of ₹4500 from my account. What are my rights?",
                                                      "documentFilter": "file_name == 'Customer Rights,Grievance Redressal and Compensation Policy 2023.pdf'"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Refund delayed — filter across multiple files",
                                            summary = "Searches across two specific PDFs",
                                            value = """
                                                    {
                                                      "message": "My refund for ORD-9934 is 10 days overdue. What action can I expect?",
                                                      "documentFilter": "file_name in ['Customer Rights,Grievance Redressal and Compensation Policy 2023.pdf', 'UPI_TPAP_Roles_Responsibilities_Dispute_Redressal.pdf']"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Grievance evaluated — returns AI resolution and source policy chunks"),
                    @ApiResponse(responseCode = "500", description = "AI call or tool invocation failed")
            }
    )
    @PostMapping("/evaluate")
    public DisputeResponse evaluatePost(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String documentFilter = body.get("documentFilter");   // optional, null if absent
        log.info("Received POST dispute. filter={}", documentFilter);
        log.info("User input: {}", message);
        long startTime = System.currentTimeMillis();

        DisputeResponse response = disputeResolutionService.resolveDispute(message, documentFilter);

        log.info("Dispute evaluated via POST in {} ms.", System.currentTimeMillis() - startTime);
        return response;
    }
}
