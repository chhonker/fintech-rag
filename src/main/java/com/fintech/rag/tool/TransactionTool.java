package com.fintech.rag.tool;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TransactionTool extends AbstractFintechTool implements DisputeTool {

    public TransactionTool(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    // @Tool powers ChatClient function-calling for disputeChatClient (AiConfig).
    // @McpTool independently publishes the same method over MCP to external clients
    // (Claude Desktop, Claude Code). The two annotations don't interact — this method
    // remains callable by both the internal LLM and any connected MCP client.
    @Tool(description = "Fetches the real-time status of a financial transaction using its transaction ID.")
    @McpTool(name = "getTransactionStatus",
            description = "Fetches the real-time status of a financial transaction using its transaction ID.")
    public String getTransactionStatus(
            @McpToolParam(description = "The transaction ID to look up, e.g. TXN-4821") String transactionId) {
        logToolExecution("getTransactionStatus", transactionId);
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                "SELECT status, reason FROM fintech_app.transaction_status WHERE transaction_id = ?", transactionId);
            return String.format("{\"status\": \"%s\", \"reason\": \"%s\"}",
                result.get("status"),
                result.get("reason") != null ? result.get("reason") : "");
        } catch (Exception e) {
            return "{\"status\": \"UNKNOWN\", \"reason\": \"Transaction not found\"}";
        }
    }
}
