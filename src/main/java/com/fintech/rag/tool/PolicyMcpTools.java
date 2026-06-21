package com.fintech.rag.tool;

import com.fintech.rag.dto.PolicyAskRequest;
import com.fintech.rag.service.PolicyQueryService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP-only exposure of policy Q&A. Unlike TransactionTool/LedgerTool, this method has
 * no @Tool counterpart — it's never used for internal ChatClient function-calling,
 * only for external MCP clients (Claude Desktop, Claude Code) asking policy questions
 * directly.
 */
@Service
public class PolicyMcpTools {

    private final PolicyQueryService policyQueryService;

    public PolicyMcpTools(PolicyQueryService policyQueryService) {
        this.policyQueryService = policyQueryService;
    }

    @McpTool(name = "askPolicyQuestion",
            description = "Answers a question about banking policies (refunds, grievances, UPI rules) using RAG over ingested policy documents.")
    public String askPolicyQuestion(
            @McpToolParam(description = "The policy question to ask") String question,
            @McpToolParam(description = "Optional conversation ID to continue a previous session. Omit to start a new session.", required = false) String conversationId) {
        var response = policyQueryService.ask(new PolicyAskRequest(question, conversationId, null));
        return response.answer();
    }
}
