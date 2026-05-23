package com.fintech.rag.service;

import com.fintech.rag.dto.DocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.InputStream;
import java.util.List;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeIngestionService(VectorStore vectorStore, ResourceLoader resourceLoader, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Ingests a single PDF from the classpath into the vector store.
     *
     * @param classpathFilename the PDF filename as it appears in src/main/resources
     *                          e.g. "UPI_TPAP_Roles_Responsibilities_Dispute_Redressal.pdf"
     * @param category          optional logical group tag stamped on every chunk as metadata
     *                          e.g. "upi", "grievance", "refund" — enables filtered searches later.
     *                          Pass null to skip tagging.
     */
    public void ingestDocument(String classpathFilename, String category) {
        Resource resource = resourceLoader.getResource("classpath:" + classpathFilename);

        try {
            log.info("Starting ingestion. file={} category={}", classpathFilename, category);
            
            // Generate MD5 hash of the file to track versioning and deduplication
            String fileHash;
            try (InputStream is = resource.getInputStream()) {
                fileHash = DigestUtils.md5DigestAsHex(is);
            }

            // Check if this exact file version is already ingested
            List<String> existingHashes = jdbcTemplate.queryForList(
                "SELECT DISTINCT metadata->>'file_hash' FROM vector_store WHERE metadata->>'file_name' = ?",
                String.class, classpathFilename);

            if (!existingHashes.isEmpty() && existingHashes.contains(fileHash)) {
                log.info("Document '{}' with hash {} is already ingested. Skipping to prevent duplicates.", classpathFilename, fileHash);
                return;
            }

            // If it exists but the hash is different (file was updated), delete the old chunks first
            if (!existingHashes.isEmpty()) {
                log.info("Document '{}' has been modified (new hash: {}). Deleting old chunks before re-ingesting.", classpathFilename, fileHash);
                deleteDocument(classpathFilename);
            }

            // 1. Read PDF page-by-page
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();
            log.info("Read {} pages from '{}'.", documents.size(), classpathFilename);

            // 2. Chunk into ~500-token segments
            TokenTextSplitter textSplitter = TokenTextSplitter.builder()
                    .withChunkSize(500)
                    .withMinChunkSizeChars(100)
                    .withMinChunkLengthToEmbed(5)
                    .withMaxNumChunks(10000)
                    .withKeepSeparator(true)
                    .build();
            List<Document> chunks = textSplitter.apply(documents);
            log.info("Split into {} chunks.", chunks.size());

            // 3. Stamp category metadata on every chunk when provided.
            //    This enables filtered vector searches later:
            //    SearchRequest.filterExpression("category == 'upi'")
            chunks.forEach(chunk -> {
                chunk.getMetadata().put("file_hash", fileHash);
                if (category != null && !category.isBlank()) {
                    chunk.getMetadata().put("category", category);
                }
            });
            log.info("Stamped file_hash='{}' and category='{}' on all {} chunks.", fileHash, category, chunks.size());

            // 4. Embed in batches of 5 (respects Gemini RPM quota) and store in pgvector
            throttledEmbedding(chunks);
            log.info("Ingestion complete. file={}", classpathFilename);

        } catch (Exception e) {
            log.error("Ingestion failed. file={} error={}", classpathFilename, e.getMessage(), e);
            throw new RuntimeException("Document ingestion failed for: " + classpathFilename, e);
        }
    }

    public void throttledEmbedding(List<Document> allChunks) {
        int batchSize = 5;
        log.info("Throttled embedding: {} chunks, batch size {}", allChunks.size(), batchSize);

        for (int i = 0; i < allChunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allChunks.size());
            List<Document> batch = allChunks.subList(i, end);

            try {
                vectorStore.add(batch);
                log.debug("Embedded batch [{}-{}].", i, end - 1);
            } catch (Exception e) {
                log.error("Embedding failed for batch [{}-{}]: {}", i, end - 1, e.getMessage(), e);
                throw new RuntimeException("Failed to embed document batch", e);
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                log.warn("Embedding thread interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public List<DocumentInfo> listDocuments() {
        return jdbcTemplate.query(
            "SELECT DISTINCT metadata->>'file_name' as file_name, metadata->>'category' as category, metadata->>'file_hash' as file_hash FROM vector_store WHERE metadata->>'file_name' IS NOT NULL",
            (rs, rowNum) -> new DocumentInfo(
                rs.getString("file_name"),
                rs.getString("category"),
                rs.getString("file_hash")
            )
        );
    }

    public void deleteDocument(String filename) {
        int deletedChunks = jdbcTemplate.update(
            "DELETE FROM vector_store WHERE metadata->>'file_name' = ?", filename);
        log.info("Deleted {} chunks for document '{}'.", deletedChunks, filename);
    }
}
