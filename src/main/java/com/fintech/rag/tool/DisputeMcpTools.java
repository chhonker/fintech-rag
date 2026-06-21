package com.fintech.rag.tool;

import com.fintech.rag.service.DisputeResolutionService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP-only exposure of dispute resolution. Like PolicyMcpTools, this has no @Tool
 * counterpart — it's a top-level capability for external MCP clients, not a function
 * the internal disputeChatClient would call on itself.
 */
@Service
public class DisputeMcpTools {

    private final DisputeResolutionService disputeResolutionService;

    public DisputeMcpTools(DisputeResolutionService disputeResolutionService) {
        this.disputeResolutionService = disputeResolutionService;
    }

    @McpTool(name = "evaluateDispute",
            description = "Evaluates a customer grievance against ingested policy documents and returns a resolution or escalation recommendation.")
    public String evaluateDispute(
            @McpToolParam(description = "The customer's grievance in plain English") String grievance) {
        var response = disputeResolutionService.resolveDispute(grievance, null);
        return response.getAnswer();
    }
}
