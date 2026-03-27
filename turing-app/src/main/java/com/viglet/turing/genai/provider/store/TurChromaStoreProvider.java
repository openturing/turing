package com.viglet.turing.genai.provider.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        ChromaApi chromaApi = buildChromaApi(turStoreInstance, decryptedCredential, options);

        String collectionName = firstNonBlank(
                optionsParser.stringValue(options, "collectionName"),
                turStoreInstance.getCollectionName(),
                DEFAULT_COLLECTION);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, TENANT_NAME_OPTION), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, DATABASE_NAME_OPTION), DEFAULT_DATABASE);
        boolean initializeSchema = Boolean.TRUE
                .equals(firstNonNull(optionsParser.booleanValue(options, "initializeSchema"), Boolean.TRUE));

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .tenantName(tenantName)
                .databaseName(databaseName)
                .initializeSchema(initializeSchema)
                .build();
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

        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(collectionName)
                .tenantName(tenantName)
                .databaseName(databaseName)
                .initializeSchema(initializeSchema)
                .build();
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
        long count = 0;
        try {
            Long c = chromaApi.countEmbeddings(tenantName, databaseName, col.id());
            if (c != null)
                count = c;
        } catch (Exception e) {
            log.warn("Failed to count embeddings for collection: {}", col.name(), e);
        }
        result.add(new TurStoreCollectionInfo(col.name(), col.id(), count, count));
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
