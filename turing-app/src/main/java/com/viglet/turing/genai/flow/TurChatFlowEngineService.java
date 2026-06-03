/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.flow.routine.TurScheduleAgentNodeExecutor;
import com.viglet.turing.genai.flow.router.TurChatFlowRouterDecision;
import com.viglet.turing.genai.flow.router.TurChatFlowRouterDecisionLog;
import com.viglet.turing.genai.flow.router.TurChatFlowRouterMethod;
import com.viglet.turing.genai.flow.strategy.AdvanceContext;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy;
import com.viglet.turing.persistence.dto.agent.TurChatFlowSubmissionDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatanalytics.TurChatSessionOutcome;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase B runtime orchestrator for chat flows. Owns the cross-cutting
 * concerns — graph parsing, state lifecycle, submission recording, and the
 * auto-trigger router — and delegates the actual <em>algorithm</em> for
 * each turn to a {@link TurChatFlowGuardrailStrategy} bean.
 *
 * <p>To add a new guardrail methodology, add a value to
 * {@link TurChatFlowGuardrailMethod} and ship a {@code @Component} that
 * implements {@link TurChatFlowGuardrailStrategy} with that
 * {@code getMethod()}. Spring picks it up automatically; no changes here.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Service
public class TurChatFlowEngineService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Spring cache name for the LLM flow-router's decisions. Keyed by
     * {@code agentId:::sha256(normalized(userMessage))}; value is the picked
     * flow id (or {@code null} when the router said "none"). Evicted on any
     * {@link TurChatFlow} save/delete so admin edits to trigger descriptions
     * propagate without restart.
     *
     * @since 2026.2.8
     */
    public static final String CACHE_ROUTER_DECISION = "turChatFlowRouterDecision";

    /**
     * T27 / §II.2.3 — per-agent {@link SearcherManager} index of trigger
     * descriptions, scored with {@link BM25Similarity} via Lucene
     * {@link MoreLikeThis}. Each entry holds one bucket per
     * {@link Analyzer} (PT/EN) so T25 language routing is preserved.
     *
     * <p>Built lazily on the first procedural route per agent; reused on
     * subsequent turns. Wiped on any {@link TurChatFlow} save/delete via
     * {@link TurChatFlowRouterEvictionListener} so admin edits to trigger
     * descriptions propagate without restart — same semantics as the
     * existing {@link #CACHE_ROUTER_DECISION} hook.
     *
     * <p>Local to this JVM by design: {@code SearcherManager} +
     * {@code ByteBuffersDirectory} are non-serializable live resources that
     * cannot ride a clustered cache. Other nodes rebuild their own copy on
     * their next turn after any save/delete (each node fires its own
     * listener through Hibernate's per-JVM SessionFactory).
     *
     * @since 2026.3.1
     */
    private final ConcurrentMap<String, AgentRouterIndex> routerIndexByAgent =
            new ConcurrentHashMap<>();

    /**
     * Minimum ratio by which the top-scoring flow must beat the runner-up to
     * commit procedurally. Scores come from Lucene MoreLikeThis/BM25 over
     * trigger descriptions. Higher ratio = less procedural usage = more
     * correctness; lower ratio = more speed.
     */
    private static final double PROCEDURAL_DOMINANCE_RATIO = 1.5;

    /**
     * Minimum length (in characters) of at least one whitespace-separated
     * token in the user message for the procedural router to even attempt
     * scoring. Defense-in-depth alongside the single-candidate skip in
     * {@link #tryProceduralRoute}.
     *
     * <p><b>Why a token-length guard and not a BM25 floor?</b> Empirical
     * measurement showed BM25 in tiny analyzer buckets (1-2 docs) is
     * dominated by IDF, not by term frequency or doc length — a 2-char
     * token like {@code "oi"} landing on a quoted negative example
     * ({@code "NÃO ATIVAR para saudações: 'oi', 'olá'..."}) scores around
     * {@code 0.26}, indistinguishable from a legitimate rare-term match
     * like {@code "refund"} hitting {@code "refund reimbursement
     * chargeback"} ({@code 0.32}). No absolute BM25 floor can reliably
     * separate the two without breaking legitimate matches.
     *
     * <p>A query-side discriminator avoids the score-calibration problem
     * entirely: 2-char greeting tokens ({@code "oi"}, {@code "ok"},
     * {@code "vc"}, {@code "tb"}) defer to the LLM router, which can
     * honor the negative-list semantics. Genuine queries with at least
     * one 3+ char content token (e.g. {@code "ir para onde?"},
     * {@code "Q1 results?"}, {@code "refund"}) still hit the procedural
     * fast-path.
     *
     * @since 2026.3.1
     */
    private static final int PROCEDURAL_MIN_TOKEN_LENGTH = 3;

    /**
     * T75 — upper bound on the {@code userMessage} field emitted in the
     * per-turn A/B trace line. Keeps log records bounded so ELK/Loki
     * shippers don't truncate the structured prefix when a visitor
     * pastes a wall of text.
     *
     * @since 2026.3.1
     */
    private static final int VARIANT_TRACE_USER_MSG_MAX = 200;

    /**
     * Per-language Lucene analyzers for the procedural pre-route. Each flow's
     * {@code triggerDescription} (and the user message, scored against that
     * flow) tokenizes through the matching analyzer:
     *
     * <ul>
     *   <li>{@link PortugueseAnalyzer} — PT-BR (default for legacy flows + the
     *       AUTO fallback). Light stemming so {@code "planos" / "plano" /
     *       "carreiras" / "carreira"} collapse, ~120 PT stopwords filtered.</li>
     *   <li>{@link EnglishAnalyzer} — Porter stemming so {@code "running" /
     *       "runs"} share a stem, EN stopwords filtered. Critical for
     *       bilingual deployments where the PT analyzer would leave EN
     *       triggers under-matched (no EN stopword filter and no EN
     *       stemming).</li>
     * </ul>
     *
     * <p>Lucene {@link Analyzer}s are thread-safe — single instance per
     * language shared across all routing calls.
     *
     * <p>T25 ({@link TurChatFlowTriggerLanguage}) widens this from the
     * original single-analyzer setup. Existing flows default to {@code AUTO}
     * — the router picks PT or EN per route based on
     * {@link #detectLanguage(String)} until an admin explicitly tags the flow.
     *
     * @since 2026.3.1 (was a single {@code FLOW_ROUTER_ANALYZER} in 2026.2.8)
     */
    private static final Analyzer ANALYZER_PT = new PortugueseAnalyzer();
    private static final Analyzer ANALYZER_EN = new EnglishAnalyzer();

    /**
     * Lightweight EN stopword set for {@link #detectLanguage(String)} — the
     * heuristic counts hits from this list against PT-equivalent hits. Kept
     * tiny on purpose: the goal isn't accurate classification (Tika would do
     * that better), it's separating "clearly EN" from "clearly PT" descriptions
     * fast and zero-allocation. Common short function words that almost never
     * appear in PT text and vice-versa give the highest signal-to-noise.
     *
     * @since 2026.3.1
     */
    private static final Set<String> EN_HINT_WORDS = Set.of(
            "the", "and", "or", "for", "with", "to", "of", "in", "on", "is", "are",
            "this", "that", "what", "how", "when", "where", "why", "you", "your",
            "i", "me", "my", "do", "does", "want", "need", "would", "should", "can");

    /**
     * PT hint stopwords mirror — same role, opposite language. Avoided overlap
     * with {@link #EN_HINT_WORDS} so neutral text (proper nouns only) returns
     * an even score → defaults to PT (legacy behavior, primary audience).
     *
     * @since 2026.3.1
     */
    private static final Set<String> PT_HINT_WORDS = Set.of(
            "o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das",
            "para", "por", "com", "sem", "que", "como", "quando", "onde",
            "eu", "você", "voce", "meu", "minha", "seu", "sua",
            "quero", "preciso", "gostaria", "tenho", "estou", "está", "esta");

    private final TurChatFlowStateRepository stateRepository;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowSubmissionRepository submissionRepository;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final TurChatSlotEventBus slotEventBus;
    private final com.viglet.turing.service.chatslots.TurChatSlotAuditService slotAuditService;
    private final com.viglet.turing.service.chatanalytics.TurAbBanditService abBanditService;
    private final CacheManager cacheManager;
    private final TurFunctionCallNodeExecutor functionCallExecutor;
    private final TurScheduleAgentNodeExecutor scheduleAgentExecutor;
    private final TurChatWebhookNodeExecutor webhookNodeExecutor;
    private final Map<TurChatFlowGuardrailMethod, TurChatFlowGuardrailStrategy> strategyByMethod;

    public TurChatFlowEngineService(TurChatFlowStateRepository stateRepository,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowSubmissionRepository submissionRepository,
            TurChatAnalyticsService chatAnalyticsService,
            TurChatSlotEventBus slotEventBus,
            com.viglet.turing.service.chatslots.TurChatSlotAuditService slotAuditService,
            com.viglet.turing.service.chatanalytics.TurAbBanditService abBanditService,
            CacheManager cacheManager,
            TurFunctionCallNodeExecutor functionCallExecutor,
            TurScheduleAgentNodeExecutor scheduleAgentExecutor,
            TurChatWebhookNodeExecutor webhookNodeExecutor,
            List<TurChatFlowGuardrailStrategy> strategies) {
        this.stateRepository = stateRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.submissionRepository = submissionRepository;
        this.chatAnalyticsService = chatAnalyticsService;
        this.slotEventBus = slotEventBus;
        this.slotAuditService = slotAuditService;
        this.abBanditService = abBanditService;
        this.cacheManager = cacheManager;
        this.functionCallExecutor = functionCallExecutor;
        this.scheduleAgentExecutor = scheduleAgentExecutor;
        this.webhookNodeExecutor = webhookNodeExecutor;
        // Index strategies by enum so dispatch is O(1) and adding a new
        // strategy needs no edit here — Spring injects the new bean and
        // the map picks it up.
        this.strategyByMethod = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        TurChatFlowGuardrailStrategy::getMethod,
                        Function.identity()));
    }

    // ─────────────────────────── Public API ───────────────────────────

    /**
     * Result of {@link #selectActiveFlow}. Tells the executor which flow (if
     * any) should govern the current chat turn.
     *
     * <p>{@code freshlyTriggered} is {@code true} on the very turn that the
     * router picked this flow — i.e. the user's message IS the trigger
     * signal, not data to feed into the first interactive node. The
     * executor uses this to skip the LlmJudge advance pass on the trigger
     * turn (otherwise the trigger text gets force-captured as the first
     * slot value — the "name = 'Plano de carreira pessoal'" bug). On
     * continuation turns it's {@code false}.
     */
    public record FlowSelection(TurChatFlow flow, ChatFlowGraph graph, TurChatFlowState state,
            boolean freshlyTriggered) {
    }

    /**
     * Outcome of {@link #advance} — the (possibly updated) state plus an
     * optional override that replaces the assistant's text when the engine
     * decided the original reply went off-topic. Only the {@code LLM_JUDGE}
     * and {@code STRUCTURED_OUTPUT} strategies set {@code assistantOverride}.
     */
    public record AdvanceResult(TurChatFlowState state, String assistantOverride) {
        public static AdvanceResult of(TurChatFlowState state) {
            return new AdvanceResult(state, null);
        }
    }

    /** Parses the flow's {@code definitionJson} into a {@link ChatFlowGraph}. */
    public Optional<ChatFlowGraph> parseGraph(TurChatFlow flow) {
        if (flow == null || flow.getDefinitionJson() == null || flow.getDefinitionJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OBJECT_MAPPER.readValue(flow.getDefinitionJson(), ChatFlowGraph.class));
        } catch (JacksonException e) {
            log.warn("[FlowEngine] Failed to parse definitionJson for flow '{}': {}",
                    flow.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Loads the runtime state for {@code (conversationId, flow)} or creates
     * a fresh one anchored at the graph's first interactive node. When the
     * conversation already has a sub-flow chain on the requested flow, the
     * deepest active leaf is returned so the caller resumes where the user
     * left off.
     */
    public Optional<TurChatFlowState> loadOrInitState(String conversationId,
            TurChatFlow flow,
            ChatFlowGraph graph) {
        if (conversationId == null || conversationId.isBlank() || flow == null) {
            return Optional.empty();
        }
        Optional<TurChatFlowState> existing = stateRepository
                .findByConversationIdAndFlow_Id(conversationId, flow.getId());
        if (existing.isPresent()) {
            // Resume at the deepest active leaf — the user may already be
            // inside a sub-flow invoked from this flow.
            return Optional.of(resolveActiveLeaf(existing.get()));
        }
        Optional<ChatFlowNode> startNode = graph.startNode();
        if (startNode.isEmpty()) {
            log.warn("[FlowEngine] Flow '{}' has no start node — cannot initialize state", flow.getId());
            return Optional.empty();
        }
        ChatFlowNode anchor = ChatFlowOps.firstInteractiveNode(graph, startNode.get())
                .orElse(startNode.get());
        TurChatFlowState created = new TurChatFlowState();
        created.setConversationId(conversationId);
        created.setFlow(flow);
        created.setCurrentNodeId(anchor.id());
        created.setVariablesJson("{}");
        // If the very first interactive node is a condition (rare but legal —
        // a flow that branches immediately on context the user already
        // provided), evaluate it before the user sends their next turn.
        ChatFlowOps.walkThroughConditions(created, graph, null);
        TurChatFlowState saved = stateRepository.save(created);
        // First interactive node could itself be a Sub Flow (or jump to one
        // through a chain of conditions): descend immediately so the next
        // turn lands on the sub-flow's prompt.
        return Optional.of(walkTransparentNodes(saved, null));
    }

    /** Returns the node currently active in the conversation, if resolvable. */
    public Optional<ChatFlowNode> currentNode(TurChatFlowState state, ChatFlowGraph graph) {
        return ChatFlowOps.currentNode(state, graph);
    }

    /**
     * Renders the system-prompt addendum for the current node, dispatching
     * to the strategy bound to the flow's {@link TurChatFlowGuardrailMethod}.
     */
    public String buildSystemPromptAddendum(TurChatFlow flow,
            TurChatFlowState state,
            ChatFlowGraph graph) {
        Optional<ChatFlowNode> node = currentNode(state, graph);
        if (node.isEmpty()) {
            return "";
        }
        // T72 — apply any per-node A/B variant before the strategy reads the
        // node's aiInstruction, so the LLM sees the assigned arm's wording.
        ChatFlowNode effective = node.get().resolveVariant(state.getConversationId());
        return resolveStrategy(flow).buildSystemPromptAddendum(flow, effective, state, graph);
    }

    /**
     * Runs one chat turn through the strategy bound to {@code flow}. The
     * strategy may mutate {@code state} (current node, variables) and may
     * return an override string the executor should display in place of the
     * raw LLM reply. The engine then walks any transparent transitions
     * (condition chains, Sub Flow descent / ascent), persists every touched
     * state, and — when the turn landed on the root flow's terminal node —
     * records an immutable submission row.
     *
     * <p><b>§I.5 step 5 / T13:</b> wrapped in {@code @Transactional} so the
     * strategy mutation, the multiple intermediate persists across the
     * transparent walk (slot SET / writeSlot / persona / switch landing —
     * each does its own {@code stateRepository.save(...)} for crash
     * survivability mid-walk), and the post-walk save coalesce into ONE
     * commit. Without this, each {@code save} ran in its own implicit
     * transaction — so a slot-heavy branch (e.g. the 5-slot transparent
     * chain in {@code programa-match-ee}) triggered 6+ separate DB commits
     * per turn, plus the caller-level save. Now they batch.
     *
     * <p>Patch #16 of the §I.3 inventory documents this "double-save"
     * pattern; with the @Transactional boundary it becomes a single
     * persistence point regardless of how many in-walk saves the strategy
     * paths take. The intermediate {@code save()} calls are intentionally
     * kept (not deleted) — they update the JPA persistence context so
     * subsequent reads inside the same advance see the latest cursor /
     * variables, but no longer cost separate DB round-trips.
     */
    @Transactional
    public AdvanceResult advance(TurChatFlow flow,
            TurChatFlowState state,
            ChatFlowGraph graph,
            String userMessage,
            String assistantMessage,
            ChatModel auxiliaryModel) {
        if (state == null || graph == null) {
            return AdvanceResult.of(state);
        }
        // Refresh from DB so we pick up any state mutation made during the
        // chat call by side effects we can't see from here — primarily slot
        // writes from Custom Tool callbacks ({@code TurCustomToolSlotHelper}
        // saves via a separate {@code findByConversationId} + {@code save}
        // round-trip, leaving the in-memory {@code state} parameter stale).
        // Without this refresh the strategy's {@code writeVariables} at the
        // end of {@code advance} silently overwrites those tool writes,
        // wiping slots like {@code programas_match} that drive the UI.
        if (state.getId() != null) {
            state = stateRepository.findById(state.getId()).orElse(state);
        }
        Optional<ChatFlowNode> nodeOpt = currentNode(state, graph);
        if (nodeOpt.isEmpty()) {
            return AdvanceResult.of(state);
        }
        String previousNodeId = state.getCurrentNodeId();
        // Snapshot the persisted variables BEFORE the strategy runs so we can
        // detect slot captures (LlmJudge `collected_value` on an aiQuestion
        // never goes through a `slot`/`writeSlot` node, so the SSE publish in
        // those branches doesn't fire — without this diff the slot inspector
        // and any UI subscribed via SSE never learn about Q&A-captured slots
        // until the next poll, which the SDK disables once SSE is active).
        String previousVariablesJson = state.getVariablesJson();
        log.info("[FlowEngine] advance: flow='{}' conv='{}' node='{}' guardrail={} userMsg='{}'",
                flow.getId(), state.getConversationId(), previousNodeId,
                flow.getGuardrailMethod(),
                userMessage == null ? "" : truncate(userMessage, 80));
        // T72 — swap in the per-node A/B variant assigned to this conversation
        // (sticky, weighted) before the strategy guards the user's reply, so
        // the same arm the LLM was prompted with is the one validated against.
        ChatFlowNode activeNode = nodeOpt.get().resolveVariant(state.getConversationId());
        AdvanceContext ctx = new AdvanceContext(state, graph, activeNode,
                userMessage, assistantMessage, auxiliaryModel, flow);
        String override = resolveStrategy(flow).advance(ctx);
        // Condition nodes are transparent to the chat — the LLM never sees them
        // as a "Goal" because they have none. Walk through any chain of
        // conditions in the same turn so the next chat round lands on the next
        // interactive node.
        ChatFlowOps.walkThroughConditions(state, graph, auxiliaryModel);
        if (!Objects.equals(previousNodeId, state.getCurrentNodeId())) {
            log.info("[FlowEngine] advance: '{}' → '{}' vars={}",
                    previousNodeId, state.getCurrentNodeId(),
                    state.getVariablesJson());
        }
        // Persist the strategy's mutations on the active state before we
        // potentially descend into a sub-flow (descent reads state.id as the
        // child's parentStateId, so the parent must already have an id).
        state = stateRepository.save(state);
        // Walk any Sub Flow boundaries: descend on a SubFlow node, ascend on
        // an end node that has a parent state. Returns the new active leaf.
        String preWalkNodeId = state.getCurrentNodeId();
        TurChatFlowState leaf = walkTransparentNodes(state, auxiliaryModel);
        // Two persistence cases:
        //  (a) Descent/ascent: leaf is a DIFFERENT row (sub-flow child or
        //      parent on ascend). Save the new leaf so its id and currentNodeId
        //      are durable before the next turn loads it.
        //  (b) Same row, but walkTransparentNodes mutated currentNodeId in
        //      place (e.g. switch → aiQuestion landing, condition resolution,
        //      satisfied-question fast-forward). Without an explicit save the
        //      cursor stays at the value persisted by the line above — i.e.
        //      the switch/condition node — and the next turn's LlmJudge
        //      processes the user message AGAINST that intermediate node,
        //      rejecting valid input as off-topic. Visible symptom: visitor
        //      sees "Beleza — qual é o código?" (regen for the landing
        //      aiQuestion) and types the coupon, only to be redirected
        //      with "Poderia me informar se você tem um cupom?" because
        //      the engine state is still parked on switch-coupon-intent.
        if (leaf != state) {
            leaf = stateRepository.save(leaf);
        } else if (!Objects.equals(preWalkNodeId, leaf.getCurrentNodeId())) {
            leaf = stateRepository.save(leaf);
        }
        // Record a submission only when the ROOT flow has just terminated —
        // sub-flow ends pop instead of recording.
        if (leaf.getParentStateId() == null) {
            ChatFlowGraph leafGraph = parseGraph(leaf.getFlow()).orElse(graph);
            recordSubmissionIfTerminal(leaf.getFlow(), leaf, leafGraph, previousNodeId);
        }
        // SSE notification: if the strategy mutated the variables map (e.g.
        // LlmJudge captured `collected_value` on an aiQuestion node, or
        // condition walk wrote an intermediate value), push the merged slot
        // map so SSE-bound clients (slot inspector, portal cards) see the
        // new values without waiting for a poll tick.
        if (!Objects.equals(previousVariablesJson, leaf.getVariablesJson())) {
            slotEventBus.publish(leaf.getConversationId(),
                    listSlotsForConversation(leaf.getConversationId()).slots());
            // T60 audit log: diff the persisted variables before/after the
            // advance + transparent walk and record each changed slot with
            // source=NODE. originDetail carries the previous/current node
            // ids so the timeline can show which step produced the write.
            try {
                Map<String, String> before = ChatFlowOps.readVariablesJson(previousVariablesJson);
                Map<String, String> after = ChatFlowOps.readVariablesJson(leaf.getVariablesJson());
                String detail = "node=" + previousNodeId
                        + (Objects.equals(previousNodeId, leaf.getCurrentNodeId())
                                ? "" : "->" + leaf.getCurrentNodeId());
                slotAuditService.recordDiff(leaf.getConversationId(), before, after,
                        com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource.NODE,
                        detail);
            } catch (RuntimeException auditEx) {
                log.debug("[FlowEngine] slot-audit skipped for conv={}: {}",
                        leaf.getConversationId(), auditEx.getMessage());
            }
        }
        // T75 — structured per-turn trace line for A/B experiments. Only
        // emits when the flow is part of an experiment (non-blank
        // experimentKey); plain flows incur a single null-check. Format
        // is `experimentKey:variantLabel:nodeId:userMessage` so operators
        // grepping for `[A/B Trace]` get a one-line-per-turn audit of the
        // exact variant + node path in production — matches the spec
        // verbatim and pairs with T70 (Thompson sampling) and T69
        // (scorecard) by giving a path-level forensic source when an
        // aggregate metric surfaces a regression.
        formatVariantTrace(flow, leaf.getCurrentNodeId(), userMessage)
                .ifPresent(trace -> log.info("[A/B Trace] {}", trace));
        // T72 — sibling node-level trace. Fires only when the node the turn
        // ran against carried a per-node experiment, so plain nodes incur a
        // single null-check. Format: nodeExperimentKey:variantLabel:nodeId.
        ChatFlowNode.formatNodeVariantTrace(activeNode, leaf.getConversationId())
                .ifPresent(trace -> log.info("[A/B Node Trace] {}", trace));
        return new AdvanceResult(leaf, override);
    }

    /**
     * T75 — format the per-turn A/B variant trace line. Returns
     * {@link Optional#empty()} when {@code flow} is null or has a blank
     * {@code experimentKey} (i.e. is not part of an experiment), so
     * non-experiment flows emit nothing. Package-private for unit testing.
     *
     * <p>Format: {@code experimentKey:variantLabel:nodeId:userMessage}.
     * The user message is truncated to {@link #VARIANT_TRACE_USER_MSG_MAX}
     * chars and stripped of CR/LF so a single turn always occupies a
     * single log line.
     *
     * @since 2026.3.1
     */
    static Optional<String> formatVariantTrace(TurChatFlow flow, String nodeId,
            String userMessage) {
        if (flow == null) {
            return Optional.empty();
        }
        String key = flow.getExperimentKey();
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String label = flow.getVariantLabel();
        String safeLabel = (label == null || label.isBlank()) ? "(unset)" : label;
        String safeNode = nodeId == null ? "" : nodeId;
        String safeMsg = userMessage == null ? "" : userMessage;
        if (safeMsg.length() > VARIANT_TRACE_USER_MSG_MAX) {
            safeMsg = safeMsg.substring(0, VARIANT_TRACE_USER_MSG_MAX) + "…";
        }
        // Collapse CR/LF so each turn occupies exactly one log line —
        // line-oriented shippers (filebeat / promtail) can ingest cleanly
        // and `grep '[A/B Trace]'` returns one match per turn.
        safeMsg = safeMsg.replace('\n', ' ').replace('\r', ' ');
        return Optional.of(key + ":" + safeLabel + ":" + safeNode + ":" + safeMsg);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /**
     * T121 — outcome of a {@link #resumeSuspendedFlow} call. {@code resumed}
     * is the number of state rows that were parked on a {@code suspend}
     * node and advanced past it; {@code nothingToResume} means the
     * conversation had no states parked at suspend.
     *
     * @since 2026.3.1
     */
    public record ResumeResult(int resumed, boolean nothingToResume) {
    }

    /**
     * T121 / §IX.6.a — resume every chat-flow state for {@code conversationId}
     * whose cursor currently sits on a {@code suspend} node. Optionally
     * applies {@code slotUpdates} BEFORE advancing so a webhook callback
     * can carry the result of whatever external work was awaited (e.g.
     * an approval decision, a CRM enrichment, a scheduled job's output).
     *
     * <p>Safe to call any number of times: states not parked on a
     * {@code suspend} node are skipped. The chat path treats a state
     * parked at a {@code suspend} node as "no LLM turn this round" via
     * the executor short-circuit; this method walks past the suspend
     * node and advances to the next interactive node, fan-out into
     * sub-flows or condition walks as usual.
     *
     * @since 2026.3.1
     */
    @Transactional
    public ResumeResult resumeSuspendedFlow(String conversationId,
            Map<String, String> slotUpdates, String resumeReason) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ResumeResult(0, true);
        }
        // Apply slot updates first so the post-advance walker reads the
        // freshest values (a condition node downstream of the suspend
        // node may branch on what the webhook just delivered).
        if (slotUpdates != null && !slotUpdates.isEmpty()) {
            for (Map.Entry<String, String> e : slotUpdates.entrySet()) {
                writeSlot(conversationId, e.getKey(), e.getValue(),
                        com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource.ENDPOINT,
                        resumeReason == null ? "resume" : "resume=" + resumeReason);
            }
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        int resumed = 0;
        for (TurChatFlowState state : states) {
            ChatFlowGraph graph = parseGraph(state.getFlow()).orElse(null);
            if (graph == null) continue;
            Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
            if (currentOpt.isEmpty()) continue;
            if (!"suspend".equals(currentOpt.get().type())) continue;
            log.info("[FlowEngine] resume: conv='{}' walking past suspend node '{}' (reason='{}')",
                    conversationId, state.getCurrentNodeId(), resumeReason);
            ChatFlowOps.advanceToFirstEdge(state, graph, currentOpt.get());
            state = stateRepository.save(state);
            TurChatFlowState leaf = walkTransparentNodes(state, null);
            if (leaf != state) {
                stateRepository.save(leaf);
            }
            resumed++;
        }
        return new ResumeResult(resumed, resumed == 0);
    }

    /**
     * T107 — outcome of a {@link #submitForm} call. {@code fieldsWritten} is
     * the number of form fields persisted as slots; {@code advancedStates} is
     * the number of chat-flow state rows that were parked on the now-satisfied
     * native {@code formCapture} node and walked past it; {@code currentNodeId}
     * is the cursor of the (first) state after the walk — the node the next
     * chat turn will render.
     *
     * @since 2026.3.1
     */
    public record FormSubmitResult(int fieldsWritten, int advancedStates, String currentNodeId) {
    }

    /**
     * T107 / §VII.13.d — apply a native multi-field {@code formCapture}
     * submission. Each entry of {@code values} is written to its named slot
     * (audit source {@code NODE}, origin {@code "formCapture"}); then every
     * chat-flow state for the conversation whose cursor sits on a native
     * {@code formCapture} node that is now {@link ChatFlowOps#isNativeFormSatisfied
     * satisfied} is advanced past it (transparent walk included), so the next
     * chat turn lands on the following node without re-asking the form
     * field-by-field.
     *
     * <p>Idempotent and lenient: a blank field name is skipped; a state parked
     * on a node that is not a satisfied native form is left untouched (its
     * cursor is still reported so the caller can decide whether to continue
     * the conversation).
     *
     * @since 2026.3.1
     */
    @Transactional
    public FormSubmitResult submitForm(String conversationId, Map<String, String> values) {
        if (conversationId == null || conversationId.isBlank()) {
            return new FormSubmitResult(0, 0, null);
        }
        int fieldsWritten = 0;
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                writeSlot(conversationId, e.getKey(), e.getValue(),
                        com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource.NODE,
                        "formCapture");
                fieldsWritten++;
            }
        }
        // Re-read after the writes so readVariables() sees the fresh slots.
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        int advanced = 0;
        String currentNodeId = null;
        for (TurChatFlowState state : states) {
            ChatFlowGraph graph = parseGraph(state.getFlow()).orElse(null);
            if (graph == null) {
                continue;
            }
            Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
            if (currentOpt.isEmpty()) {
                currentNodeId = currentNodeId == null ? state.getCurrentNodeId() : currentNodeId;
                continue;
            }
            ChatFlowNode current = currentOpt.get();
            Map<String, String> vars = ChatFlowOps.readVariables(state);
            if (ChatFlowOps.isNativeForm(current)
                    && ChatFlowOps.isNativeFormSatisfied(current, vars)) {
                log.info("[FlowEngine] form-submit: conv='{}' walking past satisfied form node '{}'",
                        conversationId, state.getCurrentNodeId());
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = stateRepository.save(state);
                TurChatFlowState leaf = walkTransparentNodes(state, null);
                if (leaf != state) {
                    state = stateRepository.save(leaf);
                }
                advanced++;
            }
            currentNodeId = state.getCurrentNodeId();
        }
        return new FormSubmitResult(fieldsWritten, advanced, currentNodeId);
    }

    /**
     * T121 — returns true when any state attached to {@code conversationId}
     * is currently parked at a {@code suspend} node. The chat executor
     * checks this to short-circuit the LLM call and reply with a parked
     * banner instead of running the strategy against a non-interactive
     * node.
     *
     * @since 2026.3.1
     */
    public Optional<String> findSuspendedReason(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return Optional.empty();
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        for (TurChatFlowState state : states) {
            ChatFlowGraph graph = parseGraph(state.getFlow()).orElse(null);
            if (graph == null) continue;
            Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
            if (currentOpt.isEmpty()) continue;
            ChatFlowNode current = currentOpt.get();
            if ("suspend".equals(current.type())) {
                String label = current.label();
                return Optional.of(label == null || label.isBlank()
                        ? "suspended"
                        : label);
            }
        }
        return Optional.empty();
    }

    /**
     * T48 — re-walks every chat-flow state for {@code conversationId} whose
     * cursor currently sits on a {@code scheduleAgent} node, so a routine
     * completion delivered through the slot bus advances the flow without
     * waiting for the user's next message. Safe to call any number of
     * times: states not parked on a {@code scheduleAgent} are skipped, and
     * the executor's COMPLETED/TIMEOUT/WAITING outcomes naturally idempotent.
     *
     * <p>Invoked by {@code TurChatFlowAutoResumeService} on every slot event.
     *
     * @since 2026.3.1
     */
    @Transactional
    public void resumeParkedScheduleAgents(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        for (TurChatFlowState state : states) {
            ChatFlowGraph graph = parseGraph(state.getFlow()).orElse(null);
            if (graph == null) continue;
            Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
            if (currentOpt.isEmpty()) continue;
            if (!"scheduleAgent".equals(currentOpt.get().type())) continue;
            log.info("[FlowEngine] auto-resume: conv='{}' re-walking parked state '{}'",
                    conversationId, state.getId());
            TurChatFlowState leaf = walkTransparentNodes(state, null);
            if (leaf != state) {
                stateRepository.save(leaf);
            }
        }
    }

    private TurChatFlowGuardrailStrategy resolveStrategy(TurChatFlow flow) {
        TurChatFlowGuardrailMethod method = flow.getGuardrailMethod() != null
                ? flow.getGuardrailMethod()
                : TurChatFlowGuardrailMethod.HEURISTIC;
        TurChatFlowGuardrailStrategy strategy = strategyByMethod.get(method);
        if (strategy != null) {
            return strategy;
        }
        // Defense in depth: if a flow somehow references a method with no
        // bean (e.g. enum value added without a strategy), fall back to
        // HEURISTIC instead of crashing the chat turn.
        log.warn("[FlowEngine] No strategy bound for method '{}' — falling back to HEURISTIC",
                method);
        return strategyByMethod.get(TurChatFlowGuardrailMethod.HEURISTIC);
    }

    /**
     * Snapshot of the conversation's active flow state — flow identity,
     * the current cursor node, and any A/B experiment context. Returned
     * by {@code GET /chat/state} so React tooling (SlotInspector,
     * useTuringFlowState, useTuringExperiment) can render debug panels
     * or variant-aware UI without round-tripping to /slots.
     *
     * <p>All fields are nullable — when the conversation has no active
     * state (visitor hasn't sent the first message yet), the DTO is
     * returned with {@code flowId=null}. Callers should treat that as
     * "no active flow yet, retry later".
     *
     * @since 2026.2.7
     */
    public record ConversationStateDto(
            String conversationId,
            String flowId,
            String flowName,
            String currentNodeId,
            String guardrailMethod,
            String experimentKey,
            String variantLabel,
            /**
             * T121 — non-null when the leaf state's cursor sits on a
             * {@code suspend} node; carries the node's label/reason so
             * the portal can render a "Waiting for external system..."
             * banner. {@code null} means the conversation is not parked.
             */
            String suspendedReason) {
    }

    /**
     * Returns the active state for the conversation, picking the LEAF state
     * row (deepest descendant in a sub-flow chain) to surface the actually-
     * driving flow when the conversation is mid sub-flow. When multiple
     * flows have state rows for the same conversation, the most recently
     * updated leaf wins — matches the engine's continuation logic.
     *
     * @since 2026.2.7
     */
    public ConversationStateDto getConversationState(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ConversationStateDto(conversationId, null, null, null, null, null, null, null);
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        if (states.isEmpty()) {
            return new ConversationStateDto(conversationId, null, null, null, null, null, null, null);
        }
        // Skip rows that point at other rows as parent — we want the leaf.
        Set<String> hasDescendant = states.stream()
                .map(TurChatFlowState::getParentStateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        TurChatFlowState leaf = states.stream()
                .filter(s -> !hasDescendant.contains(s.getId()))
                .sorted(Comparator.comparing(TurChatFlowState::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .findFirst()
                .orElse(states.get(0));
        TurChatFlow flow = leaf.getFlow();
        // T121 — surface the parked reason when the leaf cursor sits on a
        // suspend node. The portal renders this as a "Waiting..." banner.
        String suspendedReason = null;
        if (flow != null) {
            ChatFlowGraph leafGraph = parseGraph(flow).orElse(null);
            if (leafGraph != null) {
                Optional<ChatFlowNode> currentOpt = leafGraph.nodeById(leaf.getCurrentNodeId());
                if (currentOpt.isPresent() && "suspend".equals(currentOpt.get().type())) {
                    String label = currentOpt.get().label();
                    suspendedReason = label == null || label.isBlank() ? "suspended" : label;
                }
            }
        }
        return new ConversationStateDto(
                conversationId,
                flow == null ? null : flow.getId(),
                flow == null ? null : flow.getName(),
                leaf.getCurrentNodeId(),
                flow == null || flow.getGuardrailMethod() == null
                        ? null : flow.getGuardrailMethod().name(),
                flow == null ? null : flow.getExperimentKey(),
                flow == null ? null : flow.getVariantLabel(),
                suspendedReason);
    }

    /**
     * Newest-first list of every finished run of {@code flow}, read from
     * the append-only {@link TurChatFlowSubmission} table.
     */
    public List<TurChatFlowSubmissionDto> listSubmissions(TurChatFlow flow) {
        if (flow == null) {
            return List.of();
        }
        return submissionRepository.findByFlow_IdOrderByCompletedAtDesc(flow.getId()).stream()
                .map(s -> new TurChatFlowSubmissionDto(
                        s.getConversationId(),
                        flow.getId(),
                        s.getCompletedAt(),
                        ChatFlowOps.readVariablesJson(s.getVariablesJson()),
                        s.getUserId(),
                        s.getEndNodeId()))
                .toList();
    }

    /**
     * T65 / §VII.6.f — raw {@code chat_flow_state} rows for a conversation,
     * flattened to export snapshots (parsed variable map + cursor node +
     * sub-flow parent link). Newest first. Powers the "Export conversation"
     * debug button alongside {@link #listSubmissionsForConversation} and
     * {@link #listSlotsForConversation}.
     *
     * @since 2026.3.1
     */
    @Transactional(readOnly = true)
    public List<com.viglet.turing.persistence.dto.agent.TurChatSessionExportDto.FlowState> exportFlowStates(
            String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return stateRepository.findByConversationId(conversationId).stream()
                .sorted(Comparator.comparing(TurChatFlowState::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .map(state -> {
                    TurChatFlow flow = state.getFlow();
                    return new com.viglet.turing.persistence.dto.agent.TurChatSessionExportDto.FlowState(
                            state.getId(),
                            flow == null ? null : flow.getId(),
                            flow == null ? null : flow.getName(),
                            state.getCurrentNodeId(),
                            state.getParentStateId(),
                            ChatFlowOps.readVariablesJson(state.getVariablesJson()),
                            state.getUpdatedAt());
                })
                .toList();
    }

    /**
     * T65 / §VII.6.f — finished flow runs for a single conversation read from
     * the append-only {@code chat_flow_submission} table, newest first.
     * Conversation-scoped sibling of {@link #listSubmissions(TurChatFlow)}.
     *
     * @since 2026.3.1
     */
    @Transactional(readOnly = true)
    public List<TurChatFlowSubmissionDto> listSubmissionsForConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return submissionRepository.findByConversationIdOrderByCompletedAtDesc(conversationId).stream()
                .map(s -> {
                    TurChatFlow flow = s.getFlow();
                    return new TurChatFlowSubmissionDto(
                            s.getConversationId(),
                            flow == null ? null : flow.getId(),
                            s.getCompletedAt(),
                            ChatFlowOps.readVariablesJson(s.getVariablesJson()),
                            s.getUserId(),
                            s.getEndNodeId());
                })
                .toList();
    }

    /**
     * Aggregates every slot captured during a chat session into a single
     * flat map. Slots are conversation-scoped — sub-flows in one chain
     * already share their variable map through descent/ascent, and across
     * separate root flows the AI agent treats slots as globally addressable
     * by name. Merge order is oldest-first so the most recent value of a
     * shared slot wins.
     *
     * <p>The {@code conversationId} argument must match the value persisted
     * by the SDK in the {@code TUR_SESSION} cookie when the chat started —
     * it is the same string the engine writes to {@code chat_flow_state
     * .conversationId} on every turn.
     *
     * @since 2026.2.7
     */
    public TurChatSessionSlotsDto listSlotsForConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new TurChatSessionSlotsDto(conversationId, Map.of());
        }

        Map<String, String> slots = new LinkedHashMap<>();

        submissionRepository.findByConversationIdOrderByCompletedAtDesc(conversationId).stream()
                .sorted(Comparator.comparing(TurChatFlowSubmission::getCompletedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(submission -> slots.putAll(
                        ChatFlowOps.readVariablesJson(submission.getVariablesJson())));

        stateRepository.findByConversationId(conversationId).stream()
                .sorted(Comparator.comparing(TurChatFlowState::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(state -> slots.putAll(
                        ChatFlowOps.readVariablesJson(state.getVariablesJson())));

        return new TurChatSessionSlotsDto(conversationId, slots);
    }

    /**
     * Writes {@code name = value} on every chat-flow state attached to the
     * conversation, mirroring what {@code slots.set(...)} does from a Custom
     * Tool Groovy script — same persistence path, same merge semantics. Used
     * by the public {@code POST /chat/slots} endpoint so a React component
     * can force a slot (e.g. user clicks an "área = Liderança" button) and
     * skip the matching question on the next chat turn.
     *
     * <p>Returns the number of state rows touched: {@code 0} means the
     * conversation has no active flow yet — the caller should send the first
     * chat message before writing slots so the engine has a state to merge
     * into.
     *
     * @param conversationId active conversation id from {@code TUR_SESSION}
     * @param name           slot name; blank → no-op (returns 0)
     * @param value          slot value; {@code null} is coerced to "" so the
     *                       slot ends up keyed but cleared (matches the
     *                       {@code slot} chat-flow node convention)
     * @return number of {@code TurChatFlowState} rows updated
     * @since 2026.2.7
     */
    public int writeSlot(String conversationId, String name, String value) {
        return writeSlot(conversationId, name, value,
                com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource.ENDPOINT, null);
    }

    /**
     * Source-aware overload: the audit log captures which subsystem produced
     * the write so the SlotInspector timeline can render a source badge. The
     * 3-arg overload defaults to {@code ENDPOINT} for backwards compatibility
     * with the public {@code POST /chat/slots} caller.
     *
     * @since 2026.3.1
     */
    public int writeSlot(String conversationId, String name, String value,
            com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource source,
            String originDetail) {
        if (conversationId == null || conversationId.isBlank()
                || name == null || name.isBlank()) {
            return 0;
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        if (states.isEmpty()) {
            log.info("[FlowEngine.writeSlot] NO-OP — no chat-flow states for conv={} (slot '{}' skipped)",
                    conversationId, name);
            return 0;
        }
        String coerced = value == null ? "" : value;
        int touched = 0;
        // Capture pre-write merged value so the audit row carries the
        // accurate "before" — reads the same listSlotsForConversation()
        // call shape the SSE publish at the end uses.
        String previousValue = listSlotsForConversation(conversationId).slots().get(name);
        for (TurChatFlowState state : states) {
            Map<String, String> vars = new LinkedHashMap<>(ChatFlowOps.readVariables(state));
            vars.put(name, coerced);
            ChatFlowOps.writeVariables(state, vars);
            stateRepository.save(state);
            touched++;
        }
        log.info("[FlowEngine.writeSlot] conv={} slot='{}' wrote {} chars across {} state(s)",
                conversationId, name, coerced.length(), touched);
        // Notify SSE subscribers with the full merged slot map — clients
        // never have to diff, and the cost of recomputing is one extra
        // query (typically <1 ms).
        slotEventBus.publish(conversationId, listSlotsForConversation(conversationId).slots());
        slotAuditService.record(conversationId, name, previousValue, coerced,
                source != null ? source : com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource.ENDPOINT,
                originDetail);
        return touched;
    }

    /**
     * Drops the persisted state for {@code (conversationId, flowId)}. The
     * next chat turn rebuilds it from the START node.
     *
     * @return true when a row was deleted, false when there was none.
     */
    public boolean resetState(String conversationId, String flowId) {
        if (conversationId == null || conversationId.isBlank()
                || flowId == null || flowId.isBlank()) {
            return false;
        }
        Optional<TurChatFlowState> existing = stateRepository
                .findByConversationIdAndFlow_Id(conversationId, flowId);
        if (existing.isEmpty()) {
            return false;
        }
        stateRepository.delete(existing.get());
        log.info("[FlowEngine] Reset state for conversation '{}' on flow '{}'",
                conversationId, flowId);
        return true;
    }

    /**
     * Drops every persisted flow state attached to {@code conversationId} that
     * belongs to {@code agentId}. Used by public consumers (search "AI Mode")
     * that don't know which flow the auto-router picked but want a clean slate
     * on "New chat" — far simpler than asking the client to enumerate flows.
     *
     * @return number of state rows deleted.
     */
    public int resetAllStatesForAgent(String conversationId, String agentId) {
        if (conversationId == null || conversationId.isBlank()
                || agentId == null || agentId.isBlank()) {
            return 0;
        }
        List<TurChatFlowState> states = stateRepository
                .findByConversationIdAndFlow_TurAIAgent_Id(conversationId, agentId);
        if (states.isEmpty()) {
            return 0;
        }
        stateRepository.deleteAll(states);
        log.info("[FlowEngine] Reset {} flow state(s) for conversation '{}' on agent '{}'",
                states.size(), conversationId, agentId);
        return states.size();
    }

    /**
     * Outcome of {@link #pinFlowForConversation}. {@code success=false} carries
     * a {@link #reason} that the {@code POST /chat/flow-select} endpoint
     * surfaces to the caller verbatim — useful in admin UIs / SDK error toasts
     * to explain why a deep link (e.g. {@code ?flow=in-company}) failed to
     * land.
     */
    public record PinResult(boolean success, String pinnedFlowId, String pinnedFlowName,
            String reason) {
        public static PinResult ok(TurChatFlow flow) {
            return new PinResult(true, flow.getId(), flow.getName(), null);
        }

        public static PinResult error(String reason) {
            return new PinResult(false, null, null, reason);
        }
    }

    /**
     * T92 / §VII.11.b — pins a specific flow on the conversation, overriding
     * the LLM router for every subsequent turn until the flow terminates (or
     * another pin overrides it). Used by deep links like
     * {@code ?flow=in-company} that have to land the visitor on a specific
     * persona before they type anything.
     *
     * <p>Steps:
     * <ol>
     *   <li>Resolve {@code flowSelector} on {@code agent}'s flows — id match
     *       first, case-insensitive name match second.</li>
     *   <li>Snapshot the conversation's currently captured slots (so values
     *       already filled — name, email, role — carry over instead of being
     *       silently lost on the pin).</li>
     *   <li>Reset every other in-progress flow state for this conversation
     *       on this agent, so the engine's continuation rule cannot keep the
     *       caller on a stale flow.</li>
     *   <li>Pre-create the chosen flow's state with the snapshot already
     *       written in, then walk transparent / satisfied-question nodes so
     *       the cursor lands on the first node that genuinely needs user
     *       input. The next chat turn picks this state via the engine's
     *       continuation rule — no caller-side {@code flowId} parameter
     *       needed.</li>
     * </ol>
     *
     * <p>Slots survive the pin via the snapshot-and-seed dance, so a visitor
     * who switches from the B2C lead-capture flow to the B2B in-company quote
     * mid-conversation keeps their {@code name} and {@code cargo_atual}. The
     * receiving flow's satisfied-questions walker then auto-skips any
     * aiQuestion node whose {@code outputVariable} is already filled.
     *
     * @param agent           owning AI agent (the flow must belong to it —
     *                        cross-agent pins return {@link PinResult#error}).
     * @param conversationId  active conversation id ({@code TUR_SESSION}).
     * @param flowSelector    either the flow UUID or its (case-insensitive)
     *                        name — names are convenient in deep links.
     *
     * @since 2026.3.1
     */
    @Transactional
    public PinResult pinFlowForConversation(TurAIAgent agent, String conversationId,
            String flowSelector) {
        if (agent == null) {
            return PinResult.error("agent is required");
        }
        if (conversationId == null || conversationId.isBlank()) {
            return PinResult.error("conversationId is required");
        }
        if (flowSelector == null || flowSelector.isBlank()) {
            return PinResult.error("flowSelector is required");
        }
        TurChatFlow flow = resolveFlowOnAgent(agent.getId(), flowSelector);
        if (flow == null) {
            return PinResult.error("flow '" + flowSelector + "' not found on agent "
                    + agent.getId());
        }
        if (flow.getEnabled() != 1) {
            return PinResult.error("flow '" + flow.getId() + "' is disabled");
        }
        Optional<ChatFlowGraph> graphOpt = parseGraph(flow);
        if (graphOpt.isEmpty()) {
            return PinResult.error("flow '" + flow.getId() + "' has no parseable graph");
        }
        ChatFlowGraph graph = graphOpt.get();
        Optional<ChatFlowNode> startNode = graph.startNode();
        if (startNode.isEmpty()) {
            return PinResult.error("flow '" + flow.getId() + "' has no start node");
        }

        // Snapshot slots BEFORE the reset so the receiving flow can resume
        // mid-conversation without losing data the visitor already typed.
        Map<String, String> currentSlots = new LinkedHashMap<>(
                listSlotsForConversation(conversationId).slots());

        // T93: when the receiving flow declares a slotInheritanceJson mapping,
        // use ONLY the mapped slots (so the flow author controls what carries
        // over — e.g. inherit `name` but not `last_topic_searched`). When no
        // mapping is set, fall back to the legacy pin behaviour of seeding
        // every slot — the operator explicitly chose this flow, so bringing
        // along the full session context is the least-surprising default.
        Map<String, String> mappedSlots = applySlotInheritance(flow, currentSlots);
        Map<String, String> seedSlots = mappedSlots != null ? mappedSlots : currentSlots;

        // Reset every other in-progress state on this agent so the engine's
        // continuation rule cannot keep the next turn on a stale flow. We
        // delete by agent (not just by flow) to guarantee a single non-
        // terminal leaf remains after this method returns.
        int resetCount = resetAllStatesForAgent(conversationId, agent.getId());

        TurChatFlowState leaf = createInitialStateWithSeed(conversationId, flow, graph, seedSlots);

        log.info("[FlowEngine.pinFlow] conv='{}' agent='{}' selector='{}' → flow='{}' "
                + "(name='{}'), reset {} prior state(s), seeded {} slot(s)",
                conversationId, agent.getId(), flowSelector, flow.getId(), flow.getName(),
                resetCount, seedSlots.size());

        // Notify SSE subscribers: the pin reshapes the slot graph (rows
        // changed even if values didn't), so the slot inspector / live UI
        // should refresh on the next render without polling.
        slotEventBus.publish(conversationId,
                listSlotsForConversation(conversationId).slots());
        return PinResult.ok(flow);
    }

    /**
     * Builds and persists a fresh state for {@code flow} on
     * {@code conversationId} with {@code seedSlots} written into the variables
     * BEFORE the transparent walker runs. Seeded slots feed
     * {@code walkThroughSatisfiedQuestions}, so any aiQuestion whose
     * {@code outputVariable} is already in the seed map is skipped at init
     * time (same behaviour as a re-triggered ALWAYS flow).
     *
     * <p>Used by both the manual pin ({@link #pinFlowForConversation}) and
     * the auto-router fresh-route branch when the receiving flow declares an
     * inheritance mapping. Extracted as a separate helper so both paths share
     * the same anchor / walk / save sequence.
     *
     * @since 2026.3.1
     */
    private TurChatFlowState createInitialStateWithSeed(String conversationId, TurChatFlow flow,
            ChatFlowGraph graph, Map<String, String> seedSlots) {
        ChatFlowNode anchor = ChatFlowOps.firstInteractiveNode(graph,
                graph.startNode().orElseThrow(() ->
                        new IllegalStateException("graph for flow '" + flow.getId()
                                + "' has no start node")))
                .orElse(graph.startNode().get());
        TurChatFlowState created = new TurChatFlowState();
        created.setConversationId(conversationId);
        created.setFlow(flow);
        created.setCurrentNodeId(anchor.id());
        ChatFlowOps.writeVariables(created, seedSlots == null ? Map.of() : seedSlots);
        ChatFlowOps.walkThroughConditions(created, graph, null);
        TurChatFlowState saved = stateRepository.save(created);
        TurChatFlowState leaf = walkTransparentNodes(saved, null);
        if (leaf != saved) {
            stateRepository.save(leaf);
        }
        return leaf;
    }

    /**
     * T93 / §VII.11.c — projects {@code sourceSlots} through the receiving
     * flow's {@code slotInheritanceJson} mapping and returns the resulting
     * seed map. Returns {@code null} when the receiving flow declares no
     * mapping, signalling "no T93 contract here — caller decides the
     * fallback" (pin path defaults to full snapshot, auto-router path
     * defaults to empty).
     *
     * <p>Mapping shape: a JSON object {@code {receivingSlot: sourceSlot}}.
     * Use the same name on both sides for a straight pass-through; use
     * different names to rename ({@code {"position": "cargo_atual"}}).
     * Special key {@code "*"} (with any value) → copy every entry in the
     * source map verbatim, useful for "give me everything" without listing
     * each slot.
     *
     * <p>Slots whose source name is absent from {@code sourceSlots} are
     * silently dropped (no null/empty placeholders end up in the seed —
     * matches the satisfied-questions walker's "absent ≠ filled" rule).
     *
     * @since 2026.3.1
     */
    Map<String, String> applySlotInheritance(TurChatFlow receivingFlow,
            Map<String, String> sourceSlots) {
        if (receivingFlow == null) {
            return null;
        }
        String mappingJson = receivingFlow.getSlotInheritanceJson();
        if (mappingJson == null || mappingJson.isBlank()) {
            return null;
        }
        Map<String, String> mapping = parseSlotInheritanceMapping(receivingFlow, mappingJson);
        if (mapping.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> safeSource = sourceSlots == null ? Map.of() : sourceSlots;
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String receivingName = entry.getKey();
            String sourceName = entry.getValue();
            if (receivingName == null || receivingName.isBlank()) {
                continue;
            }
            if ("*".equals(receivingName)) {
                // Wildcard: copy every source slot verbatim. Names that
                // collide with explicit entries are overwritten in this
                // loop's iteration order — explicit entries win when they
                // come later (LinkedHashMap preserves insertion).
                result.putAll(safeSource);
                continue;
            }
            if (sourceName == null || sourceName.isBlank()) {
                continue;
            }
            String value = safeSource.get(sourceName);
            if (value != null) {
                result.put(receivingName, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseSlotInheritanceMapping(TurChatFlow flow, String json) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(json, Object.class);
            if (!(parsed instanceof Map<?, ?> raw)) {
                log.warn("[FlowEngine] slotInheritanceJson on flow '{}' is not a JSON object — ignoring",
                        flow.getId());
                return Map.of();
            }
            Map<String, String> out = new LinkedHashMap<>(raw.size());
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() == null) continue;
                out.put(String.valueOf(e.getKey()),
                        e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
            return out;
        } catch (JacksonException e) {
            log.warn("[FlowEngine] slotInheritanceJson on flow '{}' is unparseable: {}",
                    flow.getId(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * Resolves {@code selector} against the agent's flows: UUID match first
     * (the common SDK path), case-insensitive name match second (deep-link
     * convenience, so {@code ?flow=in-company} works without exposing UUIDs
     * in the URL). Returns {@code null} when neither resolves.
     */
    private TurChatFlow resolveFlowOnAgent(String agentId, String selector) {
        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
        if (flows == null || flows.isEmpty()) {
            return null;
        }
        for (TurChatFlow flow : flows) {
            if (selector.equals(flow.getId())) {
                return flow;
            }
        }
        for (TurChatFlow flow : flows) {
            if (flow.getName() != null && selector.equalsIgnoreCase(flow.getName())) {
                return flow;
            }
        }
        return null;
    }

    // ─────────────────────────── Auto-trigger router ───────────────────────────

    /**
     * Decides which flow (if any) should govern the current chat turn for
     * an agent. Resolution order:
     *
     * <ol>
     *   <li>If any flow already has an in-progress state for this
     *       conversation (current node is not terminal), continue with it.</li>
     *   <li>Otherwise, list flows that:
     *     <ul>
     *       <li>belong to this agent and are enabled,</li>
     *       <li>have a non-blank {@code triggerDescription},</li>
     *       <li>have a parseable graph,</li>
     *       <li>are eligible: {@code ALWAYS} flows always are; {@code ONCE}
     *           flows only when no completed state exists for the
     *           conversation.</li>
     *     </ul></li>
     *   <li>Ask the LLM router to pick a flow id from the candidates given
     *       the user's last message; on "none" or unknown id, return empty.</li>
     *   <li>For an {@code ALWAYS} flow that already has a completed state,
     *       drop the previous state so the flow restarts at its first
     *       interactive node.</li>
     * </ol>
     */
    public Optional<FlowSelection> selectActiveFlow(TurAIAgent agent,
            String conversationId,
            String lastUserMessage,
            ChatModel routerModel) {
        return selectActiveFlow(agent, conversationId, lastUserMessage, routerModel, null);
    }

    /**
     * Overload accepting a forced A/B variant label (T73 / §VII.8.d).
     *
     * <p>When {@code forcedVariant} is non-blank and the router lands on a
     * flow that belongs to an experiment, the matching arm (by
     * {@code variantLabel}, case-insensitive) is hard-pinned — bypassing the
     * deterministic hash, Thompson sampling, and the scheduled experiment
     * window — so a QA reviewer or a sales engineer can preview a specific
     * (even not-yet-live or already-retired) variant via
     * {@code ?_ab_variant=<label>}. A blank value, a non-matching label, or a
     * router pick outside any experiment all degrade gracefully to the normal
     * sticky assignment. Forcing does <em>not</em> override an already
     * in-progress flow (the continuation path wins) — preview a variant from a
     * fresh {@code conversationId}.
     *
     * @since 2026.3.1
     */
    public Optional<FlowSelection> selectActiveFlow(TurAIAgent agent,
            String conversationId,
            String lastUserMessage,
            ChatModel routerModel,
            String forcedVariant) {
        if (agent == null || conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }

        List<TurChatFlowState> existingStates = stateRepository
                .findByConversationIdAndFlow_TurAIAgent_Id(conversationId, agent.getId());

        // 1) Continuation. Any non-terminal LEAF state for this conversation
        // wins — the user is in the middle of a flow and we must keep them
        // there until they reach an `end` node (or hit the abandon path).
        // With Sub Flow nodes a conversation may have multiple state rows in
        // a parent→child chain; the leaf (the row no other row points at as
        // a parent) is the one currently driving the chat.
        Set<String> hasDescendant = existingStates.stream()
                .map(TurChatFlowState::getParentStateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (TurChatFlowState state : existingStates) {
            if (hasDescendant.contains(state.getId())) {
                continue;
            }
            Optional<ChatFlowGraph> graphOpt = parseGraph(state.getFlow());
            if (graphOpt.isEmpty()) {
                continue;
            }
            Optional<ChatFlowNode> currentOpt = graphOpt.get().nodeById(state.getCurrentNodeId());
            if (currentOpt.isEmpty() || "end".equals(currentOpt.get().type())) {
                continue;
            }
            log.info("[FlowEngine] Continuation: flow '{}' is in progress for conversation '{}'",
                    state.getFlow().getId(), conversationId);
            // Continuation: user msg should be processed as input to the
            // current node by the LlmJudge advance pass.
            return Optional.of(new FlowSelection(state.getFlow(), graphOpt.get(), state, false));
        }

        // 2) Build candidate list. A flow counts as "completed" only when its
        // ROOT row has reached an end node — child sub-flow rows are popped
        // (deleted) on completion, so any lingering child end node is a stale
        // artifact and should not gate the parent flow's eligibility.
        Set<String> completedFlowIds = new HashSet<>();
        for (TurChatFlowState state : existingStates) {
            if (state.getParentStateId() != null) {
                continue;
            }
            Optional<ChatFlowGraph> graphOpt = parseGraph(state.getFlow());
            graphOpt.flatMap(g -> g.nodeById(state.getCurrentNodeId()))
                    .filter(n -> "end".equals(n.type()))
                    .ifPresent(n -> completedFlowIds.add(state.getFlow().getId()));
        }

        List<RouterCandidate> candidates = new ArrayList<>();
        for (TurChatFlow flow : chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId())) {
            if (flow.getEnabled() != 1) {
                continue;
            }
            if (flow.getTriggerDescription() == null || flow.getTriggerDescription().isBlank()) {
                continue;
            }
            TurChatFlowTriggerMode mode = flow.getTriggerMode() != null
                    ? flow.getTriggerMode()
                    : TurChatFlowTriggerMode.ONCE;
            if (mode == TurChatFlowTriggerMode.ONCE && completedFlowIds.contains(flow.getId())) {
                continue;
            }
            Optional<ChatFlowGraph> graphOpt = parseGraph(flow);
            if (graphOpt.isEmpty()) {
                continue;
            }
            candidates.add(new RouterCandidate(flow, graphOpt.get()));
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // 3a) Procedural pre-route — fast, deterministic, no LLM. Requires
        //     ≥2 candidates AND a user message with at least one
        //     PROCEDURAL_MIN_TOKEN_LENGTH-char content token (procedural
        //     routing is a disambiguation fast-path: a single candidate
        //     has nothing to disambiguate, and a bare 2-char greeting
        //     ("oi", "ok") cannot be reliably distinguished from a
        //     legitimate single-rare-term hit via BM25 alone — we defer
        //     to the LLM router instead). When the top flow dominates
        //     the runner-up by 1.5×, commit straight to it. Cuts ~1-3s
        //     of LLM latency from freshly-triggered turns where the
        //     trigger is obvious. Falls back to the LLM router otherwise.
        Optional<ProceduralOutcome> proceduralOutcome =
                proceduralRouteExplained(agent.getId(), candidates, lastUserMessage);
        final String pickedId;
        final TurChatFlowRouterMethod routerMethod;
        if (proceduralOutcome.map(ProceduralOutcome::decided).orElse(false)) {
            pickedId = proceduralOutcome.get().pickedId();
            routerMethod = TurChatFlowRouterMethod.PROCEDURAL;
            log.info("[FlowEngine] Procedural router picked '{}' (LLM skipped)", pickedId);
        } else {
            // 3b) Cached LLM router. Same {agentId, userMessage} pair returns
            //     a cached decision on repeated calls (e.g. canned UI prompts,
            //     test runs, retries) so the LLM round-trip pays off once per
            //     unique message per agent. Cache is wiped on any
            //     TurChatFlow.save/delete so admin edits propagate.
            RouterCacheResult llm = askRouterCached(agent.getId(), routerModel, candidates,
                    lastUserMessage);
            pickedId = llm.pickedId();
            routerMethod = pickedId == null
                    ? TurChatFlowRouterMethod.NONE
                    : (llm.cacheHit() ? TurChatFlowRouterMethod.LLM_CACHE
                            : TurChatFlowRouterMethod.LLM);
        }
        // T89 / §VII.10.f — structured router-decision log (candidate flows +
        // scores + winner + method). Best-effort; never breaks the turn.
        recordRouterDecision(agent, conversationId, lastUserMessage, candidates,
                pickedId, routerMethod, proceduralOutcome.orElse(null));
        if (pickedId == null) {
            return Optional.empty();
        }
        Optional<RouterCandidate> picked = candidates.stream()
                .filter(c -> pickedId.equals(c.flow().getId()))
                .findFirst();
        if (picked.isEmpty()) {
            log.warn("[FlowEngine] Router returned unknown flow id '{}' — ignoring", pickedId);
            return Optional.empty();
        }

        // 4) A/B experiment routing: when the router-picked flow has an
        //    experimentKey, deterministically reassign to a variant within
        //    the same experiment group. The assignment is sticky per
        //    conversation (same hash bucket regardless of which variant
        //    the router happened to land on first) so a user never flips
        //    variants mid-experiment.
        AbResolved ab = resolveAbVariant(picked.get(), candidates, conversationId, forcedVariant);
        final TurChatFlow flow = ab.flow();
        final ChatFlowGraph pickedGraph = ab.graph();

        if (completedFlowIds.contains(flow.getId())
                && flow.getTriggerMode() == TurChatFlowTriggerMode.ALWAYS) {
            stateRepository.findByConversationIdAndFlow_Id(conversationId, flow.getId())
                    .ifPresent(prior -> resetForAlwaysRetrigger(prior, pickedGraph));
            stateRepository.flush();
        }

        // T93 / §VII.11.c — cross-flow slot inheritance. When the receiving
        // flow declares a slotInheritanceJson mapping AND we're about to mint
        // a brand-new state (no existing row for this flow on this
        // conversation), seed the new state with the mapped slots BEFORE the
        // transparent walker runs so satisfied questions auto-skip. ALWAYS
        // retrigger keeps its dedicated path (resetForAlwaysRetrigger
        // preserves the flow's own previous variables in-place), so we only
        // apply inheritance when there is no existing row.
        boolean noExistingRow = stateRepository
                .findByConversationIdAndFlow_Id(conversationId, flow.getId()).isEmpty();
        Map<String, String> inherited = noExistingRow
                ? applySlotInheritance(flow, listSlotsForConversation(conversationId).slots())
                : null;
        Optional<TurChatFlowState> newState;
        if (inherited != null && !inherited.isEmpty()) {
            log.info("[FlowEngine] T93: inheriting {} slot(s) into fresh state for flow '{}'",
                    inherited.size(), flow.getId());
            newState = Optional.of(createInitialStateWithSeed(conversationId, flow, pickedGraph,
                    inherited));
        } else {
            newState = loadOrInitState(conversationId, flow, pickedGraph);
        }
        // The leaf may belong to a sub-flow rather than the picked root flow.
        // Align the FlowSelection with the leaf so the prompt addendum and
        // strategy run against the correct graph.
        return newState.map(s -> {
            TurChatFlow activeFlow = s.getFlow();
            ChatFlowGraph activeGraph = activeFlow.getId().equals(flow.getId())
                    ? pickedGraph
                    : parseGraph(activeFlow).orElse(pickedGraph);
            // Fresh trigger: user msg IS the trigger signal. The executor
            // skips the LlmJudge advance and just regens the first node's
            // question for this turn — otherwise the trigger text gets
            // force-captured as the first slot value.
            return new FlowSelection(activeFlow, activeGraph, s, true);
        });
    }

    /**
     * Resolves the router-picked candidate against any A/B experiment it
     * belongs to: if the candidate has a non-blank {@code experimentKey},
     * collects sibling candidates under the same key and reassigns to the
     * deterministically-chosen variant. Also fires the analytics tag in
     * the same step so the in-flight record knows the arm before the
     * first turn is recorded.
     *
     * <p>Returns the original candidate (flow + graph) when the experiment
     * group has fewer than two variants, when the chosen variant has an
     * unparseable graph (defensive fallback), or when no experimentKey is
     * configured.
     *
     * <p>Extracted as a separate helper so {@link #selectActiveFlow} keeps
     * the inner block flat enough for the final-capture lambdas downstream
     * to compile.
     */
    private AbResolved resolveAbVariant(RouterCandidate picked,
            List<RouterCandidate> candidates, String conversationId, String forcedVariant) {
        TurChatFlow routerFlow = picked.flow();
        ChatFlowGraph routerGraph = picked.graph();
        String experimentKey = routerFlow.getExperimentKey();
        if (experimentKey == null || experimentKey.isBlank()) {
            return new AbResolved(routerFlow, routerGraph);
        }

        // T73 / §VII.8.d — forced variant for QA + sales demo. When the caller
        // supplied `?_ab_variant=<label>` and the router landed inside this
        // experiment, hard-pin the arm whose variantLabel matches
        // (case-insensitive), bypassing the deterministic hash, Thompson
        // sampling, AND the scheduled experiment window (so a reviewer can
        // preview a not-yet-live or already-retired variant). The group is
        // collected WITHOUT the window filter for exactly that reason. A
        // non-matching label or an unparseable variant graph degrades to the
        // normal assignment below so a stale demo link never blanks the chat.
        if (forcedVariant != null && !forcedVariant.isBlank()) {
            List<TurChatFlow> group = candidates.stream()
                    .map(RouterCandidate::flow)
                    .filter(f -> experimentKey.equals(f.getExperimentKey()))
                    .toList();
            TurChatFlow forced = forceVariant(group, forcedVariant);
            if (forced != null) {
                Optional<ChatFlowGraph> forcedGraph = forced.getId().equals(routerFlow.getId())
                        ? Optional.of(routerGraph)
                        : parseGraph(forced);
                if (forcedGraph.isPresent()) {
                    log.info("[A/B] Experiment '{}': FORCED variant '{}' (flow {}) via _ab_variant "
                            + "— bypassing hash/bandit + schedule window",
                            experimentKey, forced.getVariantLabel(), forced.getId());
                    chatAnalyticsService.recordExperimentAssignment(conversationId,
                            experimentKey, forced.getVariantLabel());
                    return new AbResolved(forced, forcedGraph.get());
                }
                log.warn("[A/B] Experiment '{}': forced variant '{}' has an unparseable graph "
                        + "— falling back to normal assignment", experimentKey, forcedVariant);
            } else {
                log.warn("[A/B] Experiment '{}': forced variant label '{}' not found among {} "
                        + "arm(s) — falling back to normal assignment",
                        experimentKey, forcedVariant, group.size());
            }
        }

        java.time.Instant now = java.time.Instant.now();
        List<TurChatFlow> variants = candidates.stream()
                .map(RouterCandidate::flow)
                .filter(f -> experimentKey.equals(f.getExperimentKey()))
                .filter(f -> isInExperimentWindow(f, now))
                .toList();
        if (variants.isEmpty()) {
            // Every variant is outside its scheduled window — fall back to the
            // router pick and skip A/B tagging entirely so the conversation
            // looks like a non-experiment session in analytics.
            log.info("[A/B] Experiment '{}': no variants in active window — "
                    + "falling back to router pick {}", experimentKey, routerFlow.getId());
            return new AbResolved(routerFlow, routerGraph);
        }
        TurChatFlow finalFlow = routerFlow;
        ChatFlowGraph finalGraph = routerGraph;
        if (variants.size() > 1 || !variants.get(0).getId().equals(routerFlow.getId())) {
            // T70: if any variant has banditEnabled, route via Thompson
            // sampling on the observed conversion data instead of the
            // deterministic fixed-weight hash.
            TurChatFlow assigned = com.viglet.turing.service.chatanalytics.TurAbBanditService
                            .isBanditExperiment(variants)
                    ? abBanditService.pickVariant(variants, experimentKey)
                    : assignVariant(variants, conversationId, experimentKey);
            if (!assigned.getId().equals(routerFlow.getId())) {
                Optional<ChatFlowGraph> assignedGraph = parseGraph(assigned);
                if (assignedGraph.isPresent()) {
                    log.info("[A/B] Experiment '{}': router picked {} ({}) → reassigned to {} ({})",
                            experimentKey, routerFlow.getId(), routerFlow.getVariantLabel(),
                            assigned.getId(), assigned.getVariantLabel());
                    finalFlow = assigned;
                    finalGraph = assignedGraph.get();
                } else {
                    log.warn("[A/B] Experiment '{}': assigned variant {} has unparseable graph — "
                            + "staying on router pick {}", experimentKey,
                            assigned.getId(), routerFlow.getId());
                }
            }
        }
        chatAnalyticsService.recordExperimentAssignment(conversationId,
                experimentKey, finalFlow.getVariantLabel());
        return new AbResolved(finalFlow, finalGraph);
    }

    /**
     * Schedule-window check for a flow variant. Returns true when {@code now}
     * sits inside the optional {@code [experimentStartsAt, experimentEndsAt]}
     * window — null bounds mean "open on that side". Used to gate first-touch
     * A/B assignment so flow authors can stage future variants and let past
     * ones auto-retire without admin intervention.
     *
     * <p>Package-private for unit testing.
     */
    static boolean isInExperimentWindow(TurChatFlow flow, java.time.Instant now) {
        java.time.Instant start = flow.getExperimentStartsAt();
        java.time.Instant end = flow.getExperimentEndsAt();
        return (start == null || !now.isBefore(start))
                && (end == null || !now.isAfter(end));
    }

    private record AbResolved(TurChatFlow flow, ChatFlowGraph graph) {}

    /**
     * Deterministic weighted-random assignment of a conversation to one
     * variant within an A/B experiment. Same {@code conversationId +
     * experimentKey} always maps to the same variant, so the assignment
     * is sticky for the entire experiment lifetime — visitors never flip
     * variants mid-conversation, and re-triggers preserve the original
     * arm. Uses {@code Math.floorMod} on the hash so negative hashes
     * still land in {@code [0, totalWeight)}.
     *
     * <p>Weighting rules:
     * <ul>
     *   <li>Sum of {@code trafficWeight} &gt; 0 → variants are picked
     *       proportionally to their weight. {@code (1, 1)} ≡ {@code (50, 50)}
     *       — only ratios matter, no need to sum to 100.</li>
     *   <li>All weights null or 0 → uniform distribution. Lets authors
     *       opt out of the weighting math entirely when they just want
     *       an even split.</li>
     *   <li>A single variant with weight 0 inside an experiment of
     *       non-zero variants is excluded — useful to pause an arm
     *       without deleting it (analytics on past assignments stay
     *       readable).</li>
     * </ul>
     *
     * <p>Package-private for unit testing.
     *
     * @since 2026.2.7
     */
    /**
     * Resolves a <em>forced</em> A/B variant by label (T73 / §VII.8.d).
     * Returns the variant in {@code variants} whose {@code variantLabel}
     * equals {@code forcedLabel} ignoring case and surrounding whitespace, or
     * {@code null} when no arm matches (or either input is blank/empty). Used
     * by the {@code ?_ab_variant=<label>} QA / sales-demo override to hard-pin
     * a specific arm regardless of the deterministic hash, Thompson sampling,
     * or the scheduled experiment window.
     *
     * <p>Package-private + pure (no Spring, no DB) for unit testing.
     *
     * @since 2026.3.1
     */
    static TurChatFlow forceVariant(List<TurChatFlow> variants, String forcedLabel) {
        if (variants == null || variants.isEmpty() || forcedLabel == null || forcedLabel.isBlank()) {
            return null;
        }
        String target = forcedLabel.trim();
        for (TurChatFlow v : variants) {
            String label = v.getVariantLabel();
            if (label != null && label.trim().equalsIgnoreCase(target)) {
                return v;
            }
        }
        return null;
    }

    static TurChatFlow assignVariant(List<TurChatFlow> variants, String conversationId,
            String experimentKey) {
        int totalWeight = 0;
        for (TurChatFlow v : variants) {
            totalWeight += Math.max(0, v.getTrafficWeight() == null ? 0 : v.getTrafficWeight());
        }
        int hash = (conversationId + ":" + experimentKey).hashCode();
        if (totalWeight <= 0) {
            // Uniform fallback — every variant is equally likely.
            int idx = Math.floorMod(hash, variants.size());
            return variants.get(idx);
        }
        int bucket = Math.floorMod(hash, totalWeight);
        int cumulative = 0;
        for (TurChatFlow v : variants) {
            cumulative += Math.max(0, v.getTrafficWeight() == null ? 0 : v.getTrafficWeight());
            if (bucket < cumulative) return v;
        }
        // Defensive fallback — should never reach here when totalWeight > 0.
        return variants.get(variants.size() - 1);
    }

    /**
     * Soft-reset for an {@code ALWAYS}-trigger flow whose state has reached an
     * {@code end} node. Repositions the cursor on the first interactive node
     * and clears {@code endNodeId} so the row is treated as in-progress again,
     * but <em>preserves</em> {@code variablesJson} so slot values captured on
     * the previous run carry forward. {@link ChatFlowOps#walkThroughSatisfiedQuestions}
     * is then invoked to fast-forward past every aiQuestion whose slot is
     * already filled (and any chained condition / switch / slot / sub-flow
     * via {@link #walkTransparentNodes}). On a graph with no start node — a
     * pathological edit-state — the row is deleted as a defensive fallback
     * so {@link #loadOrInitState} can rebuild from scratch.
     *
     * @since 2026.2.7
     */
    private void resetForAlwaysRetrigger(TurChatFlowState prior, ChatFlowGraph graph) {
        Optional<ChatFlowNode> startNode = graph.startNode();
        if (startNode.isEmpty()) {
            stateRepository.delete(prior);
            return;
        }
        ChatFlowNode anchor = ChatFlowOps.firstInteractiveNode(graph, startNode.get())
                .orElse(startNode.get());
        prior.setCurrentNodeId(anchor.id());
        // Variables are NOT cleared on purpose — captured slot values are
        // conversation-scoped, so a re-triggered flow can skip aiQuestion
        // nodes whose outputVariable was already filled on a previous run.
        TurChatFlowState saved = stateRepository.save(prior);
        // Walk transparent nodes (conditions, switches, slot ops, satisfied
        // aiQuestions, sub-flow descent) so the next user turn lands on the
        // first node that genuinely needs the user. {@code null} aux model is
        // fine — every walker tolerates it (slot/persona/satisfied-questions
        // are pure data, condition/switch fall back to wildcard without LLM).
        // walkTransparentNodes mutates state.currentNodeId in-memory for the
        // satisfied-questions case (no implicit save), so we save once more
        // here to guarantee the cursor lands on the right node when
        // loadOrInitState re-queries it on the next line of selectActiveFlow.
        TurChatFlowState walked = walkTransparentNodes(saved, null);
        stateRepository.save(walked);
    }

    record RouterCandidate(TurChatFlow flow, ChatFlowGraph graph) {
    }

    /**
     * Procedural pre-route — Lucene {@link MoreLikeThis} score between the
     * user message and each candidate's {@code triggerDescription}. Returns
     * the picked flow id when one candidate dominates clearly
     * (≥{@link #PROCEDURAL_DOMINANCE_RATIO}× the next best); otherwise
     * {@link Optional#empty()} so the caller can fall back to the LLM router.
     *
     * <p>T25 widened the original single Portuguese analyzer into per-flow
     * language routing. T26 keeps that behavior by grouping candidates by the
     * analyzer selected from each flow's {@code triggerLanguage}; each group
     * gets a tiny in-memory Lucene index and an MLT query derived from the user
     * message.
     *
     * @since 2026.3.1 (MLT scoring; was overlap counter in 2026.2.8)
     */
    static Optional<String> tryProceduralRoute(List<RouterCandidate> candidates,
            String userMessage) {
        if (userMessage == null || userMessage.isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }
        // Procedural routing is a disambiguation fast-path: with a single
        // candidate there is nothing to disambiguate. Defer to the LLM
        // router, which understands negative-list semantics inside trigger
        // descriptions (e.g. "NÃO ATIVAR para saudações: 'oi', 'olá'...")
        // — keyword scoring cannot.
        if (candidates.size() < 2) {
            return Optional.empty();
        }
        // Query-side quality guard: require at least one substantive token
        // (≥ PROCEDURAL_MIN_TOKEN_LENGTH chars) in the user message. Filters
        // bare 2-char greetings ("oi", "ok", "vc") that would otherwise
        // land on quoted negative examples in trigger descriptions and
        // score the same as legitimate rare-term matches.
        if (!hasSubstantiveToken(userMessage)) {
            return Optional.empty();
        }
        Map<Analyzer, List<RouterCandidate>> byAnalyzer = new HashMap<>(2);
        for (RouterCandidate c : candidates) {
            String description = c.flow().getTriggerDescription();
            if (description != null && !description.isBlank()) {
                Analyzer analyzer = analyzerFor(c.flow().getTriggerLanguage(), description);
                byAnalyzer.computeIfAbsent(analyzer, ignored -> new ArrayList<>()).add(c);
            }
        }
        if (byAnalyzer.isEmpty()) {
            return Optional.empty();
        }
        float bestScore = 0f;
        float secondScore = 0f;
        String bestId = null;
        for (Map.Entry<Analyzer, List<RouterCandidate>> bucket : byAnalyzer.entrySet()) {
            ScoredRoute scoredRoute = moreLikeThisRoute(bucket.getKey(), bucket.getValue(),
                    userMessage);
            if (scoredRoute == null) {
                continue;
            }
            if (scoredRoute.bestScore() > bestScore) {
                if (bestScore > secondScore) {
                    secondScore = bestScore;
                }
                bestScore = scoredRoute.bestScore();
                bestId = scoredRoute.bestId();
            } else if (scoredRoute.bestScore() > secondScore) {
                secondScore = scoredRoute.bestScore();
            }
            if (scoredRoute.secondScore() > secondScore) {
                secondScore = scoredRoute.secondScore();
            }
        }
        if (bestId == null || bestScore <= 0f) {
            return Optional.empty();
        }
        if (secondScore > 0f
                && bestScore < secondScore * PROCEDURAL_DOMINANCE_RATIO) {
            log.debug("[FlowEngine] Procedural router undecided "
                    + "(best={}, second={}, ratio<{}) — falling back to LLM",
                    bestScore, secondScore, PROCEDURAL_DOMINANCE_RATIO);
            return Optional.empty();
        }
        return Optional.ofNullable(bestId);
    }

    private record ScoredRoute(String bestId, float bestScore, float secondScore) {
    }

    /**
     * T89 / §VII.10.f — explained result of the keyword (procedural) routing
     * pass: the would-be pick, whether the pass was decisive, the best /
     * second scores, the dominance ratio, and the full per-candidate score
     * map (flowId → MoreLikeThis score). {@code decided} mirrors the gate the
     * legacy {@code Optional<String>} overload applied — when {@code false}
     * the engine falls through to the LLM router but still records these
     * scores so a complaint triage can see how close the race was.
     */
    record ProceduralOutcome(boolean decided, String pickedId, float bestScore,
            float secondScore, float dominanceRatio, Map<String, Float> scores) {
    }

    /**
     * Returns {@code true} when {@code userMessage} contains at least one
     * whitespace-separated token of length ≥ {@link #PROCEDURAL_MIN_TOKEN_LENGTH}
     * after stripping leading/trailing non-letter characters. The strip
     * handles trailing punctuation ({@code "oi!"}, {@code "(oi)"}) so
     * decoration around a short token doesn't leak it past the guard.
     * Letter detection uses Unicode categories so PT accents and CJK
     * characters count.
     *
     * @since 2026.3.1
     */
    static boolean hasSubstantiveToken(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        for (String raw : userMessage.split("\\s+")) {
            String trimmed = raw.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "");
            if (trimmed.length() >= PROCEDURAL_MIN_TOKEN_LENGTH) {
                return true;
            }
        }
        return false;
    }

    /**
     * T27 / §II.2.3 — cached variant of
     * {@link #tryProceduralRoute(List, String)} that reuses a pre-built
     * {@link SearcherManager}-per-analyzer index keyed by {@code agentId}.
     *
     * <p>The index covers every enabled flow on the agent (regardless of
     * per-conversation eligibility); routing applies the eligibility mask
     * after MLT scoring so the cache survives across conversations. Built
     * lazily on first call; reused on subsequent turns; wiped via
     * {@link #evictRouterIndexes()} when any flow on any agent is saved or
     * deleted.
     *
     * <p>The static overload (used by unit tests and as the on-demand
     * fallback) still works — this method delegates to it whenever the
     * cached index is empty or unavailable for the agent.
     *
     * @since 2026.3.1
     */
    Optional<String> tryProceduralRoute(String agentId, List<RouterCandidate> candidates,
            String userMessage) {
        return proceduralRouteExplained(agentId, candidates, userMessage)
                .filter(ProceduralOutcome::decided)
                .map(ProceduralOutcome::pickedId);
    }

    /**
     * T89 / §VII.10.f — score-surfacing variant of
     * {@link #tryProceduralRoute(String, List, String)}. Runs the same
     * keyword pass but returns the full per-candidate score map and the
     * best/second/ratio numbers — even when the pass is too ambiguous to
     * commit ({@link ProceduralOutcome#decided()} is {@code false}). The flow
     * engine records this on the router-decision log so an operator can see
     * how close the keyword race was, including when the decision fell
     * through to the LLM router.
     *
     * <p>Returns {@link Optional#empty()} only when the keyword pass did not
     * run at all (blank message, fewer than two candidates, no substantive
     * token, no agent index) or produced no scores — never wraps a
     * "ran but scored nothing" result.
     *
     * @since 2026.3.1
     */
    Optional<ProceduralOutcome> proceduralRouteExplained(String agentId,
            List<RouterCandidate> candidates, String userMessage) {
        if (userMessage == null || userMessage.isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }
        // Same single-candidate skip as the static overload — see the
        // disambiguation rationale there.
        if (candidates.size() < 2) {
            return Optional.empty();
        }
        // Same query-side quality guard as the static overload.
        if (!hasSubstantiveToken(userMessage)) {
            return Optional.empty();
        }
        if (agentId == null || agentId.isBlank()) {
            // No agent id → on-demand static path, which only surfaces the
            // pick (no per-candidate scores). Wrap it as a decided outcome
            // with an empty score map so callers keep the same shape.
            return tryProceduralRoute(candidates, userMessage)
                    .map(id -> new ProceduralOutcome(true, id, 0f, 0f,
                            Float.POSITIVE_INFINITY, Map.of()));
        }
        AgentRouterIndex index = routerIndexByAgent.computeIfAbsent(agentId,
                this::buildRouterIndex);
        if (index.isEmpty()) {
            return Optional.empty();
        }
        return scoreCachedRouteExplained(index, candidates, userMessage);
    }

    /**
     * Drops every cached {@link AgentRouterIndex}, releasing the underlying
     * Lucene {@link Directory}/{@link SearcherManager} resources. Invoked by
     * {@link TurChatFlowRouterEvictionListener} on
     * {@link TurChatFlow} persist/update/remove and by the API layer at the
     * one bulk-DML delete site that bypasses JPA entity callbacks.
     *
     * @since 2026.3.1
     */
    public void evictRouterIndexes() {
        if (routerIndexByAgent.isEmpty()) {
            return;
        }
        List<AgentRouterIndex> drained = new ArrayList<>(routerIndexByAgent.values());
        routerIndexByAgent.clear();
        for (AgentRouterIndex index : drained) {
            index.close();
        }
        log.debug("[FlowEngine] Router index cache evicted ({} agent entries)",
                drained.size());
    }

    /**
     * Test-visible accessor for the per-agent index cache size. Production
     * code never reads this — it exists so unit tests can pin the hit/miss
     * contract without reflection.
     */
    int routerIndexCacheSize() {
        return routerIndexByAgent.size();
    }

    private AgentRouterIndex buildRouterIndex(String agentId) {
        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
        Map<Analyzer, List<TurChatFlow>> grouped = new HashMap<>(2);
        for (TurChatFlow flow : flows) {
            if (flow.getEnabled() != 1) {
                continue;
            }
            String description = flow.getTriggerDescription();
            if (description == null || description.isBlank()) {
                continue;
            }
            Analyzer analyzer = analyzerFor(flow.getTriggerLanguage(), description);
            grouped.computeIfAbsent(analyzer, ignored -> new ArrayList<>()).add(flow);
        }
        if (grouped.isEmpty()) {
            return AgentRouterIndex.empty();
        }
        Map<Analyzer, AnalyzerBucket> buckets = new HashMap<>(grouped.size());
        for (Map.Entry<Analyzer, List<TurChatFlow>> entry : grouped.entrySet()) {
            AnalyzerBucket bucket = buildBucket(entry.getKey(), entry.getValue());
            if (bucket != null) {
                buckets.put(entry.getKey(), bucket);
            }
        }
        if (buckets.isEmpty()) {
            return AgentRouterIndex.empty();
        }
        return new AgentRouterIndex(Map.copyOf(buckets));
    }

    private static AnalyzerBucket buildBucket(Analyzer analyzer, List<TurChatFlow> flows) {
        Directory directory = new ByteBuffersDirectory();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer)
                .setSimilarity(new BM25Similarity());
        int docs = 0;
        try (IndexWriter writer = new IndexWriter(directory, cfg)) {
            for (TurChatFlow flow : flows) {
                Document doc = new Document();
                doc.add(new StringField(MLT_FIELD_ID, flow.getId(), Field.Store.YES));
                doc.add(new TextField(MLT_FIELD_TRIGGER, flow.getTriggerDescription(),
                        Field.Store.NO));
                writer.addDocument(doc);
                docs++;
            }
            writer.commit();
        } catch (IOException e) {
            log.warn("[FlowEngine] Failed to build router index bucket: {}", e.getMessage());
            try {
                directory.close();
            } catch (IOException closeException) {
                log.debug("[FlowEngine] Failed to close directory after build error: {}",
                        closeException.getMessage());
            }
            return null;
        }
        try {
            SearcherManager searcherManager = new SearcherManager(directory, null);
            return new AnalyzerBucket(directory, searcherManager, analyzer, docs);
        } catch (IOException e) {
            log.warn("[FlowEngine] Failed to open SearcherManager: {}", e.getMessage());
            try {
                directory.close();
            } catch (IOException closeException) {
                log.debug("[FlowEngine] Failed to close directory after SM error: {}",
                        closeException.getMessage());
            }
            return null;
        }
    }

    private static Optional<ProceduralOutcome> scoreCachedRouteExplained(AgentRouterIndex index,
            List<RouterCandidate> candidates, String userMessage) {
        Set<String> eligibleIds = candidates.stream()
                .map(c -> c.flow().getId())
                .collect(Collectors.toSet());
        Map<String, Float> scores = new HashMap<>();
        for (AnalyzerBucket bucket : index.buckets().values()) {
            collectBucketScores(bucket, eligibleIds, userMessage, scores);
        }
        return decide(scores);
    }

    /**
     * T89 — applies the dominance gate to a flowId→score map and packages the
     * outcome. Returns {@link Optional#empty()} only when nothing scored;
     * otherwise the outcome carries the best pick, the runner-up, the ratio,
     * and {@code decided=true} iff the best dominates the runner-up by
     * {@link #PROCEDURAL_DOMINANCE_RATIO}× (or there is no runner-up). Kept as
     * its own helper so the explained path and any future caller share the
     * exact gate the legacy {@code Optional<String>} routing used.
     */
    private static Optional<ProceduralOutcome> decide(Map<String, Float> scores) {
        String bestId = null;
        float bestScore = 0f;
        float secondScore = 0f;
        for (Map.Entry<String, Float> e : scores.entrySet()) {
            float s = e.getValue();
            if (s > bestScore) {
                if (bestScore > secondScore) {
                    secondScore = bestScore;
                }
                bestScore = s;
                bestId = e.getKey();
            } else if (s > secondScore) {
                secondScore = s;
            }
        }
        if (bestId == null || bestScore <= 0f) {
            return Optional.empty();
        }
        float ratio = secondScore > 0f ? bestScore / secondScore : Float.POSITIVE_INFINITY;
        boolean decided = !(secondScore > 0f
                && bestScore < secondScore * PROCEDURAL_DOMINANCE_RATIO);
        if (!decided) {
            log.debug("[FlowEngine] Cached router undecided "
                    + "(best={}, second={}, ratio<{}) — falling back to LLM",
                    bestScore, secondScore, PROCEDURAL_DOMINANCE_RATIO);
        }
        return Optional.of(new ProceduralOutcome(decided, bestId, bestScore, secondScore,
                ratio, Map.copyOf(scores)));
    }

    /**
     * Searches one analyzer bucket and merges every eligible flow's score
     * into {@code sink}. Unlike a top-2-only scan it keeps the full
     * distribution so {@link ProceduralOutcome} can report each candidate's
     * score (T89). Each flow lives in exactly one bucket, so the merge across
     * buckets never collides; the {@code max} guard is defensive.
     */
    private static void collectBucketScores(AnalyzerBucket bucket, Set<String> eligibleIds,
            String userMessage, Map<String, Float> sink) {
        IndexSearcher searcher = null;
        try {
            searcher = bucket.searcherManager().acquire();
            MoreLikeThis moreLikeThis = new MoreLikeThis(searcher.getIndexReader());
            moreLikeThis.setAnalyzer(bucket.analyzer());
            moreLikeThis.setFieldNames(new String[] { MLT_FIELD_TRIGGER });
            moreLikeThis.setMinTermFreq(1);
            moreLikeThis.setMinDocFreq(1);
            moreLikeThis.setMinWordLen(2);
            moreLikeThis.setMaxQueryTerms(25);
            Query query = moreLikeThis.like(MLT_FIELD_TRIGGER, new StringReader(userMessage));
            int topK = Math.max(2, bucket.docCount());
            TopDocs hits = searcher.search(query, topK);
            if (hits.scoreDocs.length == 0) {
                return;
            }
            StoredFields storedFields = searcher.storedFields();
            for (ScoreDoc sd : hits.scoreDocs) {
                String flowId = storedFields.document(sd.doc).get(MLT_FIELD_ID);
                if (flowId == null || !eligibleIds.contains(flowId)) {
                    continue;
                }
                sink.merge(flowId, sd.score, Math::max);
            }
        } catch (IOException e) {
            log.warn("[FlowEngine] Cached MoreLikeThis search failed: {}", e.getMessage());
        } finally {
            if (searcher != null) {
                try {
                    bucket.searcherManager().release(searcher);
                } catch (IOException e) {
                    log.debug("[FlowEngine] Failed to release IndexSearcher: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Pre-built per-analyzer Lucene index for one agent. Lives in the
     * {@code routerIndexByAgent} cache until {@link #evictRouterIndexes()}
     * fires; closing it releases the underlying {@link Directory} and
     * {@link SearcherManager}.
     */
    record AgentRouterIndex(Map<Analyzer, AnalyzerBucket> buckets) {

        private static final AgentRouterIndex EMPTY = new AgentRouterIndex(Map.of());

        static AgentRouterIndex empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return buckets.isEmpty();
        }

        void close() {
            for (AnalyzerBucket bucket : buckets.values()) {
                bucket.close();
            }
        }
    }

    /**
     * One bucket of the {@link AgentRouterIndex}: a Lucene directory + open
     * {@link SearcherManager} for a single {@link Analyzer} (PT or EN). The
     * bucket holds the document count so callers can size their {@code topK}
     * to cover eligibility filtering without truncating real matches.
     */
    record AnalyzerBucket(Directory directory, SearcherManager searcherManager,
            Analyzer analyzer, int docCount) {

        void close() {
            try {
                searcherManager.close();
            } catch (IOException e) {
                log.debug("[FlowEngine] SearcherManager close failed: {}", e.getMessage());
            }
            try {
                directory.close();
            } catch (IOException e) {
                log.debug("[FlowEngine] Directory close failed: {}", e.getMessage());
            }
        }
    }

    private static final String MLT_FIELD_TRIGGER = "trigger";
    private static final String MLT_FIELD_ID = "flowId";

    private static ScoredRoute moreLikeThisRoute(Analyzer analyzer,
            List<RouterCandidate> candidates, String userMessage) {
        try (Directory dir = new ByteBuffersDirectory();
                IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
            for (RouterCandidate candidate : candidates) {
                Document doc = new Document();
                doc.add(new StringField(MLT_FIELD_ID, candidate.flow().getId(), Field.Store.YES));
                doc.add(new TextField(MLT_FIELD_TRIGGER,
                        candidate.flow().getTriggerDescription(), Field.Store.NO));
                writer.addDocument(doc);
            }
            writer.commit();
            try (IndexReader reader = DirectoryReader.open(dir)) {
                MoreLikeThis moreLikeThis = new MoreLikeThis(reader);
                moreLikeThis.setAnalyzer(analyzer);
                moreLikeThis.setFieldNames(new String[] { MLT_FIELD_TRIGGER });
                moreLikeThis.setMinTermFreq(1);
                moreLikeThis.setMinDocFreq(1);
                moreLikeThis.setMinWordLen(2);
                moreLikeThis.setMaxQueryTerms(25);
                org.apache.lucene.search.Query query = moreLikeThis.like(MLT_FIELD_TRIGGER,
                        new java.io.StringReader(userMessage));
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs hits = searcher.search(query, 2);
                if (hits.scoreDocs.length == 0) {
                    return null;
                }
                StoredFields storedFields = searcher.storedFields();
                String bestId = storedFields.document(hits.scoreDocs[0].doc).get(MLT_FIELD_ID);
                float bestScore = hits.scoreDocs[0].score;
                float secondScore = hits.scoreDocs.length > 1 ? hits.scoreDocs[1].score : 0f;
                return new ScoredRoute(bestId, bestScore, secondScore);
            }
        } catch (IOException e) {
            log.warn("[FlowEngine] MoreLikeThis procedural route failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * T25 / §II.2.1 — picks the analyzer for a flow's
     * {@code triggerDescription} based on its declared
     * {@link TurChatFlowTriggerLanguage}. {@code AUTO} (default) routes
     * through {@link #detectLanguage(String)}; PT/EN are honored verbatim.
     * Null lang (pre-T25 entities from a snapshot, defensive) treated as AUTO.
     */
    static Analyzer analyzerFor(TurChatFlowTriggerLanguage lang, String description) {
        TurChatFlowTriggerLanguage effective = lang == null ? TurChatFlowTriggerLanguage.AUTO : lang;
        return switch (effective) {
            case PT -> ANALYZER_PT;
            case EN -> ANALYZER_EN;
            case AUTO -> detectLanguage(description) == TurChatFlowTriggerLanguage.EN
                    ? ANALYZER_EN : ANALYZER_PT;
        };
    }

    /**
     * T25 / §II.2.1 — lightweight EN vs PT heuristic for AUTO-tagged flows.
     * Counts EN hint stopwords vs PT hint stopwords in {@code text}; returns
     * {@link TurChatFlowTriggerLanguage#EN} when EN dominates strictly,
     * {@link TurChatFlowTriggerLanguage#PT} otherwise (PT is the platform's
     * primary language so ties + zero-signal default to it). Case- and
     * diacritic-insensitive via the lowercase + simple-letter regex split.
     *
     * <p>Cheap on purpose — designed to run on every routing pass without
     * needing Tika's model-loaded {@code LanguageDetector} or an external
     * service. For descriptions where the heuristic is wrong, admins can
     * pin {@code triggerLanguage = PT/EN} explicitly.
     *
     * @return {@code EN} when EN hint count strictly exceeds PT hint count;
     *         {@code PT} on tie or PT-leaning text or blank input
     * @since 2026.3.1
     */
    static TurChatFlowTriggerLanguage detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return TurChatFlowTriggerLanguage.PT;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        // Split on any non-letter (including digits + punctuation). Keep
        // accented chars so PT hints like "está" match without normalization.
        String[] words = lower.split("[^\\p{L}]+");
        int en = 0;
        int pt = 0;
        for (String w : words) {
            if (EN_HINT_WORDS.contains(w)) en++;
            else if (PT_HINT_WORDS.contains(w)) pt++;
        }
        return en > pt ? TurChatFlowTriggerLanguage.EN : TurChatFlowTriggerLanguage.PT;
    }

    /**
     * Tokenize {@code text} through the supplied Lucene {@link Analyzer}.
     * Applies that analyzer's pipeline (lowercase, stopword removal,
     * stemming) and returns the deduplicated set of stems. Empty for
     * blank/null input. The {@link IOException} declared by
     * {@code TokenStream} is impossible in practice with the in-memory
     * string source — the catch is for API compliance; we log and degrade
     * to an empty set so the caller falls back to the LLM router rather
     * than crashing.
     *
     * @since 2026.3.1 (took an analyzer parameter — was hard-coded to PT in 2026.2.8)
     */
    static Set<String> tokenize(String text, Analyzer analyzer) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        try (TokenStream stream = analyzer.tokenStream("text", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String token = term.toString();
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
            stream.end();
        } catch (IOException e) {
            log.warn("[FlowEngine] Lucene tokenization failed: {}", e.getMessage());
            return Set.of();
        }
        return tokens;
    }

    /**
     * LLM router with manual Spring cache lookup. Keyed by {@code agentId :::
     * sha256(normalized(userMessage))} — same agent + same wording (modulo
     * casing/whitespace) returns the cached decision without the 1-3s LLM
     * round-trip. {@code null} (router said "none") is also cached so
     * repeated "off-topic" messages don't burn LLM calls.
     *
     * <p>The cache is evicted on any {@link TurChatFlow} save/delete via the
     * {@code @CacheEvict} annotations on {@link TurChatFlowRepository}, so
     * admin edits to {@code triggerDescription} propagate without restart.
     *
     * @since 2026.2.8
     */
    private RouterCacheResult askRouterCached(String agentId, ChatModel routerModel,
            List<RouterCandidate> candidates, String userMessage) {
        Cache cache = cacheManager.getCache(CACHE_ROUTER_DECISION);
        String key = buildRouterCacheKey(agentId, userMessage);
        if (cache != null && key != null) {
            Cache.ValueWrapper hit = cache.get(key);
            if (hit != null) {
                Object cached = hit.get();
                log.info("[FlowEngine] Router cache HIT for agent='{}' key='{}' → '{}' (LLM skipped)",
                        agentId, key, cached);
                return new RouterCacheResult((String) cached, true);
            }
            log.info("[FlowEngine] Router cache MISS for agent='{}' key='{}' — calling LLM",
                    agentId, key);
        }
        String picked = askRouter(routerModel, candidates, userMessage);
        if (cache != null && key != null) {
            cache.put(key, picked);
        }
        return new RouterCacheResult(picked, false);
    }

    /**
     * Outcome of {@link #askRouterCached}: the picked flow id (or {@code null}
     * for "none") plus whether it was served from the
     * {@code turChatFlowRouterDecision} cache rather than a fresh LLM call —
     * lets the router-decision log (T89) distinguish an {@code LLM} pick from
     * an {@code LLM_CACHE} replay.
     */
    private record RouterCacheResult(String pickedId, boolean cacheHit) {
    }

    /**
     * T89 / §VII.10.f — builds and records a structured router decision on the
     * {@link TurChatFlowRouterDecisionLog}. No-op when no sink is registered
     * (unit tests without a Spring context) so the hot path skips the record
     * construction entirely. The {@code proc} outcome (when present) supplies
     * the per-candidate keyword scores and the best/second/ratio numbers —
     * including for an LLM pick where the keyword pass ran but was too
     * ambiguous to commit, so a complaint triage can still see how close the
     * race was.
     */
    private void recordRouterDecision(TurAIAgent agent, String conversationId, String userMessage,
            List<RouterCandidate> candidates, String pickedId, TurChatFlowRouterMethod method,
            ProceduralOutcome proc) {
        if (TurChatFlowRouterDecisionLog.getInstance() == null) {
            return;
        }
        Map<String, Float> scores = proc != null ? proc.scores() : Map.of();
        List<TurChatFlowRouterDecision.Candidate> cands = new ArrayList<>(candidates.size());
        String winnerName = null;
        for (RouterCandidate c : candidates) {
            String id = c.flow().getId();
            boolean winner = id.equals(pickedId);
            if (winner) {
                winnerName = c.flow().getName();
            }
            cands.add(new TurChatFlowRouterDecision.Candidate(id, c.flow().getName(),
                    scores.get(id), winner));
        }
        Float bestScore = null;
        Float secondScore = null;
        Float ratio = null;
        if (proc != null && !proc.scores().isEmpty()) {
            bestScore = proc.bestScore();
            secondScore = proc.secondScore();
            ratio = proc.dominanceRatio();
        }
        TurChatFlowRouterDecision decision = new TurChatFlowRouterDecision(
                System.currentTimeMillis(), agent.getId(), conversationId, userMessage,
                method, pickedId, winnerName, cands,
                bestScore, secondScore, ratio, bestScore != null, null);
        TurChatFlowRouterDecisionLog.recordSafely(decision);
    }

    /**
     * Deterministic cache key — agent id plus a SHA-256 of the lowercased,
     * whitespace-collapsed user message. Returns {@code null} when inputs
     * are blank so the caller skips caching gracefully.
     */
    private static String buildRouterCacheKey(String agentId, String userMessage) {
        if (agentId == null || agentId.isBlank()
                || userMessage == null || userMessage.isBlank()) {
            return null;
        }
        String normalized = userMessage.toLowerCase().trim().replaceAll("\\s+", " ");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return agentId + ":::" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JRE — fall back to plain key
            return agentId + ":::" + normalized;
        }
    }

    private String askRouter(ChatModel routerModel,
            List<RouterCandidate> candidates,
            String userMessage) {
        if (routerModel == null || candidates.isEmpty()) {
            return null;
        }
        StringBuilder sys = new StringBuilder();
        sys.append("""
                You are a chat-flow router. Given a user message and a list of named flows,
                decide which flow (if any) the user message should activate.

                Reply with ONLY the flow id from the list that best matches, or the literal
                word "none" when no flow is a good fit. Do not add explanations or quotes.

                Flows:
                """);
        for (RouterCandidate c : candidates) {
            sys.append("- id: ").append(c.flow().getId())
                    .append(" | name: ").append(ChatFlowOps.safe(c.flow().getName()))
                    .append(" | when to use: ").append(c.flow().getTriggerDescription().trim())
                    .append('\n');
        }
        sys.append("\nReply with one of: ");
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 0) {
                sys.append(", ");
            }
            sys.append(candidates.get(i).flow().getId());
        }
        sys.append(", none");

        String userText = userMessage == null ? "" : userMessage;
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(sys.toString()),
                    new UserMessage(userText)));
            var response = routerModel.call(prompt);
            String reply = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText().trim()
                            : "";
            log.info("[FlowEngine] Router raw reply: '{}'", reply);
            if (reply.isEmpty() || "none".equalsIgnoreCase(reply)) {
                return null;
            }
            String normalized = reply.replaceAll("[^A-Za-z0-9\\-]", " ").trim();
            for (String token : normalized.split("\\s+")) {
                for (RouterCandidate c : candidates) {
                    if (token.equalsIgnoreCase(c.flow().getId())) {
                        return c.flow().getId();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[FlowEngine] Router call failed: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────── Submission recording ───────────────────────────

    /**
     * Emits a submission row when the strategy just transitioned the
     * conversation onto a terminal node. Caller must ensure {@code state}
     * is the ROOT state — sub-flow ends are popped without recording.
     */
    private void recordSubmissionIfTerminal(TurChatFlow flow,
            TurChatFlowState state,
            ChatFlowGraph graph,
            String previousNodeId) {
        String currentNodeId = state.getCurrentNodeId();
        if (currentNodeId == null || currentNodeId.equals(previousNodeId)) {
            return;
        }
        if (!ChatFlowOps.isTerminalNodeId(currentNodeId, graph)) {
            return;
        }
        TurChatFlowSubmission submission = new TurChatFlowSubmission();
        submission.setFlow(flow);
        submission.setConversationId(state.getConversationId());
        submission.setVariablesJson(state.getVariablesJson());
        submission.setCompletedAt(LocalDateTime.now());
        submission.setUserId(resolveUsername());
        submission.setEndNodeId(currentNodeId);
        submissionRepository.save(submission);
        log.info("[FlowEngine] Submission recorded for flow '{}', conversation '{}', endNode '{}'",
                flow.getId(), state.getConversationId(), currentNodeId);
        // Mirror the terminal event into the analytics store. Marker
        // {@code "__abandoned__"} (set by the explicit reset endpoint) maps
        // to ABANDONED; everything else counts as COMPLETED.
        if (chatAnalyticsService != null && chatAnalyticsService.isEnabled()) {
            try {
                TurChatSessionOutcome outcome = "__abandoned__".equals(currentNodeId)
                        ? TurChatSessionOutcome.ABANDONED
                        : TurChatSessionOutcome.COMPLETED;
                chatAnalyticsService.recordSessionEnd(state.getConversationId(), outcome);
            } catch (RuntimeException e) {
                log.debug("[FlowEngine] analytics end failed for conversation {}: {}",
                        state.getConversationId(), e.getMessage());
            }
        }
    }

    private static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    // ─────────────────────────── Sub Flow walking ───────────────────────────

    /**
     * Hard cap on consecutive transparent transitions in
     * {@link #walkTransparentNodes}. A flow that stacks more than this many
     * sub-flows in one turn is almost certainly cyclic; the cap keeps a
     * pathological author error non-fatal.
     */
    private static final int TRANSPARENT_WALK_MAX = 16;

    /**
     * Walks the active state past every transparent node — condition chains,
     * Sub Flow descents (a {@code subFlow} node creates a child state
     * pointing at its parent), and Sub Flow ascents (an {@code end} node on
     * a child state pops back to the parent's next node). Mutates state and
     * persists every touched row; returns the new active leaf.
     * <p>
     * Variables are propagated through the chain so a sub-flow sees and
     * writes to the same variable map as its parent.
     */
    private TurChatFlowState walkTransparentNodes(TurChatFlowState state, ChatModel auxModel) {
        for (int i = 0; i < TRANSPARENT_WALK_MAX; i++) {
            Optional<ChatFlowGraph> graphOpt = parseGraph(state.getFlow());
            if (graphOpt.isEmpty()) {
                return state;
            }
            ChatFlowGraph graph = graphOpt.get();
            // Conditions and switches are transparent transitions (no LLM round-trip in the chat
            // path — the aux model is only consulted to evaluate the rule). Re-walk both every
            // iteration to collapse `condition → switch → condition → …` chains created by
            // descent. Order is irrelevant: each walker no-ops when the current node isn't its
            // type, so calling both is cheap and order-independent.
            ChatFlowOps.walkThroughConditions(state, graph, auxModel);
            ChatFlowOps.walkThroughSwitches(state, graph, auxModel);
            // Skip aiQuestion nodes whose slot is already filled (unless the
            // node opted in to always-ask via overrideExistingValue=true).
            ChatFlowOps.walkThroughSatisfiedQuestions(state, graph);
            Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
            if (currentOpt.isEmpty()) {
                return state;
            }
            // T72 — apply any per-node A/B variant before the transparent node
            // is executed. resolveVariant only swaps aiInstruction and keeps
            // id/type/edges, so routing (advanceToFirstEdge / advanceOnFailure)
            // is identical; the swap matters for the two template-bearing
            // transparent nodes — functionCall (tool input JSON template) and
            // scheduleAgent (routine payload template) — letting authors A/B a
            // single tool-call's input without duplicating the flow. Condition
            // / switch / slot / persona / subFlow nodes don't read
            // aiInstruction, so the swap is a no-op for them.
            ChatFlowNode current = currentOpt.get().resolveVariant(state.getConversationId());
            // Sibling node-level trace for the template-bearing nodes — fires
            // once as the walk executes the node (mirrors the interactive-node
            // trace emitted in advance()). Only when the node carries an
            // experiment, so plain transparent nodes incur a single null-check.
            if ("functionCall".equals(current.type()) || "scheduleAgent".equals(current.type())) {
                ChatFlowNode.formatNodeVariantTrace(current, state.getConversationId())
                        .ifPresent(tr -> log.info("[A/B Node Trace] {}", tr));
            }

            if ("subFlow".equals(current.type())) {
                Optional<TurChatFlowState> child = enterSubFlow(state, current);
                if (child.isPresent()) {
                    state = child.get();
                    continue;
                }
                // Sub-flow not configured / not found / would be recursive:
                // skip the node like a no-op so the parent flow doesn't stall.
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = stateRepository.save(state);
                continue;
            }

            // subFlowSwitch node (T47): like a `switch` for routing edges,
            // but each matched option carries a `subFlowId` and descent runs
            // the corresponding sub-flow. Composes reusable mini-flows by
            // slot value (e.g. lead-capture B2B vs B2C, pricing tier flows).
            // Falls back to first outgoing edge on no match / missing subFlowId
            // (matches the switch wildcard contract — authors should always
            // keep one such edge wired).
            if ("subFlowSwitch".equals(current.type())) {
                Map<String, String> variables = ChatFlowOps.readVariables(state);
                Optional<ChatFlowNode.SwitchOption> matched =
                        ChatFlowOps.resolveSwitchOption(auxModel, current, variables);
                String matchedSubFlowId = matched
                        .map(ChatFlowNode.SwitchOption::subFlowId)
                        .filter(s -> s != null && !s.isBlank())
                        .orElse(null);
                Optional<TurChatFlowState> child = matchedSubFlowId == null
                        ? Optional.empty()
                        : enterSubFlowById(state, matchedSubFlowId, current);
                if (child.isPresent()) {
                    log.info("[FlowEngine] subFlowSwitch '{}' → option '{}' → subFlow '{}'",
                            current.id(),
                            matched.map(ChatFlowNode.SwitchOption::id).orElse("(none)"),
                            matchedSubFlowId);
                    state = child.get();
                    continue;
                }
                log.info("[FlowEngine] subFlowSwitch '{}' no descent — advancing on outgoing edge",
                        current.id());
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = stateRepository.save(state);
                continue;
            }

            // Persona node: write the active-persona override into the state's
            // variables and immediately advance — no LLM round-trip. The
            // resolver (TurAgentPersonaResolver) reads __activePersonaId from
            // the most recent flow state for the conversation, so the next
            // turn already speaks in the new voice.
            if ("persona".equals(current.type())) {
                String personaId = current.personaId();
                if (personaId != null && !personaId.isBlank()) {
                    setActivePersonaVariable(state, personaId);
                }
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = stateRepository.save(state);
                continue;
            }

            // Slot node: SET writes a literal value into the conversation state
            // (honoring overrideExistingValue when the slot already has a value);
            // DELETE removes the slot entirely. Either way the node is
            // transparent — no LLM round-trip — and the flow advances on the
            // single outgoing edge.
            if ("slot".equals(current.type())) {
                ChatFlowOps.applySlotNode(state, current);
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = stateRepository.save(state);
                // SSE notification: a flow slot write is functionally
                // identical to slots.set / writeSlot — emit the full merged
                // map so subscribers don't have to special-case the source.
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
                continue;
            }

            // writeSlot node: like slot SET but expands {{varName}} template
            // references in the value at write time, so the persisted slot
            // is fully resolved (consumers do not have to interpolate).
            if ("writeSlot".equals(current.type())) {
                ChatFlowOps.applyWriteSlotNode(state, current);
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = stateRepository.save(state);
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
                continue;
            }

            // planningStep node (T108): the LLM decomposes the user's goal
            // into a typed TODO list and the engine writes the JSON plan into
            // the node's plan slot (outputVariable, default __plan). Transparent
            // — no user round-trip — and the flow advances on its single
            // outgoing edge. The plan IS a slot value, so the SSE bus surfaces
            // it to subscribers exactly like slot / writeSlot writes do; an
            // iteratePlan node (T108-2) downstream consumes it item by item.
            if ("planningStep".equals(current.type())) {
                ChatFlowOps.applyPlanningStepNode(state, current, auxModel);
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = stateRepository.save(state);
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
                continue;
            }

            // iteratePlan node (T108-2): walks the plan produced by an upstream
            // planningStep one item at a time. On each entry it picks the first
            // PENDING item, exposes it to the body sub-flow via the reserved
            // __planItemId / __planItemTitle slots, and descends into the body
            // sub-flow (subFlowId) to execute it. On ascent the engine marks (or
            // removes, per completionMode) the in-flight item and stays on this
            // node so the next walk picks the next pending item. When nothing is
            // pending, the markers are cleared and the flow advances on the
            // single outgoing edge. The body-less case (no subFlowId, or a
            // descent that can't happen) completes the item in place so the loop
            // always terminates instead of re-picking the same item forever.
            if ("iteratePlan".equals(current.type())) {
                String planSlot = planSlotKeyOf(current);
                Map<String, String> variables = ChatFlowOps.readVariables(state);
                List<ChatFlowOps.PlanItem> plan = ChatFlowOps.parsePlan(variables.get(planSlot));
                Optional<ChatFlowOps.PlanItem> next = ChatFlowOps.firstPendingItem(plan);
                if (next.isEmpty()) {
                    ChatFlowOps.clearPlanIterationMarkers(state);
                    ChatFlowOps.advanceToFirstEdge(state, graph, current);
                    state = stateRepository.save(state);
                    slotEventBus.publish(state.getConversationId(),
                            listSlotsForConversation(state.getConversationId()).slots());
                    continue;
                }
                ChatFlowOps.PlanItem item = next.get();
                ChatFlowOps.setPlanIterationMarkers(state, item);
                state = stateRepository.save(state);
                Optional<TurChatFlowState> child = enterSubFlowById(state, current.subFlowId(), current);
                if (child.isPresent()) {
                    log.info("[FlowEngine] iteratePlan '{}' → item '{}' ('{}') → body subFlow '{}'",
                            current.id(), item.id(), truncate(item.title(), 60), current.subFlowId());
                    state = child.get();
                    continue;
                }
                // No body sub-flow to descend into — complete the item in place
                // (mark_done / remove) so the iteration advances instead of
                // looping on the same pending item.
                log.info("[FlowEngine] iteratePlan '{}' has no usable body subFlow — completing item '{}' in place",
                        current.id(), item.id());
                ChatFlowOps.completePlanItem(state, planSlot, item.id(), current.completionMode());
                state = stateRepository.save(state);
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
                continue;
            }

            // functionCall node (T46): deterministic tool invocation from the
            // flow — NATIVE @Tool methods (incl. Custom Tools registered as
            // such) executed with aiInstruction as the JSON input template
            // ({{slot}} interpolated) and the result written to outputVariable.
            // T49: when the node opts in via continueOnFailure=true, a failed
            // invocation routes the flow along the 'failure' outgoing edge for
            // graceful recovery; otherwise the engine advances to the first
            // edge as before (legacy lenient default).
            if ("functionCall".equals(current.type())) {
                TurFunctionCallNodeExecutor.ExecutionResult fnResult =
                        functionCallExecutor.execute(state, current);
                if (!fnResult.ok() && Boolean.TRUE.equals(current.continueOnFailure())) {
                    ChatFlowOps.advanceOnFailure(state, graph, current);
                } else {
                    ChatFlowOps.advanceToFirstEdge(state, graph, current);
                }
                state = stateRepository.save(state);
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
                continue;
            }

            // webhook node (T62): deterministic outbound webhook POST from the
            // flow — fires a named admin-declared webhook (CRM push) at a
            // precise step, independent of slot-write triggers or handoff.
            // Same failure-edge contract as functionCall: continueOnFailure
            // routes a delivery failure along the 'failure' edge; otherwise
            // the engine logs + advances to the first edge.
            if ("webhook".equals(current.type())) {
                TurChatWebhookNodeExecutor.ExecutionResult whResult =
                        webhookNodeExecutor.execute(state, current);
                if (!whResult.ok() && Boolean.TRUE.equals(current.continueOnFailure())) {
                    ChatFlowOps.advanceOnFailure(state, graph, current);
                } else {
                    ChatFlowOps.advanceToFirstEdge(state, graph, current);
                }
                state = stateRepository.save(state);
                continue;
            }

            // scheduleAgent node (T48): asynchronous routine dispatch. First
            // entry enqueues a JMS message and parks the flow on this node;
            // subsequent re-entries (driven by slot-bus auto-resume on
            // completion, or the user's next turn) poll for the output slot
            // or expiry. WAITING bails out of the transparent walk so the
            // chat turn returns; COMPLETED advances normally; TIMEOUT routes
            // via the optional 'timeout' sourceHandle; FAILED logs + advances
            // like functionCall does on resolution errors.
            if ("scheduleAgent".equals(current.type())) {
                TurScheduleAgentNodeExecutor.Outcome outcome =
                        scheduleAgentExecutor.execute(state, current);
                switch (outcome) {
                    case WAITING_FIRED -> {
                        state = stateRepository.save(state);
                        // Fresh enqueue → publish so SSE subscribers (chat
                        // UI's waiting indicator, slot inspector) see the
                        // __scheduleAgent_pending_<nodeId> marker the
                        // executor just wrote.
                        slotEventBus.publish(state.getConversationId(),
                                listSlotsForConversation(state.getConversationId()).slots());
                        return state;
                    }
                    case WAITING_POLL -> {
                        // Markers already set on a previous turn — no slot
                        // changed, so do NOT republish. Re-publishing would
                        // feed the auto-resume listener, which would call
                        // resumeParkedScheduleAgents → re-enter here →
                        // WAITING_POLL again → publish again, looping
                        // forever.
                        return state;
                    }
                    case COMPLETED -> {
                        ChatFlowOps.advanceToFirstEdge(state, graph, current);
                        state = stateRepository.save(state);
                        slotEventBus.publish(state.getConversationId(),
                                listSlotsForConversation(state.getConversationId()).slots());
                    }
                    case TIMEOUT -> {
                        TurScheduleAgentNodeExecutor.advanceOnTimeout(state, graph, current);
                        state = stateRepository.save(state);
                        slotEventBus.publish(state.getConversationId(),
                                listSlotsForConversation(state.getConversationId()).slots());
                    }
                    case FAILED -> {
                        if (Boolean.TRUE.equals(current.continueOnFailure())) {
                            ChatFlowOps.advanceOnFailure(state, graph, current);
                        } else {
                            ChatFlowOps.advanceToFirstEdge(state, graph, current);
                        }
                        state = stateRepository.save(state);
                    }
                }
                continue;
            }

            if ("end".equals(current.type()) && state.getParentStateId() != null) {
                TurChatFlowState parent = ascendFromSubFlow(state);
                if (parent == null) {
                    return state;
                }
                state = parent;
                continue;
            }

            // T121 — `suspend` node parks the cursor indefinitely. The
            // walker stops here; the chat path replies with a "parked"
            // override (handled by the executor branch) and the cursor
            // doesn't move until POST /api/chat/resume/{conversationId}
            // is invoked by an external system / human approval click /
            // scheduled job.
            if ("suspend".equals(current.type())) {
                return state;
            }

            return state;
        }
        log.warn("[FlowEngine] walkTransparentNodes hit the {}-hop safety cap on conversation '{}'",
                TRANSPARENT_WALK_MAX, state.getConversationId());
        return state;
    }

    /**
     * Creates a child state for the sub-flow referenced by {@code subFlowNode}
     * and links it to {@code parent}. Returns empty when the sub-flow id is
     * missing, the target flow can't be loaded / parsed, or descending would
     * introduce a cycle (the target flow already appears in the parent
     * chain). The parent state stays put on the Sub Flow node so we know
     * where to resume on ascent.
     */
    private Optional<TurChatFlowState> enterSubFlow(TurChatFlowState parent, ChatFlowNode subFlowNode) {
        return enterSubFlowById(parent, subFlowNode.subFlowId(), subFlowNode);
    }

    /**
     * Resolves the plan slot an {@code iteratePlan} (or {@code planningStep})
     * node operates on: the node's {@code outputVariable}, or
     * {@link ChatFlowOps#DEFAULT_PLAN_SLOT} when blank. Trimmed so the key
     * matches what {@code applyPlanningStepNode} wrote.
     *
     * @since 2026.3.1
     */
    private static String planSlotKeyOf(ChatFlowNode node) {
        String slot = node == null ? null : node.outputVariable();
        return (slot == null || slot.isBlank()) ? ChatFlowOps.DEFAULT_PLAN_SLOT : slot.trim();
    }

    /**
     * Shared body for both the plain {@code subFlow} node (T18) and the
     * {@code subFlowSwitch} node (T47) — descent is identical once we know
     * the target sub-flow id; the only difference is where the id came from
     * (node field vs matched switch option's {@code subFlowId}).
     *
     * @param parent       the parent state at the time of descent
     * @param subFlowId    the resolved sub-flow id (may be null/blank — yields empty)
     * @param sourceNode   originating chat-flow node, used for log context only
     */
    private Optional<TurChatFlowState> enterSubFlowById(TurChatFlowState parent,
            String subFlowId, ChatFlowNode sourceNode) {
        if (subFlowId == null || subFlowId.isBlank()) {
            log.warn("[FlowEngine] Sub Flow descent from node '{}' has no subFlowId — skipping",
                    sourceNode.id());
            return Optional.empty();
        }
        if (chainContainsFlow(parent, subFlowId)) {
            log.warn("[FlowEngine] Refusing recursive Sub Flow '{}' on conversation '{}' — already in chain",
                    subFlowId, parent.getConversationId());
            return Optional.empty();
        }
        Optional<TurChatFlow> subFlowOpt = chatFlowRepository.findById(subFlowId);
        if (subFlowOpt.isEmpty()) {
            log.warn("[FlowEngine] Sub Flow '{}' referenced by node '{}' not found",
                    subFlowId, sourceNode.id());
            return Optional.empty();
        }
        TurChatFlow subFlow = subFlowOpt.get();
        if (subFlow.getEnabled() != 1) {
            log.warn("[FlowEngine] Sub Flow '{}' is disabled — skipping", subFlowId);
            return Optional.empty();
        }
        Optional<ChatFlowGraph> subGraphOpt = parseGraph(subFlow);
        if (subGraphOpt.isEmpty()) {
            log.warn("[FlowEngine] Sub Flow '{}' has no parseable graph — skipping", subFlowId);
            return Optional.empty();
        }
        ChatFlowGraph subGraph = subGraphOpt.get();
        Optional<ChatFlowNode> startOpt = subGraph.startNode();
        if (startOpt.isEmpty()) {
            log.warn("[FlowEngine] Sub Flow '{}' has no start node — skipping", subFlowId);
            return Optional.empty();
        }
        ChatFlowNode anchor = ChatFlowOps.firstInteractiveNode(subGraph, startOpt.get())
                .orElse(startOpt.get());
        TurChatFlowState child = new TurChatFlowState();
        child.setConversationId(parent.getConversationId());
        child.setFlow(subFlow);
        child.setCurrentNodeId(anchor.id());
        child.setParentStateId(parent.getId());
        // Variables are SHARED across the chain — copy parent's snapshot so
        // the sub-flow's strategy sees what's already been collected. On
        // ascent we copy back any new captures.
        child.setVariablesJson(parent.getVariablesJson() == null ? "{}" : parent.getVariablesJson());
        TurChatFlowState saved = stateRepository.save(child);
        log.info("[FlowEngine] Sub Flow descend: parent='{}' (flow '{}', node '{}') → child='{}' (flow '{}', node '{}')",
                parent.getId(), parent.getFlow().getId(), parent.getCurrentNodeId(),
                saved.getId(), subFlow.getId(), anchor.id());
        return Optional.of(saved);
    }

    /**
     * Pops {@code child} back to its parent state: copies the (possibly
     * mutated) variables back up, advances the parent past its Sub Flow
     * node, deletes the child row. Returns the parent state, or null when
     * the parent can't be loaded.
     */
    private TurChatFlowState ascendFromSubFlow(TurChatFlowState child) {
        Optional<TurChatFlowState> parentOpt = stateRepository.findById(child.getParentStateId());
        if (parentOpt.isEmpty()) {
            log.warn("[FlowEngine] Sub Flow ascend: parent state '{}' missing for child '{}' — orphan",
                    child.getParentStateId(), child.getId());
            return null;
        }
        TurChatFlowState parent = parentOpt.get();
        Optional<ChatFlowGraph> parentGraphOpt = parseGraph(parent.getFlow());
        if (parentGraphOpt.isEmpty()) {
            log.warn("[FlowEngine] Sub Flow ascend: parent flow '{}' unparseable", parent.getFlow().getId());
            return null;
        }
        ChatFlowGraph parentGraph = parentGraphOpt.get();
        Optional<ChatFlowNode> subFlowNodeOpt = parentGraph.nodeById(parent.getCurrentNodeId());
        if (subFlowNodeOpt.isEmpty()) {
            log.warn("[FlowEngine] Sub Flow ascend: parent's current node '{}' missing in flow '{}'",
                    parent.getCurrentNodeId(), parent.getFlow().getId());
            return null;
        }
        // Propagate any variables captured by the sub-flow back to the parent.
        parent.setVariablesJson(child.getVariablesJson() == null ? "{}" : child.getVariablesJson());
        String previousParentNode = parent.getCurrentNodeId();
        ChatFlowNode parentNode = subFlowNodeOpt.get();

        // T108-2 — when the parent is an iteratePlan node, the body sub-flow
        // just finished executing ONE plan item. Complete that item (mark_done
        // / remove, per completionMode) and STAY on the iteratePlan node so the
        // transparent walk re-enters it and picks the next pending item, rather
        // than advancing past it like a plain subFlow ascent does.
        if ("iteratePlan".equals(parentNode.type())) {
            String planSlot = planSlotKeyOf(parentNode);
            String itemId = ChatFlowOps.readVariables(parent).get(ChatFlowOps.PLAN_ITEM_ID_SLOT);
            boolean changed = ChatFlowOps.completePlanItem(parent, planSlot, itemId,
                    parentNode.completionMode());
            log.info("[FlowEngine] iteratePlan ascend: child='{}' → parent='{}' completed item '{}' (changed={}) — staying on '{}'",
                    child.getId(), parent.getId(), itemId, changed, parent.getCurrentNodeId());
            stateRepository.delete(child);
            TurChatFlowState saved = stateRepository.save(parent);
            if (changed) {
                slotEventBus.publish(saved.getConversationId(),
                        listSlotsForConversation(saved.getConversationId()).slots());
            }
            return saved;
        }

        ChatFlowOps.advanceToFirstEdge(parent, parentGraph, parentNode);
        log.info("[FlowEngine] Sub Flow ascend: child='{}' (flow '{}') → parent='{}' (flow '{}', '{}' → '{}')",
                child.getId(), child.getFlow().getId(),
                parent.getId(), parent.getFlow().getId(),
                previousParentNode, parent.getCurrentNodeId());
        stateRepository.delete(child);
        return stateRepository.save(parent);
    }

    /**
     * Writes (or overwrites) the reserved {@code __activePersonaId} entry in
     * the state's {@code variablesJson} so the persona resolver picks up the
     * new active voice on the next turn. Preserves any user-collected
     * variables already in the document.
     *
     * <p>The reserved key matches
     * {@link com.viglet.turing.genai.persona.TurAgentPersonaResolver#ACTIVE_PERSONA_VAR}
     * — kept as a literal here to avoid a circular dependency between the
     * engine and the resolver.
     *
     * @since 2026.2.7
     */
    private void setActivePersonaVariable(TurChatFlowState state, String personaId) {
        try {
            java.util.Map<String, Object> vars;
            String json = state.getVariablesJson();
            if (json == null || json.isBlank()) {
                vars = new java.util.LinkedHashMap<>();
            } else {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, java.util.Map.class);
                vars = parsed == null ? new java.util.LinkedHashMap<>() : parsed;
            }
            vars.put("__activePersonaId", personaId);
            state.setVariablesJson(OBJECT_MAPPER.writeValueAsString(vars));
        } catch (JacksonException e) {
            log.warn("[FlowEngine] could not update __activePersonaId on state {}: {}",
                    state.getId(), e.getMessage());
        }
    }

    private boolean chainContainsFlow(TurChatFlowState state, String flowId) {
        Set<String> visited = new LinkedHashSet<>();
        TurChatFlowState cursor = state;
        while (cursor != null) {
            if (!visited.add(cursor.getId())) {
                // Defensive: parentStateId pointing into a cycle — bail out.
                return true;
            }
            if (cursor.getFlow() != null && flowId.equals(cursor.getFlow().getId())) {
                return true;
            }
            String parentId = cursor.getParentStateId();
            if (parentId == null) {
                return false;
            }
            cursor = stateRepository.findById(parentId).orElse(null);
        }
        return false;
    }

    /**
     * Resolves the deepest active leaf of the chain rooted at {@code root}.
     * When the conversation already descended into one or more sub-flows,
     * the leaf is what {@link TurAgentChatExecutor} should resume on.
     */
    private TurChatFlowState resolveActiveLeaf(TurChatFlowState root) {
        TurChatFlowState cursor = root;
        Set<String> visited = new HashSet<>();
        while (visited.add(cursor.getId())) {
            List<TurChatFlowState> children = stateRepository.findByParentStateId(cursor.getId());
            if (children.isEmpty()) {
                return cursor;
            }
            // Prefer non-terminal children; if every child is terminal, just
            // pick the first — the caller will treat it as a regular leaf.
            TurChatFlowState next = children.get(0);
            for (TurChatFlowState candidate : children) {
                Optional<ChatFlowGraph> g = parseGraph(candidate.getFlow());
                if (g.isPresent()) {
                    Optional<ChatFlowNode> n = g.get().nodeById(candidate.getCurrentNodeId());
                    if (n.isPresent() && !"end".equals(n.get().type())) {
                        next = candidate;
                        break;
                    }
                }
            }
            cursor = next;
        }
        return cursor;
    }
}
