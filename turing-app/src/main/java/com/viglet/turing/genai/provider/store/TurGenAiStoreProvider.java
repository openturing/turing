package com.viglet.turing.genai.provider.store;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.persistence.model.store.TurStoreInstance;

import java.util.List;
import java.util.Map;

public interface TurGenAiStoreProvider {

    String getPluginType();

    VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential);

    /**
     * Lists all collections in the vector store.
     * Providers that do not support collection listing return an empty list.
     */
    default List<TurStoreCollectionInfo> listCollections(TurStoreInstance turStoreInstance,
                                                          String decryptedCredential) {
        return List.of();
    }

    /**
     * Deletes embeddings from a collection that match the given metadata key/value.
     * Used to remove old embeddings before re-indexing a file.
     */
    default void deleteByMetadata(TurStoreInstance turStoreInstance,
                                   String decryptedCredential,
                                   String collectionName,
                                   String metadataKey,
                                   String metadataValue) {
        // No-op by default
    }

    /**
     * Clears all embeddings from a collection without deleting the collection itself.
     */
    default void clearCollection(TurStoreInstance turStoreInstance,
                                  String decryptedCredential,
                                  String collectionName) {
        // No-op by default
    }

    /**
     * Deletes a collection entirely from the vector store.
     */
    default void deleteCollection(TurStoreInstance turStoreInstance,
                                   String decryptedCredential,
                                   String collectionName) {
        // No-op by default
    }

    /**
     * Returns system information about the store instance,
     * such as version, backend type, etc.
     */
    default Map<String, String> getSystemInfo(TurStoreInstance turStoreInstance,
                                               String decryptedCredential) {
        return Map.of("store", getPluginType(), "status", "UP");
    }
}
