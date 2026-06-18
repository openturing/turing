package com.viglet.turing.genai.provider.store;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.persistence.model.store.TurStoreInstance;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pluggable backend for embedding/vector storage. Implementations bridge a
 * particular store (Chroma, PGVector, Lucene, Milvus, …) into Spring AI's
 * {@link VectorStore} abstraction and expose admin operations (collection
 * lifecycle, listing, export, import) used by the React console.
 */
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
     * Returns the number of distinct logical documents in {@code collectionName}.
     * This is a deliberately separate call from {@link #listCollections} because
     * some backends (notably Chroma) compute it by paginating through chunk
     * metadata, which is too expensive to run for every collection on every
     * admin page-load. The console fetches it lazily per row, after the table
     * has rendered, so the listing stays snappy.
     * <p>
     * Default returns {@code -1L} ("unknown / not computed"); the console
     * treats {@code -1} as "fall back to chunk count". Providers that already
     * compute the value cheaply during {@link #listCollections} (PGVector,
     * Lucene) need not override this method.
     *
     * @since 2026.2.6
     */
    default long countDocuments(TurStoreInstance turStoreInstance,
                                 String decryptedCredential,
                                 String collectionName) {
        return -1L;
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

    /**
     * Bulk upserts the given records into {@code collectionName}. Records
     * whose {@link TurStoreExportRecord#embedding()} is null/empty are
     * re-embedded using {@code embeddingModel}; records that already have a
     * vector are persisted verbatim.
     * <p>
     * Default implementation throws {@link UnsupportedOperationException}
     * — admin-style imports rely on each backend's native upsert (Chroma's
     * {@code upsertEmbeddings}, PGVector's batch INSERT, …) so the provider
     * decides the contract. Spring AI's {@link VectorStore#add} is not used:
     * Document in 2.x has no embedding setter, so {@code add()} would force a
     * re-embed for every record and silently discard the JSONL vectors.
     *
     * @param embeddingModel non-null when any input record may lack a vector
     *                       — required to re-embed; ignored when every record
     *                       already has an embedding.
     * @since 2026.2.4
     */
    default TurStoreImportResult importChunks(TurStoreInstance turStoreInstance,
                                                String decryptedCredential,
                                                String collectionName,
                                                Iterable<TurStoreExportRecord> records,
                                                EmbeddingModel embeddingModel) {
        throw new UnsupportedOperationException(
                "importChunks not supported by store provider '" + getPluginType() + "'");
    }

    default void streamExport(TurStoreInstance turStoreInstance,
                              String decryptedCredential,
                              String collectionName,
                              Consumer<TurStoreExportRecord> consumer) {
        int pageSize = 200;
        int page = 0;
        while (true) {
            TurStoreChunkPage result = listChunks(turStoreInstance, decryptedCredential,
                    collectionName, page, pageSize, null);
            List<TurStoreChunkPage.Chunk> chunks = result.chunks();
            if (chunks == null || chunks.isEmpty()) {
                return;
            }
            for (TurStoreChunkPage.Chunk chunk : chunks) {
                consumer.accept(new TurStoreExportRecord(
                        chunk.id(), chunk.content(), null, chunk.metadata()));
            }
            if (chunks.size() < pageSize) {
                return;
            }
            page++;
        }
    }
}
