package com.viglet.turing.genai.provider.store;

import java.util.Locale;
import java.util.Map;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;

@Component
public class TurMilvusStoreProvider implements TurGenAiStoreProvider {

    private static final String DEFAULT_COLLECTION = "turing";

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
        String baseUri = firstNonBlank(optionsParser.stringValue(options, "baseUrl"), turStoreInstance.getUrl());

        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withUri(baseUri);

        if (StringUtils.hasText(decryptedCredential)) {
            connectBuilder.withToken(decryptedCredential);
        } else {
            String token = optionsParser.stringValue(options, "token");
            if (StringUtils.hasText(token)) {
                connectBuilder.withToken(token);
            }
        }

        String databaseName = optionsParser.stringValue(options, "databaseName");
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
        String baseUri = firstNonBlank(optionsParser.stringValue(options, "baseUrl"), turStoreInstance.getUrl());

        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder().withUri(baseUri);
        if (StringUtils.hasText(decryptedCredential)) {
            connectBuilder.withToken(decryptedCredential);
        } else {
            String token = optionsParser.stringValue(options, "token");
            if (StringUtils.hasText(token)) {
                connectBuilder.withToken(token);
            }
        }

        String databaseName = optionsParser.stringValue(options, "databaseName");
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
            String baseUri = firstNonBlank(optionsParser.stringValue(options, "baseUrl"),
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
