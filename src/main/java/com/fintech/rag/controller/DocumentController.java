package com.fintech.rag.controller;

import com.fintech.rag.service.KnowledgeIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Document Ingestion",
        description = """
                Ingests policy PDF documents into the pgvector knowledge base.
                Any PDF placed in src/main/resources (classpath) can be ingested by name.
                An optional category tag can be stamped on every chunk at ingestion time,
                enabling filtered searches later (e.g. search only "upi" or "grievance" docs).
                Re-running ingestion for the same file adds duplicate chunks — clear the
                vector store table first if a full re-index is needed.
                """
)
@RestController
@RequestMapping("/api")
public class DocumentController {

    private final KnowledgeIngestionService ingestionService;

    public DocumentController(KnowledgeIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Operation(
            summary = "Ingest a policy PDF into the vector store",
            description = """
                    Runs the full RAG ingestion pipeline for any PDF on the classpath:
                    1. Generates an MD5 hash of the PDF to check if it's already ingested.
                       If identical, ingestion is skipped (idempotent).
                       If modified, old chunks are deleted first.
                    2. Splits text into ~500-token chunks (min 100 chars) using TokenTextSplitter.
                    3. Stamps 'file_hash' and Optionally stamps a 'category' metadata field on every chunk
                       (e.g. category=upi) — enables filtered searches via documentFilter.
                    4. Sends chunks to Gemini (gemini-embedding-001) in batches of 5 to generate
                       768-dimensional vectors. A 5-second delay between batches respects the
                       Gemini API RPM quota.
                    5. Stores embeddings in PostgreSQL via pgvector (HNSW, cosine distance).

                    Expected duration: several minutes (throttled by Gemini RPM quota).
                    This endpoint is NOT idempotent — calling it twice inserts duplicate chunks.
                    """,
            parameters = {
                    @Parameter(
                            name = "document",
                            description = "Exact filename of the PDF as it appears in src/main/resources.",
                            example = "UPI_TPAP_Roles_Responsibilities_Dispute_Redressal.pdf",
                            required = true
                    ),
                    @Parameter(
                            name = "category",
                            description = """
                                    Optional logical group tag stamped on every chunk as metadata.
                                    Use this to group related PDFs under a shared label so they can be
                                    searched together with a single filter expression.
                                    Examples: upi, grievance, refund, rbi-circular
                                    """,
                            example = "upi"
                    )
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "PDF successfully chunked, embedded, and stored in pgvector"),
                    @ApiResponse(responseCode = "500", description = "Ingestion failed — PDF not found on classpath, Gemini embedding error, or DB issue")
            }
    )
    @PostMapping("/ingest")
    public ResponseEntity<String> ingestDocument(
            @RequestParam String document,
            @RequestParam(required = false) String category) {
        try {
            ingestionService.ingestDocument(document, category);
            String msg = "Document '" + document + "' ingested successfully"
                    + (category != null && !category.isBlank() ? " with category='" + category + "'" : "")
                    + ".";
            return ResponseEntity.ok(msg);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error ingesting document: " + e.getMessage());
        }
    }

    @Operation(
            summary = "List all uniquely ingested documents",
            description = "Returns a list of all distinct files currently in the vector store along with their category and MD5 hash."
    )
    @org.springframework.web.bind.annotation.GetMapping("/documents")
    public ResponseEntity<java.util.List<com.fintech.rag.dto.DocumentInfo>> listDocuments() {
        return ResponseEntity.ok(ingestionService.listDocuments());
    }

    @Operation(
            summary = "Delete an ingested document",
            description = "Deletes all chunks associated with the specified filename from the vector store."
    )
    @org.springframework.web.bind.annotation.DeleteMapping("/documents/{filename}")
    public ResponseEntity<String> deleteDocument(@org.springframework.web.bind.annotation.PathVariable String filename) {
        ingestionService.deleteDocument(filename);
        return ResponseEntity.ok("Document '" + filename + "' deleted successfully.");
    }
}
