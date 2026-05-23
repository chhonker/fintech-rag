package com.fintech.rag.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class LedgerTool extends AbstractFintechTool implements DisputeTool {

    public LedgerTool(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Tool(description = "Checks the core banking ledger to verify if the sender's account was debited and if the beneficiary's account was credited for a specific transaction ID.")
    public String getLedgerStatus(String transactionId) {
        logToolExecution("getLedgerStatus", transactionId);
        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(
                "SELECT account_debited, beneficiary_credited FROM fintech_app.ledger_status WHERE transaction_id = ?", transactionId);
            return String.format("{\"accountDebited\": %b, \"beneficiaryCredited\": %b}", 
                    (Boolean) result.get("account_debited"), 
                    (Boolean) result.get("beneficiary_credited"));
        } catch (Exception e) {
            return "{\"accountDebited\": false, \"beneficiaryCredited\": false}";
        }
    }
}
