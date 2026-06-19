package com.viglet.turing.genai.provider.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.ShowCollectionsParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.ShowCollResponseWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurMilvusStoreProvider implements TurGenAiStoreProvider {

    // --- S1192: extracted duplicated literals ---
    private static final String TOKEN = "token";
    private static final String DATABASE_NAME = "databaseName";


    private static final String DEFAULT_COLLECTION = "turing";
    private static final String BASE_URL_OPTION = "baseUrl";

    /** Tautology used as a {@code match-all} expression in {@link QueryParam} (Milvus rejects empty {@code expr}). */
    private static final String MATCH_ALL_EXPR = "doc_id != \"\"";

    /** Page size used when scanning metadata to count distinct logical documents. */
    private static final int DOC_SCAN_PAGE_SIZE = 1000;
    /** Cap on metadata-scan pages so a runaway collection doesn't block the admin page. */
    private static final int DOC_SCAN_MAX_PAGES = 50;
    /** Same COALESCE chain used by the Chroma / Lucene / PGVector providers. */
    private static final List<String> DOCUMENT_IDENTIFIER_KEYS =
            List.of("source_id", "objectName", "fileName");

    private static final ObjectMapper METADATA_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final TurProviderOptionsParser optionsParser;

    public TurMilvusStoreProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "milvus";
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential) {

        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        String baseUri = firstNonBlank(optionsParser.stringValue(options, BASE_URL_OPTION), turStoreInstance.getUrl());

        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withUri(baseUri);

        if (StringUtils.hasText(decryptedCredential)) {
            connectBuilder.withToken(decryptedCredential);
        } else {
            String token = optionsParser.stringValue(options, TOKEN);
            if (StringUtils.hasText(token)) {
                connectBuilder.withToken(token);
            }
        }

        String databaseName = optionsParser.stringValue(options, DATABASE_NAME);
        if (StringUtils.hasText(databaseName)) {
            connectBuilder.withDatabaseName(databaseName);
        }

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(connectBuilder.build());

        String collectionName = firstNonBlank(
                optionsParser.stringValue(options, "collectionName"),
                turStoreInstance.getCollectionName(),
                DEFAULT_COLLECTION);
        if (collectionName == null) {
            collectionName = DEFAULT_COLLECTION;
        }
        boolean initializeSchema = Boolean.TRUE
                .equals(firstNonNull(optionsParser.booleanValue(options, "initializeSchema"), Boolean.TRUE));

        MilvusVectorStore.Builder builder = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(initializeSchema);

        Integer embeddingDimension = optionsParser.intValue(options, "embeddingDimension");
        if (embeddingDimension != null) {
            builder.embeddingDimension(embeddingDimension);
        }

        String indexParameters = optionsParser.stringValue(options, "indexParameters");
        if (StringUtils.hasText(indexParameters)) {
            builder.indexParameters(indexParameters);
        }

        MetricType metricType = parseMetricType(optionsParser.stringValue(options, "metricType"));
        if (metricType != null) {
            builder.metricType(metricType);
        }

        IndexType indexType = parseIndexType(optionsParser.stringValue(options, "indexType"));
        if (indexType != null) {
            builder.indexType(indexType);
        }

        return builder.build();
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential,
            String collectionNameOverride) {

        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        String baseUri = firstNonBlank(optionsParser.stringValue(options, BASE_URL_OPTION), turStoreInstance.getUrl());

        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder().withUri(baseUri);
        if (StringUtils.hasText(decryptedCredential)) {
            connectBuilder.withToken(decryptedCredential);
        } else {
            String token = optionsParser.stringValue(options, TOKEN);
            if (StringUtils.hasText(token)) {
                connectBuilder.withToken(token);
            }
        }

        String databaseName = optionsParser.stringValue(options, DATABASE_NAME);
        if (StringUtils.hasText(databaseName)) {
            connectBuilder.withDatabaseName(databaseName);
        }

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(connectBuilder.build());

        String collectionName = StringUtils.hasText(collectionNameOverride)
                ? collectionNameOverride
                : firstNonBlank(
                        optionsParser.stringValue(options, "collectionName"),
                        turStoreInstance.getCollectionName(),
                        DEFAULT_COLLECTION);
        if (collectionName == null) {
            collectionName = DEFAULT_COLLECTION;
        }
        boolean initializeSchema = Boolean.TRUE
                .equals(firstNonNull(optionsParser.booleanValue(options, "initializeSchema"), Boolean.TRUE));

        MilvusVectorStore.Builder builder = MilvusVectorStore.builder(milvusServiceClient, embeddingModel)
                .collectionName(collectionName)
                .initializeSchema(initializeSchema);

        Integer embeddingDimension = optionsParser.intValue(options, "embeddingDimension");
        if (embeddingDimension != null) {
            builder.embeddingDimension(embeddingDimension);
        }

        String indexParameters = optionsParser.stringValue(options, "indexParameters");
        if (StringUtils.hasText(indexParameters)) {
            builder.indexParameters(indexParameters);
        }

        MetricType metricType = parseMetricType(optionsParser.stringValue(options, "metricType"));
        if (metricType != null) {
            builder.metricType(metricType);
        }

        IndexType indexType = parseIndexType(optionsParser.stringValue(options, "indexType"));
        if (indexType != null) {
            builder.indexType(indexType);
        }

        return builder.build();
    }

    private MetricType parseMetricType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MetricType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private IndexType parseIndexType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return IndexType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
    public java.util.Map<String, String> getSystemInfo(TurStoreInstance turStoreInstance,
                                                        String decryptedCredential) {
        var info = new java.util.LinkedHashMap<String, String>();
        info.put("store", "Milvus");
        try {
            java.util.Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            String baseUri = firstNonBlank(optionsParser.stringValue(options, BASE_URL_OPTION),
                    turStoreInstance.getUrl());
            ConnectParam.Builder connectBuilder = ConnectParam.newBuilder().withUri(baseUri);
            if (StringUtils.hasText(decryptedCredential)) {
                connectBuilder.withToken(decryptedCredential);
            }
            MilvusServiceClient client = new MilvusServiceClient(connectBuilder.build());
            var resp = client.getVersion();
            if (resp.getStatus() == io.milvus.param.R.Status.Success.getCode()) {
                info.put("version", resp.getData().getVersion());
            }
            client.close();
            info.put("status", "UP");
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    @Override
    public List<TurStoreCollectionInfo> listCollections(TurStoreInstance turStoreInstance,
            String decryptedCredential) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        MilvusServiceClient client = null;
        try {
            client = buildMilvusClient(turStoreInstance, decryptedCredential, options);
            R<ShowCollectionsResponse> resp = client.showCollections(
                    ShowCollectionsParam.newBuilder().build());
            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.warn("[Milvus] showCollections failed: {}", resp.getMessage());
                return List.of();
            }
            ShowCollResponseWrapper wrapper = new ShowCollResponseWrapper(resp.getData());
            List<String> names = wrapper.getCollectionNames();
            List<TurStoreCollectionInfo> result = new ArrayList<>();
            for (String name : names) {
                long chunkCount = countMilvusRows(client, name, MATCH_ALL_EXPR);
                long documentCount = countDistinctDocuments(client, name, chunkCount);
                if (documentCount == 0) {
                    documentCount = chunkCount;
                }
                result.add(new TurStoreCollectionInfo(name, name,
                        Math.max(0, documentCount), Math.max(0, chunkCount)));
            }
            return result;
        } catch (Exception e) {
            log.error("[Milvus] Failed to list collections", e);
            return List.of();
        } finally {
            closeQuietly(client);
        }
    }

    /**
     * Counts distinct logical documents in a Milvus collection by paginating
     * the {@code metadata} JSON field via {@link MilvusServiceClient#query} and
     * accumulating unique values from the first present
     * {@link #DOCUMENT_IDENTIFIER_KEYS}. Falls back to {@code chunkCount}
     * (returned as 0 here, then promoted by the caller) when no chunk has any
     * of the identifier keys — same convention as Chroma / Lucene / PGVector.
     */
    private long countDistinctDocuments(MilvusServiceClient client, String collectionName, long chunkCount) {
        if (chunkCount <= 0) {
            return 0L;
        }
        java.util.Set<String> identifiers = new java.util.HashSet<>();
        boolean sawAnyMetadata = false;
        try {
            for (int page = 0; page < DOC_SCAN_MAX_PAGES; page++) {
                long offset = (long) page * DOC_SCAN_PAGE_SIZE;
                if (offset >= chunkCount) {
                    break;
                }
                QueryParam pageParam = QueryParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withExpr(MATCH_ALL_EXPR)
                        .withOutFields(List.of(MilvusVectorStore.METADATA_FIELD_NAME))
                        .withOffset(offset)
                        .withLimit((long) DOC_SCAN_PAGE_SIZE)
                        .build();
                R<QueryResults> resp = client.query(pageParam);
                if (resp.getStatus() != R.Status.Success.getCode()) {
                    log.debug("[Milvus] metadata page query failed for '{}': {}", collectionName, resp.getMessage());
                    break;
                }
                QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
                var rows = wrapper.getRowRecords();
                if (rows.isEmpty()) {
                    break;
                }
                sawAnyMetadata = true;
                for (var row : rows) {
                    Map<String, Object> metadata = parseMilvusMetadata(
                            row.get(MilvusVectorStore.METADATA_FIELD_NAME));
                    for (String key : DOCUMENT_IDENTIFIER_KEYS) {
                        Object value = metadata.get(key);
                        if (value != null) {
                            String asString = value.toString();
                            if (!asString.isBlank()) {
                                identifiers.add(asString);
                                break;
                            }
                        }
                    }
                }
                if (rows.size() < DOC_SCAN_PAGE_SIZE) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("[Milvus] Failed to count distinct documents for '{}': {}",
                    collectionName, e.getMessage());
            return chunkCount;
        }
        if (!sawAnyMetadata) {
            return chunkCount;
        }
        return identifiers.size();
    }

    private MilvusServiceClient buildMilvusClient(TurStoreInstance turStoreInstance,
            String decryptedCredential, Map<String, Object> options) {
        String baseUri = firstNonBlank(optionsParser.stringValue(options, BASE_URL_OPTION),
                turStoreInstance.getUrl());
        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder().withUri(baseUri);
        if (StringUtils.hasText(decryptedCredential)) {
            connectBuilder.withToken(decryptedCredential);
        } else {
            String token = optionsParser.stringValue(options, TOKEN);
            if (StringUtils.hasText(token)) {
                connectBuilder.withToken(token);
            }
        }
        String databaseName = optionsParser.stringValue(options, DATABASE_NAME);
        if (StringUtils.hasText(databaseName)) {
            connectBuilder.withDatabaseName(databaseName);
        }
        return new MilvusServiceClient(connectBuilder.build());
    }

    private void closeQuietly(MilvusServiceClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.debug("[Milvus] Failed to close client: {}", e.getMessage());
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
        String baseUri = firstNonBlank(optionsParser.stringValue(options, BASE_URL_OPTION),
                turStoreInstance.getUrl());
        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder().withUri(baseUri);
        if (StringUtils.hasText(decryptedCredential)) {
            connectBuilder.withToken(decryptedCredential);
        } else {
            String token = optionsParser.stringValue(options, TOKEN);
            if (StringUtils.hasText(token)) {
                connectBuilder.withToken(token);
            }
        }
        String databaseName = optionsParser.stringValue(options, DATABASE_NAME);
        if (StringUtils.hasText(databaseName)) {
            connectBuilder.withDatabaseName(databaseName);
        }

        int safePageSize = Math.clamp(pageSize, 1, 200);
        int safePage = Math.max(0, page);
        long offset = (long) safePage * safePageSize;
        String filterExpr = buildMilvusFilterExpression(filters);
        String effectiveExpr = filterExpr != null ? filterExpr : MATCH_ALL_EXPR;

        MilvusServiceClient client = null;
        try {
            client = new MilvusServiceClient(connectBuilder.build());

            QueryParam pageParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(effectiveExpr)
                    .withOutFields(List.of(
                            MilvusVectorStore.DOC_ID_FIELD_NAME,
                            MilvusVectorStore.CONTENT_FIELD_NAME,
                            MilvusVectorStore.METADATA_FIELD_NAME))
                    .withOffset(offset)
                    .withLimit((long) safePageSize)
                    .build();
            R<QueryResults> pageResp = client.query(pageParam);
            if (pageResp.getStatus() != R.Status.Success.getCode()) {
                log.warn("[Milvus] query failed for '{}' (status={}): {}",
                        collectionName, pageResp.getStatus(), pageResp.getMessage());
                return new TurStoreChunkPage(List.of(), -1L);
            }
            QueryResultsWrapper wrapper = new QueryResultsWrapper(pageResp.getData());
            List<TurStoreChunkPage.Chunk> chunks = new ArrayList<>();
            for (var row : wrapper.getRowRecords()) {
                Object idVal = row.get(MilvusVectorStore.DOC_ID_FIELD_NAME);
                Object contentVal = row.get(MilvusVectorStore.CONTENT_FIELD_NAME);
                Object metadataVal = row.get(MilvusVectorStore.METADATA_FIELD_NAME);
                String id = idVal == null ? "" : idVal.toString();
                String content = contentVal == null ? "" : contentVal.toString();
                Map<String, Object> metadata = parseMilvusMetadata(metadataVal);
                chunks.add(new TurStoreChunkPage.Chunk(id, content, metadata));
            }

            long total = countMilvusRows(client, collectionName, effectiveExpr);
            return new TurStoreChunkPage(chunks, total);
        } catch (Exception e) {
            log.warn("[Milvus] Failed to list chunks for '{}' (page={}, pageSize={}): {}",
                    collectionName, page, pageSize, e.getMessage());
            return new TurStoreChunkPage(List.of(), -1L);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.debug("[Milvus] Failed to close client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Translates the {@code Map<String, List<String>>} filter convention into
     * a Milvus filter expression: each key becomes a clause over the
     * {@code metadata["key"]} JSON path, joined with {@code &&}. Multi-value
     * lists become {@code in [...]} clauses; single values use {@code ==}.
     * Returns {@code null} when no filter applies.
     */
    private String buildMilvusFilterExpression(Map<String, List<String>> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        List<String> clauses = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : filters.entrySet()) {
            String key = e.getKey();
            List<String> values = e.getValue();
            if (key == null || key.isBlank() || values == null || values.isEmpty()
                    || !key.matches("\\w+")) {
                continue;
            }
            List<String> nonBlank = values.stream().filter(v -> v != null && !v.isBlank()).toList();
            if (nonBlank.isEmpty()) {
                continue;
            }
            String accessor = "metadata[\"" + key + "\"]";
            if (nonBlank.size() == 1) {
                clauses.add(accessor + " == \"" + escapeMilvusString(nonBlank.get(0)) + "\"");
            } else {
                String list = String.join(", ", nonBlank.stream()
                        .map(v -> "\"" + escapeMilvusString(v) + "\"").toList());
                clauses.add(accessor + " in [" + list + "]");
            }
        }
        if (clauses.isEmpty()) {
            return null;
        }
        return String.join(" && ", clauses);
    }

    private static String escapeMilvusString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private long countMilvusRows(MilvusServiceClient client, String collectionName, String expr) {
        try {
            QueryParam countParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .withOutFields(List.of("count(*)"))
                    .build();
            R<QueryResults> resp = client.query(countParam);
            if (resp.getStatus() != R.Status.Success.getCode()) {
                log.debug("[Milvus] count(*) failed for '{}': {}", collectionName, resp.getMessage());
                return -1L;
            }
            QueryResultsWrapper wrapper = new QueryResultsWrapper(resp.getData());
            var rows = wrapper.getRowRecords();
            if (rows.isEmpty()) {
                return 0L;
            }
            Object countVal = rows.get(0).get("count(*)");
            if (countVal instanceof Number n) {
                return n.longValue();
            }
            return -1L;
        } catch (Exception e) {
            log.debug("[Milvus] count(*) threw for '{}': {}", collectionName, e.getMessage());
            return -1L;
        }
    }

    private Map<String, Object> parseMilvusMetadata(Object metadataVal) {
        if (metadataVal == null) {
            return new LinkedHashMap<>();
        }
        if (metadataVal instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                if (e.getKey() != null) {
                    result.put(e.getKey().toString(), e.getValue());
                }
            }
            return result;
        }
        // JSON-encoded scalar (Milvus typically returns a JSON string for the
        // metadata field on older drivers).
        try {
            return METADATA_MAPPER.readValue(metadataVal.toString(), METADATA_TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
