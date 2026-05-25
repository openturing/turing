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

    /** Tool pre-filtering knobs (§IV.2 / T29). */
    private TurGenAiToolPreFilterProperty toolPrefilter = new TurGenAiToolPreFilterProperty();

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

    /**
     * Settings for the §IV.2 / T29 tool pre-filter — a BM25 Lucene match
     * between the user's message and each tool's
     * {@code prompts/tools/**.md} description, keeping only the top-K
     * tools sent to the LLM. Off-by-default for small catalogs so existing
     * agents see no behavior change; the filter only kicks in once the
     * union of native + MCP + custom tools crosses
     * {@link #minToolsThreshold}.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.1
     */
    @Getter
    @Setter
    public static class TurGenAiToolPreFilterProperty {
        /**
         * Master switch. When {@code false} the filter is a no-op even if
         * the catalog is huge — useful to A/B compare token usage with and
         * without filtering on the same agent.
         */
        private boolean enabled = true;

        /**
         * Maximum number of tools forwarded to the LLM per turn. The agent's
         * native tools and the active node's {@code requiredTools} bypass
         * this cap (they are always included).
         */
        private int topK = 10;

        /**
         * Skip pre-filtering entirely when the candidate pool is smaller
         * than or equal to this number. Below the threshold the cost of
         * running the filter outweighs the token savings; description
         * bloat only becomes a problem with dozens of tools.
         */
        private int minToolsThreshold = 20;
    }
}
