package com.viglet.turing.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;
import com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_agent")
@EntityListeners(TurSNSiteSnapshotEvictionListener.class)
public class TurAIAgent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = true, length = 500)
    private String description;

    @Column(length = 150)
    private String icon;

    @Lob
    @Column(name = "system_prompt")
    private String systemPrompt;

    @Lob
    @Column(name = "systemPromptMetaPrompt")
    private String systemPromptMetaPrompt;

    @Column(nullable = false)
    private int enabled;

    @Column(name = "rag_enabled", nullable = false)
    private boolean ragEnabled = false;

    /**
     * When {@code true}, the agent enqueues every chat turn into the chat
     * memory pipeline so it can be persisted by {@code turing.logging.engine}
     * (mongodb / redis) keyed by {@code conversationId}. Has no effect when
     * the logging engine is set to {@code none}.
     *
     * @since 2026.2.6
     */
    @Column(name = "chat_memory_enabled", nullable = false)
    private boolean chatMemoryEnabled = false;

    /**
     * How often the in-memory chat-memory queue is flushed to the configured
     * logging engine. The worker buffers turns and writes them in batches
     * every {@code N} minutes so high-frequency conversations don't generate
     * one I/O round-trip per token. Default: 5 minutes.
     *
     * @since 2026.2.6
     */
    @Column(name = "chat_memory_flush_interval_minutes", nullable = false)
    private int chatMemoryFlushIntervalMinutes = 5;

    /**
     * Hard cap on how many messages are kept in the persisted chat memory
     * document for a single conversation — older turns are dropped via
     * {@code $slice} (Mongo) or list trimming (Redis). Prevents unbounded
     * growth on long-running conversations. Default: 100.
     *
     * @since 2026.2.6
     */
    @Column(name = "chat_memory_max_messages", nullable = false)
    private int chatMemoryMaxMessages = 100;

    /**
     * T30 / §IV.4 — opt-in to conversation-relevance retrieval: when {@code true}
     * and persisted chat memory has more turns than {@link #chatMemoryRecentN},
     * a per-conversation BM25 index over the older turns is built per request
     * and the top {@link #chatMemoryRelevanceTopK} hits are concatenated to the
     * head of the recent-N window (prefixed with a position/timestamp marker so
     * the LLM keeps coherence). When {@code false} the executor behaves as
     * before — only the recent-N client window is fed to the model. Default
     * {@code false} so existing agents are unaffected.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_relevance_enabled", nullable = false)
    private boolean chatMemoryRelevanceEnabled = false;

    /**
     * T30 / §IV.4 — how many older turns the BM25 retriever returns (per side
     * — user and assistant messages are scored from the same pool). Capped at
     * {@code 20} in practice by the retriever; the persisted column can hold
     * any non-negative value. Default {@code 5}.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_relevance_top_k", nullable = false)
    private int chatMemoryRelevanceTopK = 5;

    /**
     * T30 / §IV.4 — size of the recent-N FIFO window kept verbatim (no scoring,
     * always passed to the LLM in chronological order). The BM25 retriever
     * scores ONLY turns OLDER than this window so the retrieved set never
     * duplicates the recent context. Default {@code 10}.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_recent_n", nullable = false)
    private int chatMemoryRecentN = 10;

    /**
     * T123 / §IX.7 — enforceable token budget for the per-turn prompt.
     * {@code 0} (default) disables enforcement: the executor never
     * estimates tokens and never short-circuits the LLM call. Positive
     * values are the upper bound on the estimated prompt size; when the
     * estimate exceeds the budget, {@link #overBudgetBehavior} decides
     * what happens.
     *
     * @since 2026.3.1
     */
    @Column(name = "max_prompt_tokens", nullable = false)
    private int maxPromptTokens = 0;

    /**
     * T123 / §IX.7 — failure mode when the predicted prompt exceeds
     * {@link #maxPromptTokens}:
     * <ul>
     *   <li>{@code WARN} — log + proceed (no behaviour change for the
     *       end user; surfaces budget pressure to operators).</li>
     *   <li>{@code ERROR} — short-circuit the LLM call and surface an
     *       error response so the caller can retry / fall back.</li>
     *   <li>{@code COMPACT} — placeholder for the future T115
     *       workspace-backed history compression; v1 degrades to WARN
     *       (logs a "compact-not-implemented" notice and proceeds).</li>
     * </ul>
     *
     * @since 2026.3.1
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "over_budget_behavior", length = 16, nullable = false)
    private TurAgentOverBudgetBehavior overBudgetBehavior = TurAgentOverBudgetBehavior.WARN;

    /**
     * T66 / §VII.6.g — retention policy for this agent's completed
     * {@code chat_flow_submission} rows (LGPD / GDPR compliance hook).
     * {@code RETAIN_FOREVER} (default) keeps everything; {@code RETAIN_DAYS}
     * lets the daily cleanup job delete submissions older than
     * {@link #submissionRetentionDays}; {@code DELETE_AFTER_EXPORT} purges a
     * conversation's submissions right after it is exported. Stored as a
     * string column to avoid ordinal drift.
     *
     * @since 2026.3.1
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "submission_retention", length = 20, nullable = false)
    private TurSubmissionRetention submissionRetention = TurSubmissionRetention.RETAIN_FOREVER;

    /**
     * T66 / §VII.6.g — age threshold in days used only when
     * {@link #submissionRetention} is {@code RETAIN_DAYS}. Null or
     * non-positive disables the time-based purge (effectively
     * {@code RETAIN_FOREVER}).
     *
     * @since 2026.3.1
     */
    @Column(name = "submission_retention_days")
    private Integer submissionRetentionDays;

    @Column(length = 2000)
    private String nativeTools;

    /**
     * Whether the underlying LLM round-trip uses Spring AI's blocking
     * {@code chatModel.call(prompt)} (default — the assistant text arrives
     * as one SSE event) or the reactive {@code chatModel.stream(prompt)}
     * (each token chunk is forwarded immediately to the SSE pipeline so
     * the user sees the bubble fill in). Honored by
     * {@link com.viglet.turing.genai.TurAgentChatExecutor} on the
     * non-flow path; flow-driven turns always fall back to {@code call}
     * because the post-LLM advance/regen pipeline needs the full text
     * before deciding whether to rewrite the bubble.
     *
     * @since 2026.2.7
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "chatMode", length = 16, nullable = false)
    private TurAgentChatMode chatMode = TurAgentChatMode.CALL;

    // T19/T24 reposition (2026.2.7) — `ragBm25Fallback` and `ragHybridSearch`
    // moved from TurAIAgent to TurSNSiteGenAi. Rationale: RAG only runs in
    // SN site context (the SN site chat API is the only invocation path
    // that exercises RAG), and locale-aware BM25 routing depends on
    // TurSNSiteLocale which is owned by the SN site. The agent itself
    // doesn't know what locales it supports, so per-agent flags here were
    // a category error. See Liquibase v2026.2.7.11.yaml for the column
    // drop and v2026.2.7.11.yaml for the new fields on sn_site_genai.

    /**
     * Optional agent-specific Python {@code requirements.txt} addendum.
     * The Code Interpreter sandbox installs the UNION of:
     * <ol>
     *   <li>Global {@code GLOBAL_PYTHON_REQUIREMENTS} setting — baseline
     *       used by every agent (typically {@code reportlab},
     *       {@code matplotlib}, …).</li>
     *   <li>This field — packages specific to this agent's tools
     *       (e.g. an analytics agent adds {@code pandas==2.0}).</li>
     * </ol>
     * <p>The {@code TurCustomToolDependencyService} hashes the union and
     * caches by hash, so different combinations get their own deps dir
     * without conflict. Agent-level requirements travel with the agent
     * on export/import, which makes cross-environment promotion painless
     * (vs. having to also sync Global Settings on each target instance).
     *
     * <p>Format: one package per line, optionally version-pinned
     * ({@code pandas==2.0}, {@code openpyxl>=3.1}). Blank lines and
     * {@code #} comments tolerated. Empty/null = global-only (no
     * agent-specific addition).
     *
     * @since 2026.2.7
     */
    @Lob
    @Column(name = "pythonRequirements")
    private String pythonRequirements;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ai_agent_llm_instance",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "llm_instance_id"))
    private Set<TurLLMInstance> llmInstances = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ai_agent_mcp_server",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "mcp_server_id"))
    private Set<TurMcpServer> mcpServers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ai_agent_custom_tool",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "custom_tool_id"))
    private Set<TurCustomTool> customTools = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "embedding_model_instance_id")
    private TurEmbeddingModel turEmbeddingModelInstance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_instance_id")
    private TurStoreInstance turStoreInstance;

    /**
     * Catalogue of personas this agent is allowed to speak with. The agent
     * runs with one persona at a time at runtime, but a chat flow can switch
     * the active voice mid-conversation by setting the special variable
     * {@code __activePersonaId} on the flow state — the resolver picks the
     * matching entry from this set before each turn. {@link #defaultPersona}
     * is the fallback when no override is active.
     *
     * <p>Constraint enforced at save time: {@link #defaultPersona}, when
     * present, must be a member of this set.
     *
     * @since 2026.2.7
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ai_agent_persona",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "persona_id"))
    private Set<TurPersona> personas = new HashSet<>();

    /**
     * The persona used when no chat flow has overridden the active voice.
     * Optional — when null, the agent speaks in the LLM's default voice.
     *
     * <p>Mapped to the (renamed) column {@code default_persona_id}; the
     * column kept its FK contract from the v2026.2.6.3 migration but was
     * renamed in v2026.2.7.1.
     *
     * @since 2026.2.7
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_persona_id")
    private TurPersona defaultPersona;

    /**
     * T28 / §III.5 Phase C — SE instance hosting the agent's analytics
     * intent index (the per-agent {@code intent_<agentShortId>} core
     * provisioned by {@code TurAnalyticsIntentIndexer}). When null, the
     * indexer falls back to the global default
     * ({@code turing.chat.analytics.classifier.se-instance-id} or the
     * first registered SE) so agents that don't need a custom binding
     * keep working as before.
     *
     * <p>Lazy fetch because the chat-runtime hot path doesn't touch this
     * field — only the analytics enricher cycle (cron-driven) and the
     * indexer (mutation-driven) resolve it.
     *
     * @since 2026.3.1
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analytics_se_instance_id")
    private TurSEInstance analyticsSeInstance;
}
