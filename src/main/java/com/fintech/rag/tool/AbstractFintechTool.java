package com.fintech.rag.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public abstract class AbstractFintechTool implements FintechTool {

    protected final JdbcTemplate jdbcTemplate;

    protected AbstractFintechTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    protected void logToolExecution(String toolName, String input) {
        log.info("Tool execution triggered: {}", toolName);
        log.debug("Tool {} received input: {}", toolName, input);
    }
}
