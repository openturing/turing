package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * GenAI / RAG configuration properties.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
public class TurGenAiProperty {
    /** Reindex-specific knobs (parallelism, batching, …). */
    private TurGenAiReindexProperty reindex = new TurGenAiReindexProperty();

    @Getter
    @Setter
    public static class TurGenAiReindexProperty {
        /**
         * Number of documents indexed concurrently during a RAG reindex.
         * The reindex job uses a fixed-size thread pool so embedding HTTP
         * calls (typically 200 ms–2 s each) overlap; with the default
         * {@code 4} a 10 000-doc collection finishes ~4× faster than
         * sequential indexing without saturating typical embedding-API
         * rate limits. Set to {@code 1} to force sequential indexing.
         */
        private int parallel = 4;
    }
}
