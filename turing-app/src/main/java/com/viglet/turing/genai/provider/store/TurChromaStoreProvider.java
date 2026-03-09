package com.viglet.turing.genai.provider.store;

import java.util.Map;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

@Component
public class TurChromaStoreProvider implements TurGenAiStoreProvider {

    private static final String DEFAULT_COLLECTION = "turing";
    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

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

        String collectionName = firstNonBlank(
                optionsParser.stringValue(options, "collectionName"),
                turStoreInstance.getCollectionName(),
                DEFAULT_COLLECTION);
        String tenantName = firstNonBlank(optionsParser.stringValue(options, "tenantName"), DEFAULT_TENANT);
        String databaseName = firstNonBlank(optionsParser.stringValue(options, "databaseName"), DEFAULT_DATABASE);
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
