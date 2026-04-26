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
     * Creates a VectorStore with a custom collection name override.
     * Used when documents must be stored in a per-locale collection (e.g., the SN locale core name).
     */
    default VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential,
            String collectionNameOverride) {
        return createVectorStore(turStoreInstance, embeddingModel, decryptedCredential);
    }

    /**
     * Lists all collections in the vector store.
     * Providers that do not support collection listing return an empty list.
     */
    default List<TurStoreCollectionInfo> listCollections(TurStoreInstance turStoreInstance,
                                                          String decryptedCredential) {
        return List.of();
    }

    /**
     * Creates an empty collection in the vector store.
     */
    default void createCollection(TurStoreInstance turStoreInstance,
                                   String decryptedCredential,
                                   String collectionName) {
        // No-op by default
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

    /**
     * Paginates over the chunks stored in {@code collectionName} without
     * computing similarity — used by the ANN admin UI when the search field
     * is empty (or {@code *}) to browse all indexed content. Filters mirror
     * the {@code search} endpoint: an AND-of-IN over scalar metadata.
     * <p>
     * Default implementation throws {@link UnsupportedOperationException}
     * since not every backend exposes a native list-with-pagination API.
     * Implement on a per-store basis using its native cursor/limit/offset.
     *
     * @since 2026.2.4
     */
    default TurStoreChunkPage listChunks(TurStoreInstance turStoreInstance,
                                          String decryptedCredential,
                                          String collectionName,
                                          int page,
                                          int pageSize,
                                          Map<String, List<String>> filters) {
        throw new UnsupportedOperationException(
                "listChunks not supported by store provider '" + getPluginType() + "'");
    }
}
