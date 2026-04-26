package com.viglet.turing.genai.provider.store;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurChromaStoreProvider implements TurGenAiStoreProvider {

    private static final String STATUS = "status";
    private static final String DEFAULT_COLLECTION = "turing";
    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";
    private static final String TENANT_NAME_OPTION = "tenantName";
    private static final String DATABASE_NAME_OPTION = "databaseName";

    /**
     * Page size used when scanning a Chroma collection's metadata to count
     * distinct logical documents. Chosen to balance round-trip count vs.
     * payload size for typical RAG collections (≤ ~10k chunks).
     */
    private static final int METADATA_SCAN_PAGE_SIZE = 1000;
    /**
     * Cap on the metadata scan to avoid pathological collections (millions of
     * chunks) blocking the admin page. When the cap is hit, the count is a
     * lower bound; the admin can still rely on chunkCount for the upper bound.
     */
    private static final int METADATA_SCAN_MAX_PAGES = 50;

    /**
     * Metadata keys checked, in order, to identify a logical document — same
     * COALESCE chain used by the Lucene and PGVector providers so the
     * collections UI shows comparable numbers across stores.
     */
    private static final List<String> DOCUMENT_IDENTIFIER_KEYS =
            List.of("source_id", "objectName", "fileName");

    private final TurProviderOptionsParser optionsParser;

    public TurChromaStoreProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "chroma";
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential) {
        return createVectorStore(turStoreInstance, embeddingModel, decryptedCredential, null);
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential,
            String collectionNameOverride) {

        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);

        String collectionName = StringUtils.hasText(collectionNameOverride)
                ? collectionNameOverride
                : firstNonBlank(
                        optionsParser.stringValue(options, "collectionName"),
                        turStoreInstance.getCollectionName(),
                        DEFAULT_COLLECTION);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);
        boolean initializeSchema = Boolean.TRUE
                .equals(firstNonNull(optionsParser.booleanValue(options, "initializeSchema"), Boolean.TRUE));

        ChromaVectorStore springAiStore = ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .tenantName(tenantName)
                .databaseName(databaseName)
                .initializeSchema(initializeSchema)
                .build();
        // Wrap so we can intercept add/similaritySearch — adds marker keys at
        // index time and translates Filter.Expression to a where clause that
        // hits those markers, side-stepping Chroma's array-stringification
        // limitation.
        return new TurChromaVectorStore(springAiStore, chromaApi, embeddingModel,
                tenantName, databaseName, collectionName);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public void createCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);
        try {
            ensureTenantAndDatabase(chromaApi, tenantName, databaseName);
            chromaApi.createCollection(tenantName, databaseName,
                    new ChromaApi.CreateCollectionRequest(collectionName));
            log.info("[Chroma] Created collection: {}", collectionName);
        } catch (Exception e) {
            log.error("[Chroma] Failed to create collection: {}", collectionName, e);
            throw new TurRuntimeException("Failed to create collection: " + collectionName, e);
        }
    }

    private void ensureTenantAndDatabase(ChromaApi chromaApi, String tenantName, String databaseName) {
        try {
            chromaApi.createTenant(tenantName);
            log.info("[Chroma] Created tenant: {}", tenantName);
        } catch (Exception e) {
            log.debug("[Chroma] Tenant '{}' already exists or creation skipped: {}", tenantName, e.getMessage());
        }
        try {
            chromaApi.createDatabase(tenantName, databaseName);
            log.info("[Chroma] Created database: {} in tenant: {}", databaseName, tenantName);
        } catch (Exception e) {
            log.debug("[Chroma] Database '{}' already exists or creation skipped: {}", databaseName, e.getMessage());
        }
    }

    @Override
    public List<TurStoreCollectionInfo> listCollections(TurStoreInstance turStoreInstance,
            String decryptedCredential) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);

        try {
            List<ChromaApi.Collection> collections = chromaApi.listCollections(tenantName, databaseName);
            List<TurStoreCollectionInfo> result = new ArrayList<>();
            for (ChromaApi.Collection col : collections) {
                retrieveEmbeddingCount(chromaApi, tenantName, databaseName, result, col);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to list Chroma collections", e);
            return List.of();
        }
    }

    private void retrieveEmbeddingCount(ChromaApi chromaApi, String tenantName, String databaseName,
            List<TurStoreCollectionInfo> result, ChromaApi.Collection col) {
        long chunkCount = 0;
        try {
            Long c = chromaApi.countEmbeddings(tenantName, databaseName, col.id());
            if (c != null)
                chunkCount = c;
        } catch (Exception e) {
            log.warn("Failed to count embeddings for collection: {}", col.name(), e);
        }
        long documentCount = countDistinctDocuments(chromaApi, tenantName, databaseName, col, chunkCount);
        result.add(new TurStoreCollectionInfo(col.name(), col.id(), documentCount, chunkCount));
    }

    /**
     * Counts distinct logical documents in a Chroma collection by paginating
     * through chunk metadata and accumulating unique values for the first
     * present key in {@link #DOCUMENT_IDENTIFIER_KEYS}. Falls back to
     * {@code chunkCount} when no chunk has any of the identifier keys (e.g.,
     * legacy chunks indexed before the metadata convention existed) — same
     * semantics as the Lucene provider.
     */
    private long countDistinctDocuments(ChromaApi chromaApi, String tenantName, String databaseName,
            ChromaApi.Collection col, long chunkCount) {
        if (chunkCount <= 0) {
            return 0L;
        }
        Set<String> identifiers = new HashSet<>();
        boolean sawAnyMetadata = false;
        try {
            for (int page = 0; page < METADATA_SCAN_MAX_PAGES; page++) {
                int offset = page * METADATA_SCAN_PAGE_SIZE;
                if (offset >= chunkCount) {
                    break;
                }
                // Pass `null` (not Map.of()) for the `where` clause: Chroma's
                // REST API rejects an empty object with "Invalid where clause".
                // `null` for `ids` likewise omits the filter and matches all.
                ChromaApi.GetEmbeddingsRequest request = new ChromaApi.GetEmbeddingsRequest(
                        null,
                        null,
                        METADATA_SCAN_PAGE_SIZE,
                        offset,
                        List.of(ChromaApi.QueryRequest.Include.METADATAS));
                ChromaApi.GetEmbeddingResponse response = chromaApi.getEmbeddings(
                        tenantName, databaseName, col.id(), request);
                if (response == null || response.metadata() == null || response.metadata().isEmpty()) {
                    break;
                }
                sawAnyMetadata = true;
                for (Map<String, String> meta : response.metadata()) {
                    if (meta == null || meta.isEmpty()) {
                        continue;
                    }
                    for (String key : DOCUMENT_IDENTIFIER_KEYS) {
                        String value = meta.get(key);
                        if (value != null && !value.isBlank()) {
                            identifiers.add(value);
                            break;
                        }
                    }
                }
                if (response.metadata().size() < METADATA_SCAN_PAGE_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to count distinct documents for Chroma collection '{}': {}",
                    col.name(), e.getMessage());
            return chunkCount;
        }
        if (!sawAnyMetadata || identifiers.isEmpty()) {
            return chunkCount;
        }
        return identifiers.size();
    }

    @Override
    public void clearCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);
        try {
            // Delete and recreate the collection to clear all embeddings
            chromaApi.deleteCollection(tenantName, databaseName, collectionName);
            chromaApi.createCollection(tenantName, databaseName,
                    new ChromaApi.CreateCollectionRequest(collectionName));
            log.info("[Chroma] Cleared collection: {}", collectionName);
        } catch (Exception e) {
            log.error("[Chroma] Failed to clear collection: {}", collectionName, e);
            throw new TurRuntimeException("Failed to clear collection: " + collectionName, e);
        }
    }

    @Override
    public void deleteCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);
        try {
            chromaApi.deleteCollection(tenantName, databaseName, collectionName);
            log.info("[Chroma] Deleted collection: {}", collectionName);
        } catch (Exception e) {
            log.error("[Chroma] Failed to delete collection: {}", collectionName, e);
            throw new TurRuntimeException("Failed to delete collection: " + collectionName, e);
        }
    }

    @Override
    public void deleteByMetadata(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName,
            String metadataKey,
            String metadataValue) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);

        try {
            // Get the collection to find its ID
            ChromaApi.Collection collection = chromaApi.getCollection(tenantName, databaseName, collectionName);
            if (collection == null)
                return;

            Map<String, Object> where = Map.of(metadataKey, metadataValue);
            var deleteRequest = new ChromaApi.DeleteEmbeddingsRequest(List.of(), where);
            int deleted = chromaApi.deleteEmbeddings(tenantName, databaseName, collection.id(), deleteRequest);
            log.info("[Chroma] Deleted {} embeddings where {}={} from collection {}",
                    deleted, metadataKey, metadataValue, collectionName);
        } catch (Exception e) {
            log.warn("[Chroma] Failed to delete embeddings by metadata: {}={}", metadataKey, metadataValue, e);
        }
    }

    @Override
    public TurStoreChunkPage listChunks(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName,
            int page,
            int pageSize,
            Map<String, List<String>> filters) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);

        ChromaApi.Collection collection;
        try {
            collection = chromaApi.getCollection(tenantName, databaseName, collectionName);
        } catch (Exception e) {
            log.warn("[Chroma] Failed to resolve collection '{}' for listChunks: {}", collectionName, e.getMessage());
            return new TurStoreChunkPage(List.of(), 0L);
        }
        if (collection == null) {
            return new TurStoreChunkPage(List.of(), 0L);
        }

        Map<String, Object> where = buildWhereClause(filters);
        long total;
        if (where == null) {
            try {
                Long c = chromaApi.countEmbeddings(tenantName, databaseName, collection.id());
                total = c != null ? c : -1L;
            } catch (Exception e) {
                log.warn("[Chroma] Failed to count embeddings for '{}': {}", collectionName, e.getMessage());
                total = -1L;
            }
        } else {
            // Chroma has no "count where". Skip the total — frontend uses
            // "page returned full pageSize?" as a "has next" heuristic.
            total = -1L;
        }

        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int safePage = Math.max(0, page);
        int offset = safePage * safePageSize;

        ChromaApi.GetEmbeddingsRequest request = new ChromaApi.GetEmbeddingsRequest(
                null,
                where,
                safePageSize,
                offset,
                List.of(ChromaApi.QueryRequest.Include.METADATAS,
                        ChromaApi.QueryRequest.Include.DOCUMENTS));
        try {
            ChromaApi.GetEmbeddingResponse response = chromaApi.getEmbeddings(
                    tenantName, databaseName, collection.id(), request);
            if (response == null || response.ids() == null) {
                return new TurStoreChunkPage(List.of(), total);
            }
            List<String> ids = response.ids();
            List<String> documents = response.documents() != null ? response.documents() : List.of();
            List<Map<String, String>> metadatas = response.metadata() != null ? response.metadata() : List.of();
            List<TurStoreChunkPage.Chunk> chunks = new ArrayList<>(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                String content = i < documents.size() ? documents.get(i) : "";
                Map<String, String> raw = i < metadatas.size() ? metadatas.get(i) : Map.of();
                Map<String, Object> metadata = raw == null
                        ? Map.of()
                        : new java.util.LinkedHashMap<>(raw);
                chunks.add(new TurStoreChunkPage.Chunk(ids.get(i), content == null ? "" : content, metadata));
            }
            return new TurStoreChunkPage(chunks, total);
        } catch (Exception e) {
            log.warn("[Chroma] Failed to list chunks for collection '{}' (page={}, pageSize={}): {}",
                    collectionName, page, pageSize, e.getMessage());
            return new TurStoreChunkPage(List.of(), total);
        }
    }

    /**
     * Builds a Chroma {@code where} filter from the
     * {@code Map<String, List<String>>} convention used by the ANN search
     * endpoint. Returns {@code null} when no filter is requested — the caller
     * passes {@code null} to Chroma to match all (an empty {@code {}} is
     * rejected as "Invalid where clause").
     */
    private Map<String, Object> buildWhereClause(Map<String, List<String>> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> clauses = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : filters.entrySet()) {
            String key = e.getKey();
            List<String> values = e.getValue();
            if (key == null || key.isBlank() || values == null || values.isEmpty()) {
                continue;
            }
            List<String> nonBlank = values.stream().filter(v -> v != null && !v.isBlank()).toList();
            if (nonBlank.isEmpty()) {
                continue;
            }
            if (nonBlank.size() == 1) {
                clauses.add(Map.of(key, nonBlank.get(0)));
            } else {
                clauses.add(Map.of(key, Map.of("$in", nonBlank)));
            }
        }
        if (clauses.isEmpty()) {
            return null;
        }
        if (clauses.size() == 1) {
            return clauses.get(0);
        }
        return Map.of("$and", clauses);
    }

    private ChromaApi buildChromaApi(TurStoreInstance turStoreInstance, String decryptedCredential,
            Map<String, Object> options) {
        ChromaApi chromaApi = ChromaApi.builder()
                .baseUrl(firstNonBlank(optionsParser.stringValue(options, "baseUrl"), turStoreInstance.getUrl(),
                        "http://localhost:8000"))
                .build();

        if (StringUtils.hasText(decryptedCredential)) {
            if (decryptedCredential.startsWith("basic:")) {
                String[] credentials = decryptedCredential.substring("basic:".length()).split(":", 2);
                if (credentials.length == 2) {
                    chromaApi = chromaApi.withBasicAuthCredentials(credentials[0], credentials[1]);
                }
            } else if (decryptedCredential.startsWith("token:")) {
                chromaApi = chromaApi.withKeyToken(decryptedCredential.substring("token:".length()));
            } else {
                chromaApi = chromaApi.withKeyToken(decryptedCredential);
            }
        } else {
            String keyToken = optionsParser.stringValue(options, "keyToken");
            String basicUsername = optionsParser.stringValue(options, "basicUsername");
            String basicPassword = optionsParser.stringValue(options, "basicPassword");
            if (StringUtils.hasText(keyToken)) {
                chromaApi = chromaApi.withKeyToken(keyToken);
            } else if (StringUtils.hasText(basicUsername) && StringUtils.hasText(basicPassword)) {
                chromaApi = chromaApi.withBasicAuthCredentials(basicUsername, basicPassword);
            }
        }
        return chromaApi;
    }

    @Override
    public Map<String, String> getSystemInfo(TurStoreInstance turStoreInstance,
            String decryptedCredential) {
        var info = new java.util.LinkedHashMap<String, String>();
        info.put("store", "ChromaDB");
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            String baseUrl = firstNonBlank(optionsParser.stringValue(options, "baseUrl"),
                    turStoreInstance.getUrl(), "http://localhost:8000");
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/v2/version"))
                    .GET().build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body != null) {
                body = body.replace("\"", "").trim();
                info.put("version", body);
            }
            info.put(STATUS, "UP");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while retrieving ChromaDB version: {}", e.getMessage());
            info.put(STATUS, "DOWN");
            info.put("error", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to retrieve ChromaDB version: {}", e.getMessage());
            info.put(STATUS, "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
