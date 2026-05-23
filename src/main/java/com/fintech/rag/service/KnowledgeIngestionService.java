package com.fintech.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;

    public KnowledgeIngestionService(VectorStore vectorStore, ResourceLoader resourceLoader) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
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
            if (category != null && !category.isBlank()) {
                chunks.forEach(chunk -> chunk.getMetadata().put("category", category));
                log.info("Stamped category='{}' on all {} chunks.", category, chunks.size());
            }

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

            // Sleep between batches to respect Gemini RPM quota
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                log.warn("Embedding thread interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
