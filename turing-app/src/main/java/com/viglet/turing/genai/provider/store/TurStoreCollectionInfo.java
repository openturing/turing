package com.viglet.turing.genai.provider.store;

/**
 * Vendor-agnostic representation of a vector store collection.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public record TurStoreCollectionInfo(
        String name,
        String id,
        long documentCount,
        long chunkCount
) {}
