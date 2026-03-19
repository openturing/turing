package com.viglet.turing.genai.provider.store;

import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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

    private final TurProviderOptionsParser optionsParser;

    public TurPgVectorStoreProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
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

        String tableName = StringUtils.hasText(collectionNameOverride)
                ? collectionNameOverride
                : firstNonBlank(
                        optionsParser.stringValue(options, "tableName"),
                        turStoreInstance.getCollectionName(),
                        DEFAULT_TABLE_NAME);

        String schemaName = firstNonBlank(
                optionsParser.stringValue(options, "schemaName"),
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
        config.setPoolName("turing-pgvector-" + turStoreInstance.getId());

        DataSource dataSource = new HikariDataSource(config);
        return new JdbcTemplate(dataSource);
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

            try {
                String extVersion = jdbcTemplate.queryForObject(
                        "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
                info.put("pgvectorExtension", extVersion != null ? extVersion : "not installed");
            } catch (Exception e) {
                info.put("pgvectorExtension", "not installed");
            }

            info.put("status", "UP");
        } catch (Exception e) {
            log.warn("Failed to retrieve PGVector system info: {}", e.getMessage());
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
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
