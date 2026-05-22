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

    /**
     * T19 / §III.3 — per-agent opt-out for the BM25 emergency fallback in
     * {@code search_knowledge_base}. When {@code true} (default), if the
     * vector top-1 cosine score is below {@code MIN_VECTOR_CONFIDENCE},
     * {@link com.viglet.turing.genai.tool.TurRagSearchToolService}
     * retries the query as BM25 over {@code FIELD_CONTENT} and prepends a
     * keyword-only warning header. When {@code false}, vector misses
     * return "no results" directly — appropriate for agents whose corpora
     * are tightly domain-scoped and where a keyword-match noise floor
     * does more harm than the recall it buys.
     *
     * <p>Default {@code true} matches the post-T19 ship behavior; agents
     * that prefer strict semantic-only retrieval flip this in the
     * settings form.
     *
     * @since 2026.2.7
     */
    @Column(name = "ragBm25Fallback", nullable = false)
    private boolean ragBm25Fallback = true;

    /**
     * T24 / §III.2 — when {@code true} (default) AND
     * {@link #ragBm25Fallback} is also {@code true}, RAG retrieval runs
     * BOTH vector KNN and BM25 over {@code FIELD_CONTENT} in parallel and
     * fuses the two ranked lists via Reciprocal Rank Fusion ({@code k=60}).
     * The fused list catches morphology/rare-term queries the embedding
     * model misses AND semantic queries the keyword pass misses, with
     * docs in both ranked highest.
     *
     * <p>When {@code false} (legacy T19 behavior), BM25 is only triggered
     * as an emergency fallback when the vector top-1 score is below
     * {@code MIN_VECTOR_CONFIDENCE} — quieter retrieval, but misses the
     * complementarity hybrid offers.
     *
     * <p>When {@link #ragBm25Fallback} itself is {@code false}, this
     * flag is ignored entirely (no BM25 anywhere — strict vector only).
     *
     * <p>Default {@code true} matches the post-T24 ship behavior; agents
     * that want the quieter T19 fallback semantics flip the toggle in
     * the settings form.
     *
     * @since 2026.2.7
     */
    @Column(name = "ragHybridSearch", nullable = false)
    private boolean ragHybridSearch = true;

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
}
