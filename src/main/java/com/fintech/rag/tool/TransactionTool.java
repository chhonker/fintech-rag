package com.fintech.rag.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TransactionTool extends AbstractFintechTool implements DisputeTool {

    public TransactionTool(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Tool(description = "Fetches the real-time status of a financial transaction using its transaction ID.")
    public String getTransactionStatus(String transactionId) {
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
