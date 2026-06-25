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

    /** Human-in-the-loop {@code humanApproval} node knobs (§IX.5.a / T119). */
    private TurGenAiHumanApprovalProperty humanApproval = new TurGenAiHumanApprovalProperty();

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

        /**
         * T424 / §XXI — minimum number of <em>attached</em> (MCP / custom)
         * tools that always survive the filter, even when the agent's
         * always-keep native tools alone meet or exceed {@link #topK}.
         *
         * <p>Without this floor, an agent with many operator-selected native
         * tools starved its attached MCP/custom tools to a quota of zero
         * ({@code topK − nativeCount ≤ 0}), so the model never saw the very
         * tools the operator attached (real incident: 24 natives, {@code topK=10}
         * → every {@code dspace_*} MCP tool dropped). The floor guarantees the
         * top-scoring attached tools reach the LLM regardless of native count.
         * Set to {@code 0} to restore the pre-T424 behaviour.
         */
        private int reserveAttached = 8;
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

    /**
     * Knobs for the §IX.5.a / T119 {@code humanApproval} node. The node itself
     * is opt-in per flow (an author drops it on the canvas); these settings
     * only shape how the notification resume link is built and how the timeout
     * sweep runs.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.1
     */
    @Getter
    @Setter
    public static class TurGenAiHumanApprovalProperty {
        /**
         * Public base URL of this Turing instance (scheme + host[:port], no
         * trailing slash) used to build the absolute approval link embedded in
         * notifications, e.g. {@code https://turing.example.com}. When blank,
         * the notification carries a relative {@code /api/genai/approval/{token}}
         * path — fine for same-origin operators, but set this in production so
         * Slack/e-mail links resolve.
         */
        private String baseUrl = "";

        /**
         * Default timeout (seconds) applied to a {@code humanApproval} node that
         * leaves {@code timeoutSeconds} unset. {@code 0} (the default) means
         * "wait indefinitely" — the conversation parks until an operator acts.
         */
        private int defaultTimeoutSeconds = 0;
    }
}
