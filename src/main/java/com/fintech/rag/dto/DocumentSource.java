package com.fintech.rag.dto;

import org.springframework.ai.document.Document;

import java.util.Map;

/**
 * Typed representation of a policy document chunk retrieved from the vector store.
 *
 * <p>Spring AI's {@link org.springframework.ai.reader.pdf.PagePdfDocumentReader}
 * attaches the following metadata keys to every chunk it produces:
 * <ul>
 *   <li>{@code file_name}   – original PDF filename</li>
 *   <li>{@code page_number} – 1-based page number within the PDF</li>
 * </ul>
 * {@link #from(Document)} extracts these into a clean, typed record instead of
 * leaking raw {@code Map<String, Object>} to API consumers.
 */
public record DocumentSource(String fileName, Integer pageNumber) {

    public static DocumentSource from(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        String fileName = (String) metadata.getOrDefault("file_name", "unknown");

        Object pageObj = metadata.get("page_number");
        Integer pageNumber = (pageObj instanceof Number n) ? n.intValue() : null;

        return new DocumentSource(fileName, pageNumber);
    }
}
