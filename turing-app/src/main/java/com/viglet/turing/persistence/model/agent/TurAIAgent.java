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
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
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
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

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
     * Per-agent opt-in for rich-content rendering. When {@code true}, the
     * agent's system prompt is augmented with {@code prompts/render.md} so the
     * model knows it may emit {@code ```html} (live preview / games / Chart.js)
     * and {@code ```d2} (diagrams) blocks that the chat client renders natively.
     * Default {@code false} keeps existing agents unchanged (no extra prompt
     * tokens, no rich blocks). Surfaced as an explicit toggle in the agent
     * settings so the capability is discoverable, not a hidden global.
     *
     * @since 2026.3.1
     */
    @Column(name = "rich_content_enabled", nullable = false)
    private boolean richContentEnabled = false;

    /**
     * T787 / §LIII.4 — per-agent opt-in to disclose the model's knowledge cutoff.
     * When on, the assembled system prompt gains a line stating the configured
     * model's catalog {@code knowledgeCutoff} ("Your knowledge cutoff is &lt;date&gt;")
     * so answers self-disclose how stale their knowledge may be. Off by default —
     * a no-op segment for existing agents.
     *
     * @since 2026.3.4
     */
    @Column(name = "disclose_knowledge_cutoff", nullable = false)
    private boolean discloseKnowledgeCutoff = false;

    /**
     * T442 / §XXIII.1 — per-agent opt-in for "answer-as-an-app": built-in
     * generative-UI client tools ({@code comparison_table}, {@code spec_card},
     * {@code configurator}) that let the model assemble a live mini-app from a
     * typed SN search result set instead of returning prose. When {@code true}
     * the built-in tools are advertised (folded into the T438 client-tool set,
     * so they park + resume the same way) and the agent's system prompt is
     * augmented with {@code prompts/answer-as-app.md}. Independent of
     * {@link #clientToolsEnabled}: an operator can enable answer-as-app without
     * declaring any custom client tools. Default {@code false} keeps existing
     * agents byte-for-byte unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "answer_as_app_enabled", nullable = false)
    private boolean answerAsAppEnabled = false;

    /**
     * T443 / §XXIII.2 — per-agent opt-in for "co-browse": built-in client tools
     * ({@code set_search_query}, {@code toggle_facet}, {@code clear_facets},
     * {@code set_sort}, {@code set_page}) that let the agent drive the host page's
     * REAL search UI (query, facet checkboxes, sort, page) instead of replying in
     * a parallel box — "filter to the red ones under R$50" flips the actual
     * facets and updates the URL. When {@code true} the built-in tools are folded
     * into the advertised client-tool set (T438 park/resume) and the system
     * prompt gains {@code prompts/co-browse.md}. Independent of
     * {@link #clientToolsEnabled}. Default {@code false} keeps existing agents
     * byte-for-byte unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "co_browse_enabled", nullable = false)
    private boolean coBrowseEnabled = false;

    /**
     * T445 / §XXIII.4 — per-agent opt-in for the "ambient / proactive copilot".
     * When {@code true}, a server-sent {@code /chat/proactive/stream} watches the
     * conversation's slot bus (T63) for ambient interaction signals (slots named
     * {@code signal.<kind>} carrying an interaction count, written by the host via
     * {@code useTuringClickTracking}/dwell) and, once a signal count crosses
     * {@link #proactiveThresholdSignals}, pushes ONE throttled proactive offer
     * ("I noticed you've looked at several return-policy items — want help?").
     * Default {@code false}: no proactive stream, existing behaviour unchanged. A
     * proactive agent that interrupts constantly is worse than none, hence opt-in
     * + threshold + throttle.
     *
     * @since 2026.3.4
     */
    @Column(name = "proactive_enabled", nullable = false)
    private boolean proactiveEnabled = false;

    /**
     * T445 / §XXIII.4 — how many ambient interaction signals of one kind must
     * accumulate before the proactive copilot offers help. Default 3. Only used
     * when {@link #proactiveEnabled}.
     *
     * @since 2026.3.4
     */
    @Column(name = "proactive_threshold_signals", nullable = false)
    private int proactiveThresholdSignals = 3;

    /**
     * T446 / §XXIII.5 — per-agent opt-in for cross-conversation personal memory.
     * When {@code true}, the built-in {@code recall_user_memory} / {@code
     * remember_fact} client tools are advertised and {@code prompts/user-memory.md}
     * is injected, so the agent can persist and recall durable facts about a user
     * across sessions (the host SDK supplies the stable userId and calls the
     * {@code /sn/{site}/user-memory} endpoints). Default {@code false} keeps
     * existing agents unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "user_memory_enabled", nullable = false)
    private boolean userMemoryEnabled = false;

    /**
     * T447 / §XXIII.6 — per-agent opt-in for the self-tuning loop. When
     * {@code true}, a scheduled job mines recent failed conversations, drafts a
     * revised system prompt, scores it against the agent's golden sets (Block K),
     * and — only if it scores better — opens a PR-style "suggested change" in the
     * admin for human approval (never auto-applied). Requires at least one enabled
     * golden set + a usable LLM. Default {@code false} keeps existing agents
     * unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "self_tuning_enabled", nullable = false)
    private boolean selfTuningEnabled = false;

    /**
     * T603 / §XXXIII.18 — per-agent opt-in for continuous / online eval. When
     * {@code true} (and the global {@code turing.genai.online-eval.enabled}
     * switch is on), a scheduled job samples this agent's recent live sessions,
     * grades them in the background, and records a quality snapshot that flags
     * drift versus the agent's healthy baseline. Grader scoring only runs when
     * {@link #onlineEvalGraderStackId} is also set; otherwise the snapshot
     * carries only the signal-based rates (sentiment / citation / failing).
     * Default {@code false} keeps existing agents unchanged (no sampling).
     *
     * @since 2026.3.4
     */
    @Column(name = "online_eval_enabled", nullable = false)
    private boolean onlineEvalEnabled = false;

    /**
     * T603 / §XXXIII.18 — the reusable grader stack (T600) online eval scores
     * sampled live traffic with. Blank/null → no grader scoring (the snapshot
     * still records sentiment / citation / failing-session rates). Expectation-
     * based graders (slot / outcome / node) naturally skip live traffic that
     * carries no golden expectation, so a content/quality stack (rubric,
     * model-judge, contains/regex, tool-called) is the useful choice here.
     *
     * @since 2026.3.4
     */
    @Column(name = "online_eval_grader_stack_id", length = 36)
    private String onlineEvalGraderStackId;

    /**
     * T448 / §XXIII.7 — per-agent opt-in for agent-to-agent handoff. When
     * {@code true} (and {@link #specialistAgentIds} lists at least one agent), this
     * agent becomes a router: it gets a {@code delegate_to_agent} tool that runs a
     * listed specialist agent on a sub-task and returns its answer — the handoff is
     * visible via the T436 tool-call events. Recursion is bounded by the shared
     * {@code agent.invoke} depth cap. Default {@code false} keeps existing agents
     * unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "agent_handoff_enabled", nullable = false)
    private boolean agentHandoffEnabled = false;

    /**
     * T450 / §XXIII.9 — per-agent opt-in for the embeddable "action widget". When
     * {@code true} the built-in host-action client tools ({@code navigate},
     * {@code fill_form}, {@code click_element}, {@code add_to_cart}) are advertised,
     * so the embedded SDK widget can ACT on the host page (an action layer over any
     * website, no browser extension) — the host wires the matching handlers via the
     * vanilla SDK's {@code createHostActions}. Default {@code false} keeps existing
     * agents unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "action_widget_enabled", nullable = false)
    private boolean actionWidgetEnabled = false;

    /**
     * T187 / §X.15.a — per-agent opt-in for "Live answers" hybrid mode. When
     * {@code true} on a native OpenAI/Anthropic turn, the executor fuses four
     * information sources into ONE answer: (a) the indexed knowledge base (top-K
     * SN/RAG passages injected as grounding context, the leg the OpenAI Responses
     * path otherwise lacks), (b) {@code web_search} citations, (c) live data via
     * {@code web_fetch}, and (d) computed analytics via {@code code_execution}.
     * It is a composition layer over the two-level capability gate: it injects the
     * knowledge-base grounding + a fusion-guidance prompt ({@code prompts/live-answers.md})
     * and instructs the model to weave whichever of its admin-selected native tools
     * are relevant into a single cited reply — it does NOT force-enable a capability
     * the instance doesn't support. To avoid double-grounding it skips the grounding
     * block when the Anthropic citations / native-PDF document path is already
     * attaching passages. Default {@code false} keeps existing agents unchanged.
     *
     * @since 2026.3.4
     */
    @Column(name = "live_answers_enabled", nullable = false)
    private boolean liveAnswersEnabled = false;

    /**
     * T189 / §X.15.c — per-agent opt-in for "Call-center augmentation". When
     * {@code true}, the {@code /call-center/assist} endpoint is enabled for this
     * agent: an operator's transcribed customer utterance is translated into the
     * operator's working language (T150), answered with citations from the
     * customer's index (T248 cited RAG), and the answer is translated back into
     * the customer's language so the operator can relay it. Pure composition of
     * shipped services; default {@code false} keeps the endpoint closed (403) for
     * existing agents.
     *
     * @since 2026.3.4
     */
    @Column(name = "call_center_enabled", nullable = false)
    private boolean callCenterEnabled = false;

    /**
     * T190 / §X.15.d — per-agent opt-in for "self-installing connectors". When
     * {@code true}, the {@code /connector} endpoints are enabled for this agent so
     * it can walk a customer through connecting a third-party system ("connect my
     * Notion"): surface the vendor's token-generation steps and TEST the supplied
     * credential before it is saved/enabled through the existing integration /
     * MCP-server CRUD. When a real computer-use driver (T136) is deployed the
     * steps can be driven in a browser; with the no-op default it degrades to
     * guided instructions + the connection probe. Default {@code false} keeps the
     * endpoints closed (403) for existing agents.
     *
     * @since 2026.3.4
     */
    @Column(name = "connector_setup_enabled", nullable = false)
    private boolean connectorSetupEnabled = false;

    /**
     * T191 / §X.15.e — per-agent opt-in for "self-improvement overnight". When
     * {@code true} (and the distillation tier is enabled), a nightly job distills
     * the agent's recent production traffic (T167 stored completions) into a
     * fine-tuned candidate, scores it against the agent's eval golden sets, and —
     * only if it beats the incumbent — opens a {@code DISTILLATION_CANDIDATE}
     * suggestion for human approval (never auto-applied). Default {@code false}
     * keeps existing agents out of the nightly loop.
     *
     * @since 2026.3.4
     */
    @Column(name = "overnight_improvement_enabled", nullable = false)
    private boolean overnightImprovementEnabled = false;

    /**
     * T448 / §XXIII.7 — CSV of specialist agent ids this router may delegate to
     * (the allowlist for {@code delegate_to_agent}). Only consulted when
     * {@link #agentHandoffEnabled}. Null/blank = no delegation.
     *
     * @since 2026.3.4
     */
    @Column(name = "specialist_agent_ids", length = 2000)
    private String specialistAgentIds;

    /**
     * T323 / §IX.4.d — per-agent opt-in for Anthropic-compatible skills. When
     * {@code true} (and the skill sandbox is available — object storage
     * configured AND Code Interpreter mode {@code DOCKER}), every enabled,
     * indexed skill folder is offered to this agent's chat turns through the
     * progressive-disclosure harness: a cheap name + description block is
     * appended to the system prompt and the {@code load_skill} / {@code skill_bash}
     * activation tools are added to the toolset so the model can load a skill's
     * instructions and drive its sandboxed container on demand. Default
     * {@code false} keeps existing agents byte-for-byte unchanged (no extra
     * prompt tokens, no skill tools). Has no effect on a {@code NATIVE} or
     * storage-disabled deployment — the harness reports itself unavailable and
     * both contributions are skipped.
     *
     * @since 2026.3.1
     */
    @Column(name = "skills_enabled", nullable = false)
    private boolean skillsEnabled = false;

    /**
     * T145 / §X.5.b — when {@code true}, the agent's attached MCP servers (the
     * {@link #mcpServers} allow-list) are <em>federated</em> to whichever native
     * vendor handles the turn: the OpenAI Responses remote {@code mcp} tool
     * (T138) or the Anthropic MCP Connector (T144). On such a native turn the
     * same servers are <em>not</em> also wired as Turing-side MCP client tools
     * (the {@code TurNativeChatExecutor} suppresses the client callbacks to avoid
     * double-wiring). {@code false} (default) keeps the legacy behaviour: MCP
     * servers are reached only through Turing's in-process MCP client, and the
     * vendor-native MCP comes solely from the per-instance capability config.
     * Has no effect on non-native (Spring AI) turns, where MCP servers always
     * ride as client tools.
     *
     * @since 2026.3.4
     */
    @Column(name = "mcp_native_federation", nullable = false)
    private boolean mcpNativeFederation = false;

    /**
     * T436 / §XXII.1 — per-agent opt-in for live tool-call events on the chat
     * SSE. When {@code true}, every server-side tool invocation in a CALL-mode
     * turn emits a structured {@code "tool_call"} event ({@code start} then
     * {@code end}, with a redacted arg digest, status, and duration) so the UI
     * can show "the agent is calling {@code search_knowledge_base}…" while the
     * tool loop runs. Default {@code false} keeps existing streams byte-for-byte
     * identical (no extra SSE events). Independent of the read-only T427 trace,
     * which is always recorded; this flag only governs the <em>live</em> stream.
     *
     * @since 2026.3.4
     */
    @Column(name = "tool_call_events_enabled", nullable = false)
    private boolean toolCallEventsEnabled = false;

    /**
     * T618 / §XXXIV.6 — per-agent opt-in for true per-past-turn prompt replay.
     * When {@code true} (and object storage is enabled), the executor captures
     * the exact assembled system message + the T617 whole-message list at send
     * time, keyed by conversation id + turn index, onto the pluggable
     * {@link com.viglet.turing.service.storage.TurStorageService} — so the Live
     * Preview can load any <em>past</em> turn verbatim instead of reconstructing
     * the conversation's current state (T612). Because this writes on the chat
     * hot path it is opt-in and bounded (a per-conversation turn ring, a
     * per-entry byte cap, and a retention sweep — see
     * {@code com.viglet.turing.genai.capture.TurPromptCaptureProperties}).
     * Default {@code false} keeps existing agents byte-for-byte unchanged (no
     * extra storage writes). Has no effect on a storage-disabled deployment
     * (the capture service reports itself unavailable and every write is
     * skipped).
     *
     * @since 2026.3.4
     */
    @Column(name = "prompt_capture_enabled", nullable = false)
    private boolean promptCaptureEnabled = false;

    /**
     * T438 / §XXII.3 — per-agent opt-in for frontend ("client") tools. When
     * {@code true} and {@link #clientToolsJson} declares at least one tool, those
     * tools are advertised to the model; a call to one parks the turn and emits a
     * {@code client_tool_call} SSE event for the browser to run (resumed via
     * {@code POST /api/v2/chat/client-tool-result}). Default {@code false} keeps
     * the turn server-only (no client round-trip).
     *
     * @since 2026.3.4
     */
    @Column(name = "client_tools_enabled", nullable = false)
    private boolean clientToolsEnabled = false;

    /**
     * T438 / §XXII.3 — JSON array declaring this agent's client tools:
     * {@code [{"name":"…","description":"…","schema":{…}}]}. {@code schema} is the
     * JSON-Schema for the tool's input parameters, advertised to the model. Only
     * declared names are callable; an unknown name is rejected on resume.
     * {@code longtext} for cross-DB portability. Null/blank → no client tools.
     *
     * @since 2026.3.4
     */
    @Lob
    @Column(name = "client_tools_json")
    private String clientToolsJson;

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
     * T115 / §IX.3.e — opt-in to workspace-backed memory compression. When
     * {@code true} and the older pool (turns OLDER than the
     * {@link #chatMemoryRecentN} window) is estimated to exceed
     * {@link #chatMemoryCompressionThresholdTokens}, those older turns are
     * summarized in a single dedicated LLM call, the summary is stored in the
     * conversation workspace at {@code memory/summary-1-{to}.md}, and a compact
     * summary block replaces them in the prompt context. Composes with T30:
     * the BM25 retriever still surfaces specific high-signal older turns; the
     * summary is the cheap default for the low-signal remainder. Default
     * {@code false} so existing agents are byte-for-byte unchanged.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_compression_enabled", nullable = false)
    private boolean chatMemoryCompressionEnabled = false;

    /**
     * T115 / §IX.3.e — token estimate (older pool) above which compression
     * triggers. Below this the older turns are cheap enough to leave to the
     * T30 retriever / FIFO window, so no summary LLM call is made. Default
     * {@code 8192}.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_compression_threshold_tokens", nullable = false)
    private int chatMemoryCompressionThresholdTokens = 8192;

    /**
     * T115 / §IX.3.e — id of the {@link TurLLMInstance} used for the
     * summarization call. Intended to be an explicitly cheap model (the
     * summary doesn't need a flagship). {@code null}/blank falls back to the
     * default LLM configured in Global Settings.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_compression_llm_id", length = 36)
    private String chatMemoryCompressionLlmId;

    /**
     * T115 / §IX.3.e — minimum wall-clock interval between two summarization
     * calls for the same conversation (ISO-8601 {@link java.time.Duration},
     * e.g. {@code PT1H}). Between regenerations the last summary is reused so
     * a long conversation doesn't pay an LLM call every turn. Default
     * {@code PT1H}.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_compression_interval", length = 32, nullable = false)
    private String chatMemoryCompressionInterval = "PT1H";

    /**
     * T309 / §IX.3.f — run the T115 summarization off the hot path. When
     * {@code true} (default) and a fresh summary is due, the triggering turn
     * enqueues a background regeneration and proceeds uncompressed (reusing the
     * last stored summary if present); the next turn picks up the new summary.
     * When {@code false} the summary LLM call runs synchronously inside the
     * prompt assembler (legacy T115 behavior) — pick this when the summary must
     * be applied on the very turn it becomes due.
     *
     * @since 2026.3.1
     */
    @Column(name = "chat_memory_compression_async", nullable = false)
    private boolean chatMemoryCompressionAsync = true;

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
     * T291 / §XVI.3 (Block L) — soft monthly spend cap in USD for this agent.
     * {@code null} or {@code <= 0} disables the gate. When the agent's
     * month-to-date USD cost (summed from {@code llm_token_usage.cost_usd})
     * reaches this value, the turn-time budget gate fires: it either downgrades
     * the turn to {@link #budgetDowngradeLlmId} (when set and resolvable) or
     * logs a warning and proceeds. Soft by design — it never hard-fails a turn,
     * so a paying conversation is never dropped mid-flight.
     *
     * @since 2026.3.4
     */
    @Column(name = "monthly_budget_usd")
    private Double monthlyBudgetUsd;

    /**
     * T291 / §XVI.3 (Block L) — per-turn soft cost cap in USD. {@code null} or
     * {@code <= 0} disables it. Enforced post-hoc: when a completed turn's
     * {@code cost_usd} exceeds this value a warning is logged (operator signal
     * that a single turn is unusually expensive). Does not abort the turn —
     * the cost is only known after the LLM responds.
     *
     * @since 2026.3.4
     */
    @Column(name = "per_turn_soft_cap_usd")
    private Double perTurnSoftCapUsd;

    /**
     * T291 / §XVI.3 (Block L) — id of a cheaper {@link TurLLMInstance} to switch
     * to for the remainder of the month once {@link #monthlyBudgetUsd} is
     * breached. {@code null}/blank means "warn only" (no downgrade). The
     * instance must be one this agent can use; if it can't be resolved the gate
     * degrades to warn-only.
     *
     * @since 2026.3.4
     */
    @Column(name = "budget_downgrade_llm_id", length = 255)
    private String budgetDowngradeLlmId;

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
     * T166 / §X.9.d — scope for the Anthropic memory tool (T163) file area.
     * {@code CONVERSATION} (default) keeps the {@code memory/} scratchpad isolated
     * per conversation; {@code USER} shares it across every conversation a given
     * authenticated end user has with the agent (per-Keycloak-{@code sub}), so the
     * agent remembers stable preferences. Falls back to per-conversation when no
     * stable user identity is resolvable. Stored as a string to avoid ordinal drift.
     *
     * @since 2026.3.4
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "memory_scope", length = 20, nullable = false)
    private TurMemoryScope memoryScope = TurMemoryScope.CONVERSATION;

    /**
     * Per-agent grounding policy. {@code OPEN} (default) keeps the flexible,
     * general-purpose behaviour (code generation, tool calling, prompt used
     * verbatim). {@code STRICT_RAG} makes the agent answer ONLY from retrieved
     * website content: a non-removable guard is prefixed to the system prompt at
     * runtime, so the grounding cannot be lost by editing the prompt and survives
     * an export/import round-trip. Stored as a string to avoid ordinal drift.
     *
     * @since 2026.3.4
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "grounding_mode", length = 20, nullable = false)
    private TurAgentGroundingMode groundingMode = TurAgentGroundingMode.OPEN;

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
     * T433 / §X.18.b — the per-agent selection of <em>provider-native</em>
     * capabilities (a CSV of {@link com.viglet.turing.genai.nativeapi.TurNativeCapability}
     * keys, e.g. {@code "anthropic-web-search,anthropic-code-execution"}),
     * mirroring {@link #nativeTools}. It is a subset of what the agent's LLM
     * instance is technically capable of (the per-instance matrix, T132); the
     * native executor resolves {@code agentSelected ∩ instanceCapable}.
     *
     * <p><b>Opt-in / legacy.</b> {@code null} means the agent has made no
     * explicit selection — the native path keeps its pre-T433 winner-takes-all
     * behaviour (every instance-enabled native capability is used, no Turing
     * tools attached). A non-null value (even an empty string) opts into the
     * T433 "capacidade é mutex, resto coexiste" path: only the selected
     * provider-native capabilities run, and the agent's Turing/MCP/custom tools
     * whose {@code function} isn't claimed by a selected native pick are
     * attached and run through the native tool-execution loop.
     *
     * @since 2026.3.4
     */
    @Column(length = 2000)
    private String nativeCapabilities;

    /**
     * T435 / §X.18.d — per-agent {@code REQUEST_OPTION} selections as a JSON
     * object keyed by capability key (e.g.
     * {@code {"reasoning-effort":"high","citations":"true"}}). Rendered by the
     * registry-driven "Request Options" settings; each F.13 request-shape task
     * (T152/T160/T164/…) reads its own key when building the provider request.
     * {@code null} = no options set.
     *
     * @since 2026.3.4
     */
    @Column(columnDefinition = "longtext")
    private String requestOptionsJson;

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
