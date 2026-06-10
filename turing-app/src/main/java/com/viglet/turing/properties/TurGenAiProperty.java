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

    /** Auto-offload-large-tool-results knobs (§IX.3.d / T114). */
    private TurGenAiToolResultOffloadProperty toolResultOffload = new TurGenAiToolResultOffloadProperty();

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

    /**
     * Auto-offload large tool results (§IX.3.d / T114). When a decorated
     * tool returns more than {@link #inlineMaxChars} characters, the
     * {@code TurToolCallbackPipeline} stores the full payload in the agent
     * workspace ({@code tool-results/{toolName}-{ts}.json}) and replaces the
     * inline result with a short reference the LLM can resolve via the
     * always-present {@code workspace_read} tool. Cuts prompt-token bloat on
     * tools that return large catalogs / JSON arrays / search dumps.
     *
     * <p>Offloading is only active when storage is enabled
     * ({@code turing.storage.type != none}); without a storage backend there
     * is nowhere to put the payload, so the pipeline leaves results inline
     * and never adds the {@code workspace_read} tool. This keeps the default
     * {@code none} deployment (and every test running on it) byte-for-byte
     * unchanged.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.1
     */
    @Getter
    @Setter
    public static class TurGenAiToolResultOffloadProperty {
        /**
         * Master switch. When {@code false} tool results are always left
         * inline regardless of size, and the {@code workspace_read} tool is
         * not added.
         */
        private boolean enabled = true;

        /**
         * Maximum tool-result length (in characters) kept inline. Results
         * longer than this are written to the workspace and replaced with a
         * reference. Default {@code 4096}.
         */
        private int inlineMaxChars = 4096;
    }
}
