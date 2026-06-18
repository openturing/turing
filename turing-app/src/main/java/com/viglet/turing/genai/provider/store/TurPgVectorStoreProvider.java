package com.viglet.turing.genai.provider.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * PGVector embedding store provider.
 * Connects to a PostgreSQL database with the pgvector extension.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Component
public class TurPgVectorStoreProvider implements TurGenAiStoreProvider {

    private static final String DEFAULT_TABLE_NAME = "vector_store";
    private static final String DEFAULT_SCHEMA_NAME = "public";
    private static final String SCHEMA_NAME_OPTION = "schemaName";

    private static final ObjectMapper METADATA_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final TurProviderOptionsParser optionsParser;
    private final ConcurrentHashMap<String, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public TurPgVectorStoreProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @PreDestroy
    public void shutdown() {
        dataSourceCache.values().forEach(ds -> {
            try {
                ds.close();
            } catch (Exception e) {
                log.warn("Failed to close PGVector datasource pool: {}", e.getMessage());
            }
        });
        dataSourceCache.clear();
    }

    @Override
    public String getPluginType() {
        return "pgvector";
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential) {
        return buildVectorStore(turStoreInstance, embeddingModel, decryptedCredential, null);
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential,
            String collectionNameOverride) {
        return buildVectorStore(turStoreInstance, embeddingModel, decryptedCredential, collectionNameOverride);
    }

    private VectorStore buildVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential,
            String collectionNameOverride) {

        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);

        String rawTableName = StringUtils.hasText(collectionNameOverride)
                ? collectionNameOverride
                : firstNonBlank(
                        optionsParser.stringValue(options, "tableName"),
                        turStoreInstance.getCollectionName(),
                        DEFAULT_TABLE_NAME);
        String tableName = sanitizeTableName(rawTableName);

        String schemaName = firstNonBlank(
                optionsParser.stringValue(options, SCHEMA_NAME_OPTION),
                DEFAULT_SCHEMA_NAME);

        boolean initializeSchema = Boolean.TRUE
                .equals(firstNonNull(optionsParser.booleanValue(options, "initializeSchema"), Boolean.TRUE));

        var builder = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName(tableName)
                .schemaName(schemaName)
                .initializeSchema(initializeSchema);

        Integer dimensions = optionsParser.intValue(options, "dimensions");
        if (dimensions != null && dimensions > 0) {
            builder.dimensions(dimensions);
        }

        PgDistanceType distanceType = parseDistanceType(optionsParser.stringValue(options, "distanceType"));
        if (distanceType != null) {
            builder.distanceType(distanceType);
        }

        PgIndexType indexType = parseIndexType(optionsParser.stringValue(options, "indexType"));
        if (indexType != null) {
            builder.indexType(indexType);
        }

        return builder.build();
    }

    private JdbcTemplate buildJdbcTemplate(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            Map<String, Object> options) {

        String cacheKey = turStoreInstance.getId();
        HikariDataSource dataSource = dataSourceCache.computeIfAbsent(cacheKey, key -> {
            String jdbcUrl = firstNonBlank(
                    optionsParser.stringValue(options, "jdbcUrl"),
                    turStoreInstance.getUrl());

            String username = optionsParser.stringValue(options, "username");
            String password = optionsParser.stringValue(options, "password");

            if (StringUtils.hasText(decryptedCredential)) {
                String[] parts = decryptedCredential.split(":", 2);
                if (parts.length == 2) {
                    username = parts[0];
                    password = parts[1];
                } else {
                    password = decryptedCredential;
                }
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            if (StringUtils.hasText(username)) {
                config.setUsername(username);
            }
            if (StringUtils.hasText(password)) {
                config.setPassword(password);
            }
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setPoolName("turing-pgvector-" + key);

            return new HikariDataSource(config);
        });

        return new JdbcTemplate(dataSource);
    }

    @Override
    public List<TurStoreCollectionInfo> listCollections(TurStoreInstance turStoreInstance,
            String decryptedCredential) {
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);
            String schemaName = firstNonBlank(
                    optionsParser.stringValue(options, SCHEMA_NAME_OPTION), DEFAULT_SCHEMA_NAME);

            String sql = """
                    SELECT t.tablename
                    FROM pg_tables t
                    WHERE t.schemaname = ?
                      AND EXISTS (
                          SELECT 1 FROM information_schema.columns c
                          WHERE c.table_schema = t.schemaname
                            AND c.table_name = t.tablename
                            AND c.column_name = 'embedding'
                      )
                    ORDER BY t.tablename
                    """;

            List<String> tableNames = jdbcTemplate.queryForList(sql, String.class, schemaName);
            List<TurStoreCollectionInfo> result = new java.util.ArrayList<>();
            for (String tableName : tableNames) {
                retrieveDocumentCount(jdbcTemplate, schemaName, result, tableName);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to list PGVector collections", e);
            return List.of();
        }
    }

    private void retrieveDocumentCount(JdbcTemplate jdbcTemplate, String schemaName,
            List<TurStoreCollectionInfo> result,
            String tableName) {
        String quotedTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        long chunkCount = queryChunkCount(jdbcTemplate, quotedTable, tableName);
        long documentCount = queryDocumentCount(jdbcTemplate, quotedTable, tableName);
        if (documentCount == 0) {
            documentCount = chunkCount;
        }
        result.add(new TurStoreCollectionInfo(tableName, tableName, documentCount, chunkCount));
    }

    private long queryChunkCount(JdbcTemplate jdbcTemplate, String quotedTable, String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + quotedTable, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.debug("Failed to count chunks for {}: {}", tableName, e.getMessage());
            return 0;
        }
    }

    private long queryDocumentCount(JdbcTemplate jdbcTemplate, String quotedTable, String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT COALESCE("
                            + "metadata ->> 'source_id', "
                            + "metadata ->> 'objectName', "
                            + "metadata ->> 'fileName'"
                            + ")) FROM " + quotedTable
                            + " WHERE COALESCE("
                            + "metadata ->> 'source_id', "
                            + "metadata ->> 'objectName', "
                            + "metadata ->> 'fileName'"
                            + ") IS NOT NULL",
                    Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.debug("Failed to count documents for {}: {}", tableName, e.getMessage());
            return 0;
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
        JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);
        String schemaName = firstNonBlank(
                optionsParser.stringValue(options, SCHEMA_NAME_OPTION), DEFAULT_SCHEMA_NAME);
        String quotedTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(collectionName);

        int safePageSize = Math.clamp(pageSize, 1, 200);
        int safePage = Math.max(0, page);
        int offset = safePage * safePageSize;

        // Build a parameterized WHERE clause from the filter map. Each key/list
        // becomes a `metadata ->> 'key' IN (?, ?, ...)` clause; multiple keys
        // are joined with AND. Keys are sanitized to identifier-safe characters
        // because they're interpolated into the SQL (the values are bound).
        List<Object> bindings = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder();
        if (filters != null && !filters.isEmpty()) {
            for (Map.Entry<String, List<String>> e : filters.entrySet()) {
                String key = sanitizeMetadataKey(e.getKey());
                List<String> values = e.getValue();
                if (key == null || values == null || values.isEmpty()) {
                    continue;
                }
                List<String> nonBlank = values.stream().filter(v -> v != null && !v.isBlank()).toList();
                if (nonBlank.isEmpty()) {
                    continue;
                }
                if (!whereClause.isEmpty()) {
                    whereClause.append(" AND ");
                }
                String placeholders = String.join(",", nonBlank.stream().map(v -> "?").toList());
                whereClause.append("metadata ->> '").append(key).append("' IN (").append(placeholders).append(")");
                bindings.addAll(nonBlank);
            }
        }
        String whereSql = !whereClause.isEmpty() ? " WHERE " + whereClause : "";

        long total;
        try {
            String countSql = "SELECT COUNT(*) FROM " + quotedTable + whereSql;
            Long c = jdbcTemplate.queryForObject(countSql, Long.class, bindings.toArray());
            total = c != null ? c : 0L;
        } catch (Exception e) {
            log.warn("[PGVector] Failed to count chunks for '{}': {}", collectionName, e.getMessage());
            total = -1L;
        }

        // Order by id for stable pagination across calls. Bind values are
        // appended after limit/offset, so the bindings list is rebuilt.
        String selectSql = "SELECT id::text AS id, content, metadata FROM " + quotedTable
                + whereSql + " ORDER BY id LIMIT ? OFFSET ?";
        List<Object> selectBindings = new ArrayList<>(bindings);
        selectBindings.add(safePageSize);
        selectBindings.add(offset);

        List<TurStoreChunkPage.Chunk> chunks;
        try {
            chunks = jdbcTemplate.query(selectSql, (rs, i) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata");
                Map<String, Object> metadata = parseMetadata(metadataJson);
                return new TurStoreChunkPage.Chunk(id, content == null ? "" : content, metadata);
            }, selectBindings.toArray());
        } catch (Exception e) {
            log.warn("[PGVector] Failed to list chunks for '{}' (page={}, pageSize={}): {}",
                    collectionName, page, pageSize, e.getMessage());
            return new TurStoreChunkPage(List.of(), total);
        }
        return new TurStoreChunkPage(chunks, total);
    }

    /**
     * Allows letters, digits, and underscores in metadata keys interpolated
     * into the SQL — anything else is rejected (returns {@code null}) so a
     * malicious filter key cannot break out of the {@code ->>} expression.
     */
    private static String sanitizeMetadataKey(String key) {
        if (key == null || key.isBlank() || !key.matches("\\w+")) {
            return null;
        }
        return key;
    }

    private static Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> metadata = METADATA_MAPPER.readValue(json, METADATA_TYPE);
            return metadata != null ? metadata : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @Override
    public void createCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);
            String schemaName = firstNonBlank(
                    optionsParser.stringValue(options, SCHEMA_NAME_OPTION), DEFAULT_SCHEMA_NAME);

            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS hstore");
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

            String quotedTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(collectionName);
            String sql = "CREATE TABLE IF NOT EXISTS " + quotedTable + " ("
                    + "id uuid DEFAULT uuid_generate_v4() PRIMARY KEY, "
                    + "content text, "
                    + "metadata json, "
                    + "embedding vector"
                    + ")";
            jdbcTemplate.execute(sql);
            log.info("[PGVector] Created collection: {}", collectionName);
        } catch (Exception e) {
            log.error("[PGVector] Failed to create collection: {}", collectionName, e);
            throw new TurRuntimeException("Failed to create collection: " + collectionName, e);
        }
    }

    @Override
    public void clearCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);
            String schemaName = firstNonBlank(
                    optionsParser.stringValue(options, SCHEMA_NAME_OPTION), DEFAULT_SCHEMA_NAME);

            String quotedTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(collectionName);
            jdbcTemplate.execute("DELETE FROM " + quotedTable);
            log.info("[PGVector] Cleared collection: {}", collectionName);
        } catch (Exception e) {
            log.error("[PGVector] Failed to clear collection: {}", collectionName, e);
            throw new TurRuntimeException("Failed to clear collection: " + collectionName, e);
        }
    }

    @Override
    public void deleteCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);
            String schemaName = firstNonBlank(
                    optionsParser.stringValue(options, SCHEMA_NAME_OPTION), DEFAULT_SCHEMA_NAME);

            String quotedTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(collectionName);
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + quotedTable);
            log.info("[PGVector] Deleted collection: {}", collectionName);
        } catch (Exception e) {
            log.error("[PGVector] Failed to delete collection: {}", collectionName, e);
            throw new TurRuntimeException("Failed to delete collection: " + collectionName, e);
        }
    }

    @Override
    public void deleteByMetadata(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName,
            String metadataKey,
            String metadataValue) {
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);
            String schemaName = firstNonBlank(
                    optionsParser.stringValue(options, SCHEMA_NAME_OPTION), DEFAULT_SCHEMA_NAME);

            String quotedTable = quoteIdentifier(schemaName) + "." + quoteIdentifier(collectionName);
            String sql = "DELETE FROM " + quotedTable + " WHERE metadata ->> ? = ?";
            int deleted = jdbcTemplate.update(sql, metadataKey, metadataValue);
            log.info("[PGVector] Deleted {} rows where {}={} from {}", deleted, metadataKey, metadataValue,
                    collectionName);
        } catch (Exception e) {
            log.warn("[PGVector] Failed to delete by metadata: {}={}", metadataKey, metadataValue, e);
            throw new TurRuntimeException("Failed to delete by metadata: " + metadataKey + "=" + metadataValue, e);
        }
    }

    private String sanitizeTableName(String name) {
        return name.replaceAll("\\W", "_");
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public Map<String, String> getSystemInfo(TurStoreInstance turStoreInstance,
            String decryptedCredential) {
        var info = new java.util.LinkedHashMap<String, String>();
        info.put("store", "PGVector");
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            JdbcTemplate jdbcTemplate = buildJdbcTemplate(turStoreInstance, decryptedCredential, options);
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
            info.put("version", version != null ? version : "unknown");
            info.put("pgvectorExtension", queryPgVectorExtVersion(jdbcTemplate));
            info.put("status", "UP");
        } catch (Exception e) {
            log.warn("Failed to retrieve PGVector system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    private String queryPgVectorExtVersion(JdbcTemplate jdbcTemplate) {
        try {
            String extVersion = jdbcTemplate.queryForObject(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
            return extVersion != null ? extVersion : "not installed";
        } catch (Exception e) {
            return "not installed";
        }
    }

    private PgDistanceType parseDistanceType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return PgDistanceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PgIndexType parseIndexType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return PgIndexType.valueOf(value.trim().toUpperCase(Locale.ROOT));
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
