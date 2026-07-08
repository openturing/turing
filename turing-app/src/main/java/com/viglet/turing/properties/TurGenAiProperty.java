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

    /** Citation drift detection knobs (§X.7.d / T155). */
    private TurGenAiCitationDriftProperty citationDrift = new TurGenAiCitationDriftProperty();

    /** Continuous / online eval knobs (§XXXIII.18 / T603). */
    private TurGenAiOnlineEvalProperty onlineEval = new TurGenAiOnlineEvalProperty();

    /**
     * F.7 / §X.8.e — global default {@code service_tier} for live chat requests
     * ({@code auto} / {@code default} / {@code flex} / {@code priority}). Blank
     * (default) leaves the provider's own default; a per-agent Request Option or
     * per-instance {@code serviceTier} overrides it.
     */
    private String serviceTier;

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

    /**
     * §X.7.d / T155 — citation drift detection. Builds on the Anthropic
     * Citations path (T152–T154): when an agent answers with verifiable RAG
     * citations, each cited passage is persisted as a {@code chat_citation_record}
     * row, and a daily background job re-resolves recent citations against the
     * current index. A citation is flagged {@code citationStale} when the source
     * was re-indexed (its {@code modification_date} is newer than the answer) or
     * the cited passage is no longer present in the live index — so the
     * compliance question "what document did this answer come from, and has it
     * changed since?" has a recorded answer.
     *
     * <p><b>Opt-in, default off.</b> When {@link #enabled} is {@code false}
     * nothing is persisted and the scan job is a no-op, so agents that already
     * stream citations (T152) are byte-for-byte unchanged. Flip it on for
     * deployments that need the auditable, time-aware provenance record.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.4
     */
    @Getter
    @Setter
    public static class TurGenAiCitationDriftProperty {
        /**
         * Master switch for both citation persistence (at answer time) and the
         * daily drift scan. {@code false} (default) → no rows written, scan job
         * returns immediately.
         */
        private boolean enabled = false;

        /**
         * Only re-check citations whose answer was produced within the last N
         * days; older records are left untouched (they age out of relevance and
         * keep the scan bounded). Default {@code 7}.
         */
        private int checkWindowDays = 7;

        /**
         * Skip a record the scan already checked less than this many hours ago,
         * so a daily job doesn't re-resolve the same citation every run until it
         * either drifts or ages out of the window. Default {@code 24}.
         */
        private int recheckIntervalHours = 24;

        /**
         * How many passages to re-retrieve when verifying a citation. A wider
         * pool than the answer-time top-K reduces false "not present" verdicts
         * caused by pure ranking drift. Default {@code 20}.
         */
        private int recheckTopK = 20;

        /**
         * Maximum number of due records processed per scan run, so a large
         * backlog doesn't run the job long past its lock window. Default
         * {@code 500}.
         */
        private int maxPerRun = 500;
    }

    /**
     * §XXXIII.18 / T603 — continuous / online eval. Where the Agent-CI eval
     * (Block AJ) scores <b>golden</b> cases <b>before</b> publish, this samples
     * <b>live production traffic</b> after publish, grades it in the background
     * with the same pluggable CODE + MODEL grader stack (no replay — the answer
     * already happened), and flags quality <b>drift</b> versus a healthy baseline
     * snapshot. It composes the T87 session sentiment, the T155 citation-drift
     * record, and the T447 failing-session miner into one rolling quality signal.
     *
     * <p><b>Opt-in, default off — twice.</b> The whole sweep is a no-op unless
     * {@link #enabled} is {@code true} <em>and</em> an agent flips
     * {@code onlineEvalEnabled}; grader scoring only runs when that agent also
     * binds an {@code onlineEvalGraderStackId} (otherwise only the signal-based
     * rates are computed). Everything is fail-open: a disabled analytics store,
     * a missing LLM, or any error yields an "unavailable" snapshot, never an
     * exception on the schedule.
     *
     * @author Alexandre Oliveira
     * @since 2026.3.4
     */
    @Getter
    @Setter
    public static class TurGenAiOnlineEvalProperty {
        /**
         * Master switch for the scheduled sweep. {@code false} (default) → the
         * job is a no-op scan regardless of any per-agent flag. The on-demand
         * "run now" API is independent of this and only needs the per-agent flag.
         */
        private boolean enabled = false;

        /**
         * Cron for the background sweep (cluster-once via ShedLock). Default
         * {@code 0 30 3 * * *} — nightly, staggered after the citation-drift and
         * prompt-capture jobs.
         */
        private String cron = "0 30 3 * * *";

        /** How many recent sessions to sample per agent per run. Default {@code 50}. */
        private int sampleSize = 50;

        /** Sampling window in days (sessions completed within the last N days). Default {@code 1}. */
        private int windowDays = 1;

        /**
         * Minimum sampled sessions before drift is evaluated at all — below this
         * the sample is too small to trust, so the snapshot is recorded but never
         * flags drift. Default {@code 5}.
         */
        private int minSamplesForDrift = 5;

        /**
         * Flag drift when the window mean grader score falls at least this far
         * below the baseline snapshot's mean score. Default {@code 0.15}.
         */
        private double scoreDropThreshold = 0.15;

        /**
         * Flag drift when the window pass-rate falls at least this far below the
         * baseline snapshot's pass-rate. Default {@code 0.15}.
         */
        private double passRateDropThreshold = 0.15;

        /**
         * Absolute alarm: flag drift when the fraction of sampled sessions with
         * NEGATIVE / FRUSTRATED sentiment (T87) meets or exceeds this. Default
         * {@code 0.4}.
         */
        private double negativeSentimentThreshold = 0.4;

        /**
         * Absolute alarm: flag drift when the fraction of sampled sessions with at
         * least one stale citation (T155) meets or exceeds this. Only evaluated
         * when citation-drift detection is itself enabled. Default {@code 0.25}.
         */
        private double staleCitationThreshold = 0.25;

        /**
         * Absolute alarm: flag drift when the fraction of sampled sessions the
         * T447 miner classes as failing (abandoned / error / handoff / negative /
         * tool-error) meets or exceeds this. Default {@code 0.4}.
         */
        private double failingSessionThreshold = 0.4;

        /** Cap on how many messages per conversation are read to build a grading context. */
        private int maxTurnsPerSession = 40;
    }
}
