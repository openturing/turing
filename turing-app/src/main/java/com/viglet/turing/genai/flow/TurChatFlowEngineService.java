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

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy;
import com.viglet.turing.persistence.dto.agent.TurChatFlowSubmissionDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
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

    // --- S1192: extracted duplicated literals ---
    private static final String HUMAN_APPROVAL = "humanApproval";
    private static final String SCHEDULE_AGENT = "scheduleAgent";
    private static final String FLOW = "flow '";


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
     * T75 — upper bound on the {@code userMessage} field emitted in the
     * per-turn A/B trace line. Keeps log records bounded so ELK/Loki
     * shippers don't truncate the structured prefix when a visitor
     * pastes a wall of text.
     *
     * @since 2026.3.1
     */
    private static final int VARIANT_TRACE_USER_MSG_MAX = 200;


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
    private final TurHumanApprovalNodeExecutor humanApprovalNodeExecutor;
    private final TurChatFlowTriggerRouter triggerRouter;
    private final Map<TurChatFlowGuardrailMethod, TurChatFlowGuardrailStrategy> strategyByMethod;
    /**
     * T237 — opt-in per-turn node-visit log for the path-aware funnel.
     * Field-injected (not a constructor param) so the unit tests that
     * {@code new} this service directly compile unchanged and default the flag
     * to {@code false} — the safe, behaviour-preserving default.
     */
    @org.springframework.beans.factory.annotation.Value(
            "${turing.chat.analytics.node-visit-log.enabled:false}")
    private boolean nodeVisitLogEnabled;

    /**
     * Used by {@link #resolveFlowForRead} to ask the JPA provider (via the
     * standard {@link jakarta.persistence.PersistenceUnitUtil}) whether a flow
     * is already initialized, so an already-loaded flow needs no DB round-trip.
     * Field-injected (not a constructor param) for the same reason as
     * {@link #nodeVisitLogEnabled}: the unit tests that {@code new} this service
     * directly compile unchanged and leave it {@code null}, in which case
     * {@code resolveFlowForRead} treats the passed flow as already loaded (true
     * for the plain entities those tests use).
     */
    @jakarta.persistence.PersistenceUnit
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    /**
     * T487 / §XXVIII.2 — dedicated read-model cache for the parsed
     * {@link ChatFlowGraph}. Field-injected (not a constructor param) for the
     * same reason as {@link #entityManagerFactory}: the unit tests that
     * {@code new} this service directly compile unchanged and leave it
     * {@code null}, in which case {@link #parseGraph} falls back to the
     * uncached parse — behaviour-identical to the pre-T487 path.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TurChatFlowGraphCache graphCache;

    /**
     * T119 — node types that <em>park</em> the conversation: the engine stops
     * walking and waits for an external resume. {@code suspend} (T121) is
     * resumed via {@code POST /chat/resume}; {@code humanApproval} (T119) via
     * the approval endpoint / timeout sweep. Both are advanced by
     * {@link #resumeSuspendedFlow} and surfaced on the parked-conversations
     * dashboard.
     */
    static final Set<String> PARKED_NODE_TYPES = Set.of("suspend", HUMAN_APPROVAL);

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
            TurHumanApprovalNodeExecutor humanApprovalNodeExecutor,
            TurChatFlowTriggerRouter triggerRouter,
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
        this.humanApprovalNodeExecutor = humanApprovalNodeExecutor;
        this.triggerRouter = triggerRouter;
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

    /**
     * Parses the flow's {@code definitionJson} into a {@link ChatFlowGraph}.
     *
     * <p>A persisted flow (id present) routes through the
     * {@link TurChatFlowGraphCache} read-model cache (T487): the parsed graph is
     * an immutable record tree with no JPA proxies, so a cache hit skips both the
     * JSON parse and the {@link #resolveFlowForRead} hydration round-trip. The
     * cache is evicted on any flow write via {@link #evictFlowDerivedCaches()}.
     * Transient flows (no id) and unit tests that {@code new} the service without
     * the cache bean fall back to the uncached parse.
     */
    public Optional<ChatFlowGraph> parseGraph(TurChatFlow flow) {
        if (graphCache != null && flow != null && flow.getId() != null) {
            return Optional.ofNullable(graphCache.graph(flow.getId(), () -> parseGraphUncached(flow)));
        }
        return Optional.ofNullable(parseGraphUncached(flow));
    }

    /**
     * Resolves the flow for out-of-session reads and deserializes its
     * {@code definitionJson}. Returns {@code null} (rather than throwing) for a
     * missing / blank / unparseable definition so the {@link TurChatFlowGraphCache}
     * {@code unless} guard keeps it out of the cache.
     */
    private ChatFlowGraph parseGraphUncached(TurChatFlow flow) {
        flow = resolveFlowForRead(flow);
        if (flow == null || flow.getDefinitionJson() == null || flow.getDefinitionJson().isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(flow.getDefinitionJson(), ChatFlowGraph.class);
        } catch (JacksonException e) {
            log.warn("[FlowEngine] Failed to parse definitionJson for flow '{}': {}",
                    flow.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Returns a flow whose scalar columns ({@code definitionJson}, name, …) are
     * safe to read outside an open session.
     *
     * <p>Most callers pass {@code state.getFlow()} from a state loaded through a
     * {@code JOIN FETCH} finder, so the flow is already initialized and is
     * returned as-is — no DB round-trip. The exception is a {@code save()}
     * result: Spring Data returns a <em>merge</em>-managed state whose
     * {@code @ManyToOne(LAZY)} flow is an <em>uninitialized</em> proxy, and the
     * engine parses the graph after the save commits — dereferencing that proxy
     * would raise {@code LazyInitializationException} (we deliberately run with
     * {@code hibernate.enable_lazy_load_no_trans=false}).
     *
     * <p>We detect the two cases with the standard JPA
     * {@link jakarta.persistence.PersistenceUnitUtil#isLoaded(Object)} and, only
     * for the uninitialized proxy, re-fetch via the JPQL
     * {@link TurChatFlowRepository#findByIdInitialized(String)} — a query
     * hydrates the entity, whereas {@code findById}/{@code EntityManager.find}
     * would hand back the same uninitialized proxy from the persistence context.
     * A transient flow (no id yet) is returned as-is; a missing row falls back
     * to the passed instance.
     */
    private TurChatFlow resolveFlowForRead(TurChatFlow flow) {
        if (flow == null || flow.getId() == null) {
            return flow;
        }
        if (entityManagerFactory == null
                || entityManagerFactory.getPersistenceUnitUtil().isLoaded(flow)) {
            return flow;
        }
        return chatFlowRepository.findByIdInitialized(flow.getId()).orElse(flow);
    }

    /**
     * Persists a runtime state and returns a result whose {@code flow} is safe
     * to read outside the loading session. Spring Data {@code save()} merges the
     * entity and the returned managed copy carries an <em>uninitialized</em>
     * {@code @ManyToOne(LAZY)} flow proxy; since every state handed to a save
     * already holds an initialized flow (loaded via a {@code JOIN FETCH} finder
     * or passed in by the caller), we re-attach that instance onto the merge
     * result — so {@code saved.getFlow()} is readable with no extra DB
     * round-trip. The read-side counterpart is {@link #resolveFlowForRead}.
     */
    private TurChatFlowState saveState(TurChatFlowState state) {
        TurChatFlow flow = state.getFlow();
        TurChatFlowState saved = stateRepository.save(state); // NOSONAR — the one real save; callers use saveState(...)
        if (saved != state && flow != null) {
            saved.setFlow(flow);
        }
        return saved;
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
        TurChatFlowState saved = saveState(created);
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
        state = saveState(state);
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
        if (leaf != state || !Objects.equals(preWalkNodeId, leaf.getCurrentNodeId())) {
            leaf = saveState(leaf);
        }
        // T237 — opt-in per-turn node-visit log: append the node this turn
        // settled on to the leaf's path, then persist. Gated so the default
        // (feature off) path is unchanged — no extra column write, no behaviour
        // change. The path is snapshotted onto the submission at flow end.
        if (nodeVisitLogEnabled) {
            String appendedPath = TurChatFlowNodeVisitPath.append(
                    leaf.getNodeVisitPath(), leaf.getCurrentNodeId());
            if (!Objects.equals(appendedPath, leaf.getNodeVisitPath())) {
                leaf.setNodeVisitPath(appendedPath);
                leaf = saveState(leaf);
            }
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
        applyResumeSlotUpdates(conversationId, slotUpdates, resumeReason);
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        int resumed = 0;
        for (TurChatFlowState state : states) {
            if (resumeState(conversationId, state, resumeReason)) {
                resumed++;
            }
        }
        return new ResumeResult(resumed, resumed == 0);
    }

    private void applyResumeSlotUpdates(String conversationId, Map<String, String> slotUpdates,
            String resumeReason) {
        if (slotUpdates == null || slotUpdates.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : slotUpdates.entrySet()) {
            writeSlot(conversationId, e.getKey(), e.getValue(),
                    com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource.ENDPOINT,
                    resumeReason == null ? "resume" : "resume=" + resumeReason);
        }
    }

    /**
     * Walks one state past its parked node if it sits on a parked type. Returns
     * true when the state was actually resumed.
     */
    private boolean resumeState(String conversationId, TurChatFlowState state, String resumeReason) {
        ChatFlowGraph graph = parseGraph(state.getFlow()).orElse(null);
        if (graph == null) return false;
        Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
        if (currentOpt.isEmpty()) return false;
        ChatFlowNode parkedNode = currentOpt.get();
        if (!PARKED_NODE_TYPES.contains(parkedNode.type())) return false;
        log.info("[FlowEngine] resume: conv='{}' walking past {} node '{}' (reason='{}')",
                conversationId, parkedNode.type(), state.getCurrentNodeId(), resumeReason);
        // T119 — when force-advancing past a humanApproval node, cancel any
        // still-PENDING record (the decide/timeout paths already moved it to
        // DECIDED/TIMED_OUT, so this is a no-op except on an admin unblock).
        if (HUMAN_APPROVAL.equals(parkedNode.type())) {
            humanApprovalNodeExecutor.cancelPending(conversationId, parkedNode.id());
        }
        ChatFlowOps.advanceToFirstEdge(state, graph, parkedNode);
        state = saveState(state);
        TurChatFlowState leaf = walkTransparentNodes(state, null);
        if (leaf != state) {
            saveState(leaf);
        }
        return true;
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
        int fieldsWritten = writeFormFields(conversationId, values);
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
            } else {
                ChatFlowNode current = currentOpt.get();
                Map<String, String> vars = ChatFlowOps.readVariables(state);
                if (ChatFlowOps.isNativeForm(current)
                        && ChatFlowOps.isNativeFormSatisfied(current, vars)) {
                    log.info("[FlowEngine] form-submit: conv='{}' walking past satisfied form node '{}'",
                            conversationId, state.getCurrentNodeId());
                    state = advanceAndWalk(state, graph, current);
                    advanced++;
                }
                currentNodeId = state.getCurrentNodeId();
            }
        }
        return new FormSubmitResult(fieldsWritten, advanced, currentNodeId);
    }

    /** Writes each non-blank form field to its slot (audit NODE/formCapture); returns the count written. */
    private int writeFormFields(String conversationId, Map<String, String> values) {
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
        return fieldsWritten;
    }

    /** Advances {@code state} past {@code current}, walks transparent nodes, and returns the saved leaf. */
    private TurChatFlowState advanceAndWalk(TurChatFlowState state, ChatFlowGraph graph,
            ChatFlowNode current) {
        ChatFlowOps.advanceToFirstEdge(state, graph, current);
        TurChatFlowState saved = saveState(state);
        TurChatFlowState leaf = walkTransparentNodes(saved, null);
        return leaf != saved ? saveState(leaf) : saved;
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
            Optional<String> reason = suspendedReasonForState(state);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    /**
     * True when {@code conversationId} has a chat flow IN PROGRESS — i.e. a leaf
     * flow state (no descendant) whose cursor sits on a non-{@code end} node.
     * Mirrors {@link #findContinuationFlow} so callers can tell whether an
     * incoming user message is flow INPUT (an answer to the current question)
     * rather than a free-form query.
     *
     * <p>The public SN RAG path uses this to skip its relevance-gate refusal
     * (T329) when a flow governs the turn: a flow answer like
     * {@code "Finanças & Investimentos"} retrieves zero site documents and would
     * otherwise be short-circuited to "...não disponível na base de dados..."
     * BEFORE the flow ever sees it, derailing the conversation.
     *
     * @since 2026.3.4
     */
    @Transactional(readOnly = true)
    public boolean hasActiveFlow(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        if (states.isEmpty()) {
            return false;
        }
        Set<String> hasDescendant = states.stream()
                .map(TurChatFlowState::getParentStateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (TurChatFlowState state : states) {
            if (hasDescendant.contains(state.getId())) {
                continue;
            }
            Optional<ChatFlowNode> currentOpt = parseGraph(state.getFlow())
                    .flatMap(g -> g.nodeById(state.getCurrentNodeId()));
            if (currentOpt.isPresent() && !"end".equals(currentOpt.get().type())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the suspended-reason label when {@code state}'s cursor sits on a
     * parked node, otherwise empty.
     */
    private Optional<String> suspendedReasonForState(TurChatFlowState state) {
        ChatFlowGraph graph = parseGraph(state.getFlow()).orElse(null);
        if (graph == null) return Optional.empty();
        Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
        if (currentOpt.isEmpty()) return Optional.empty();
        ChatFlowNode current = currentOpt.get();
        if (!PARKED_NODE_TYPES.contains(current.type())) {
            return Optional.empty();
        }
        String label = current.label();
        if (label != null && !label.isBlank()) {
            return Optional.of(label);
        }
        return Optional.of(HUMAN_APPROVAL.equals(current.type())
                ? "awaiting approval" : "suspended");
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
            if (graph == null) {
                continue;
            }
            Optional<ChatFlowNode> currentOpt = graph.nodeById(state.getCurrentNodeId());
            if (currentOpt.isPresent() && SCHEDULE_AGENT.equals(currentOpt.get().type())) {
                log.info("[FlowEngine] auto-resume: conv='{}' re-walking parked state '{}'",
                        conversationId, state.getId());
                TurChatFlowState leaf = walkTransparentNodes(state, null);
                if (leaf != state) {
                    saveState(leaf);
                }
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
            String suspendedReason,
            /**
             * T461 (Block Z) — the active persona id resolved for this
             * conversation (the {@code __activePersonaId} flow variable set by a
             * persona node / override), or {@code null} when none is active.
             * Exposed so the client analytics bus can stamp {@code persona_id}
             * on every event and GA4 can answer "which persona variation
             * converted" natively.
             */
            String personaId) {
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
    private TurChatFlowState findLeafState(List<TurChatFlowState> states) {
        Set<String> hasDescendant = states.stream()
                .map(TurChatFlowState::getParentStateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return states.stream()
                .filter(s -> !hasDescendant.contains(s.getId()))
                .sorted(Comparator.comparing(TurChatFlowState::getUpdatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .findFirst()
                .orElse(states.get(0));
    }

    /** Suspended-reason label when the leaf cursor sits on a parked node, else null. */
    private String resolveLeafSuspendedReason(TurChatFlow flow, TurChatFlowState leaf) {
        if (flow == null) {
            return null;
        }
        ChatFlowGraph leafGraph = parseGraph(flow).orElse(null);
        if (leafGraph == null) {
            return null;
        }
        Optional<ChatFlowNode> currentOpt = leafGraph.nodeById(leaf.getCurrentNodeId());
        if (currentOpt.isEmpty() || !PARKED_NODE_TYPES.contains(currentOpt.get().type())) {
            return null;
        }
        String label = currentOpt.get().label();
        if (label != null && !label.isBlank()) {
            return label;
        }
        return HUMAN_APPROVAL.equals(currentOpt.get().type()) ? "awaiting approval" : "suspended";
    }

    public ConversationStateDto getConversationState(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ConversationStateDto(conversationId, null, null, null, null, null, null, null, null);
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        if (states.isEmpty()) {
            return new ConversationStateDto(conversationId, null, null, null, null, null, null, null, null);
        }
        // Skip rows that point at other rows as parent — we want the leaf.
        TurChatFlowState leaf = findLeafState(states);
        TurChatFlow flow = leaf.getFlow();
        // T121 — surface the parked reason when the leaf cursor sits on a
        // suspend node. The portal renders this as a "Waiting..." banner.
        String suspendedReason = resolveLeafSuspendedReason(flow, leaf);
        // T461 — the active persona id (the reserved __activePersonaId variable)
        // for client-side A/B attribution. Blank-safe; null when no persona node
        // ran in this conversation.
        String personaId = ChatFlowOps.readVariablesJson(leaf.getVariablesJson())
                .get(TurAgentPersonaResolver.ACTIVE_PERSONA_VAR);
        return new ConversationStateDto(
                conversationId,
                flow == null ? null : flow.getId(),
                flow == null ? null : flow.getName(),
                leaf.getCurrentNodeId(),
                flow == null || flow.getGuardrailMethod() == null
                        ? null : flow.getGuardrailMethod().name(),
                flow == null ? null : flow.getExperimentKey(),
                flow == null ? null : flow.getVariantLabel(),
                suspendedReason,
                (personaId == null || personaId.isBlank()) ? null : personaId);
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
            saveState(state);
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
        return pinFlowForConversation(agent, conversationId, flowSelector, null);
    }

    /**
     * T633 / §XXVII.4 overload — additionally seeds a per-request persona
     * ({@code activePersonaId}) into the freshly-pinned flow state so the
     * {@code TurAgentPersonaResolver} speaks as it across every turn of the
     * flow, without needing a persona node in the flow graph. Used by the
     * anonymous public {@code flow-select} path so the demo's chosen persona
     * survives the pin. The id must already be validated against the agent's
     * catalog by the caller; a blank id leaves the seed untouched (legacy
     * behaviour, byte-identical to the 3-arg overload).
     *
     * @param activePersonaId nullable/blank → no persona seeded (legacy)
     * @since 2026.3.4
     */
    public PinResult pinFlowForConversation(TurAIAgent agent, String conversationId,
            String flowSelector, String activePersonaId) {
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
            return PinResult.error(FLOW + flowSelector + "' not found on agent "
                    + agent.getId());
        }
        if (flow.getEnabled() != 1) {
            return PinResult.error(FLOW + flow.getId() + "' is disabled");
        }
        Optional<ChatFlowGraph> graphOpt = parseGraph(flow);
        if (graphOpt.isEmpty()) {
            return PinResult.error(FLOW + flow.getId() + "' has no parseable graph");
        }
        ChatFlowGraph graph = graphOpt.get();
        Optional<ChatFlowNode> startNode = graph.startNode();
        if (startNode.isEmpty()) {
            return PinResult.error(FLOW + flow.getId() + "' has no start node");
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

        // T633 — seed the anonymous per-request persona into the receiving
        // flow's variables so the resolver speaks as it from the first turn.
        // Literal key (not the resolver constant) mirrors setActivePersonaVariable,
        // keeping this file free of a dependency on the persona package.
        if (activePersonaId != null && !activePersonaId.isBlank()) {
            seedSlots = new LinkedHashMap<>(seedSlots);
            seedSlots.put("__activePersonaId", activePersonaId);
        }

        // Reset every other in-progress state on this agent so the engine's
        // continuation rule cannot keep the next turn on a stale flow. We
        // delete by agent (not just by flow) to guarantee a single non-
        // terminal leaf remains after this method returns.
        int resetCount = resetAllStatesForAgent(conversationId, agent.getId());

        createInitialStateWithSeed(conversationId, flow, graph, seedSlots);

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
        ChatFlowNode startNode = graph.startNode().orElseThrow(() ->
                new IllegalStateException("graph for flow '" + flow.getId() + "' has no start node"));
        ChatFlowNode anchor = ChatFlowOps.firstInteractiveNode(graph, startNode).orElse(startNode);
        TurChatFlowState created = new TurChatFlowState();
        created.setConversationId(conversationId);
        created.setFlow(flow);
        created.setCurrentNodeId(anchor.id());
        ChatFlowOps.writeVariables(created, seedSlots == null ? Map.of() : seedSlots);
        ChatFlowOps.walkThroughConditions(created, graph, null);
        TurChatFlowState saved = saveState(created);
        TurChatFlowState leaf = walkTransparentNodes(saved, null);
        if (leaf != saved) {
            saveState(leaf);
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
    // S1168: null here is a documented sentinel ("no inheritance contract — caller
    // decides the fallback"), distinct from an empty map ("inherit nothing"). The
    // caller at applySlotInheritance's call sites branches on null vs empty, so
    // returning an empty map would silently break the legacy full-snapshot fallback.
    @SuppressWarnings("java:S1168")
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
            applyInheritanceEntry(entry, safeSource, result);
        }
        return result;
    }

    /**
     * Applies a single inheritance mapping entry into {@code result}: handles
     * the {@code "*"} wildcard (copy every source slot) and named pass-through
     * / rename. Entries with a blank key, or a blank source for a named entry,
     * are skipped.
     */
    private void applyInheritanceEntry(Map.Entry<String, String> entry,
            Map<String, String> safeSource, Map<String, String> result) {
        String receivingName = entry.getKey();
        String sourceName = entry.getValue();
        if (receivingName == null || receivingName.isBlank()) {
            return;
        }
        if ("*".equals(receivingName)) {
            // Wildcard: copy every source slot verbatim. Names that collide
            // with explicit entries are overwritten in iteration order —
            // explicit entries win when they come later (LinkedHashMap
            // preserves insertion).
            result.putAll(safeSource);
            return;
        }
        if (sourceName == null || sourceName.isBlank()) {
            return;
        }
        String value = safeSource.get(sourceName);
        if (value != null) {
            result.put(receivingName, value);
        }
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
            Map<String, String> out = LinkedHashMap.newLinkedHashMap(raw.size());
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
        Optional<FlowSelection> continuation = findContinuationFlow(existingStates, conversationId);
        if (continuation.isPresent()) {
            return continuation;
        }

        // 2) Build candidate list. A flow counts as "completed" only when its
        // ROOT row has reached an end node — child sub-flow rows are popped
        // (deleted) on completion, so any lingering child end node is a stale
        // artifact and should not gate the parent flow's eligibility.
        Set<String> completedFlowIds = collectCompletedRootFlowIds(existingStates);
        List<RouterCandidate> candidates = buildRouterCandidates(agent, completedFlowIds);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // 3) Route: procedural pre-route (fast, deterministic) then the cached
        //    LLM router fallback. See routeToFlow for the gate semantics.
        RouterPick pick = routeToFlow(agent, candidates, lastUserMessage, routerModel);
        // T89 / §VII.10.f — structured router-decision log (candidate flows +
        // scores + winner + method). Best-effort; never breaks the turn.
        recordRouterDecision(agent, conversationId, lastUserMessage, candidates,
                pick.pickedId(), pick.routerMethod(), pick.outcome());
        String pickedId = pick.pickedId();
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
     * Phase 1 of {@link #selectActiveFlow}: returns the in-progress non-terminal
     * leaf flow for this conversation, if any. With Sub Flow nodes a conversation
     * may have multiple state rows in a parent→child chain; the leaf (a row no
     * other row points at as a parent) is the one currently driving the chat.
     *
     * @since 2026.3.1
     */
    private Optional<FlowSelection> findContinuationFlow(List<TurChatFlowState> existingStates,
            String conversationId) {
        Set<String> hasDescendant = existingStates.stream()
                .map(TurChatFlowState::getParentStateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (TurChatFlowState state : existingStates) {
            if (hasDescendant.contains(state.getId())) {
                continue;
            }
            Optional<ChatFlowGraph> graphOpt = parseGraph(state.getFlow());
            if (graphOpt.isPresent()) {
                Optional<ChatFlowNode> currentOpt = graphOpt.get().nodeById(state.getCurrentNodeId());
                if (currentOpt.isPresent() && !"end".equals(currentOpt.get().type())) {
                    log.info("[FlowEngine] Continuation: flow '{}' is in progress for conversation '{}'",
                            state.getFlow().getId(), conversationId);
                    // Continuation: user msg should be processed as input to the
                    // current node by the LlmJudge advance pass.
                    return Optional.of(new FlowSelection(state.getFlow(), graphOpt.get(), state, false));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Phase 2a of {@link #selectActiveFlow}: collects the ids of flows whose
     * ROOT state row has reached an {@code end} node. Child sub-flow rows are
     * popped on completion, so a lingering child end node is a stale artifact
     * and must not gate the parent flow's eligibility.
     *
     * @since 2026.3.1
     */
    private Set<String> collectCompletedRootFlowIds(List<TurChatFlowState> existingStates) {
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
        return completedFlowIds;
    }

    /**
     * Phase 2b of {@link #selectActiveFlow}: builds the eligible router-candidate
     * list — enabled flows with a non-blank trigger description and a parseable
     * graph, excluding ONCE-mode flows already completed on this conversation.
     *
     * @since 2026.3.1
     */
    private List<RouterCandidate> buildRouterCandidates(TurAIAgent agent, Set<String> completedFlowIds) {
        List<RouterCandidate> candidates = new ArrayList<>();
        for (TurChatFlow flow : chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId())) {
            if (flow.getEnabled() != 1
                    || flow.getTriggerDescription() == null || flow.getTriggerDescription().isBlank()) {
                continue;
            }
            TurChatFlowTriggerMode mode = flow.getTriggerMode() != null
                    ? flow.getTriggerMode()
                    : TurChatFlowTriggerMode.ONCE;
            boolean onceAndDone = mode == TurChatFlowTriggerMode.ONCE
                    && completedFlowIds.contains(flow.getId());
            if (!onceAndDone) {
                Optional<ChatFlowGraph> graphOpt = parseGraph(flow);
                if (graphOpt.isPresent()) {
                    candidates.add(new RouterCandidate(flow, graphOpt.get()));
                }
            }
        }
        return candidates;
    }

    /**
     * The router's decision for {@link #selectActiveFlow}: the picked flow id
     * (nullable when nothing matched), the method that produced it, and the
     * procedural-routing diagnostics (nullable) for the structured decision log.
     *
     * @since 2026.3.1
     */
    private record RouterPick(String pickedId, TurChatFlowRouterMethod routerMethod, ProceduralOutcome outcome) {
    }

    /**
     * Phase 3 of {@link #selectActiveFlow}: tries the procedural pre-route first
     * (fast, deterministic, no LLM — commits when one flow dominates the
     * runner-up), then falls back to the cached LLM router. A {@code null}
     * {@code pickedId} means no flow matched.
     *
     * @since 2026.3.1
     */
    private RouterPick routeToFlow(TurAIAgent agent, List<RouterCandidate> candidates,
            String lastUserMessage, ChatModel routerModel) {
        Optional<ProceduralOutcome> proceduralOutcome =
                proceduralRouteExplained(agent.getId(), candidates, lastUserMessage);
        if (proceduralOutcome.map(ProceduralOutcome::decided).orElse(false)) {
            String pickedId = proceduralOutcome.get().pickedId();
            log.info("[FlowEngine] Procedural router picked '{}' (LLM skipped)", pickedId);
            return new RouterPick(pickedId, TurChatFlowRouterMethod.PROCEDURAL, proceduralOutcome.orElse(null));
        }
        // Cached LLM router. Same {agentId, userMessage} pair returns a cached
        // decision on repeated calls so the LLM round-trip pays off once per
        // unique message per agent. Cache is wiped on any TurChatFlow.save/delete.
        RouterCacheResult llm = askRouterCached(agent.getId(), routerModel, candidates, lastUserMessage);
        String pickedId = llm.pickedId();
        TurChatFlowRouterMethod llmMethod = llm.cacheHit()
                ? TurChatFlowRouterMethod.LLM_CACHE
                : TurChatFlowRouterMethod.LLM;
        TurChatFlowRouterMethod routerMethod = pickedId == null ? TurChatFlowRouterMethod.NONE : llmMethod;
        return new RouterPick(pickedId, routerMethod, proceduralOutcome.orElse(null));
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

        // T73 / §VII.8.d — forced variant for QA + sales demo (bypasses
        // hash/bandit + schedule window). A non-matching label or unparseable
        // graph degrades to the normal assignment below.
        Optional<AbResolved> forced = resolveForcedVariant(forcedVariant, candidates,
                experimentKey, routerFlow, routerGraph, conversationId);
        if (forced.isPresent()) {
            return forced.get();
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
        return assignExperimentVariant(variants, routerFlow, routerGraph, experimentKey, conversationId);
    }

    /**
     * T73 — resolves a {@code ?_ab_variant=<label>} forced arm (window-bypassing),
     * recording the assignment. Empty when no/blank label, an unknown label, or an
     * unparseable variant graph — the caller then runs the normal assignment.
     */
    private Optional<AbResolved> resolveForcedVariant(String forcedVariant,
            List<RouterCandidate> candidates, String experimentKey, TurChatFlow routerFlow,
            ChatFlowGraph routerGraph, String conversationId) {
        if (forcedVariant == null || forcedVariant.isBlank()) {
            return Optional.empty();
        }
        // Collected WITHOUT the window filter so a reviewer can preview a
        // not-yet-live or already-retired variant.
        List<TurChatFlow> group = candidates.stream()
                .map(RouterCandidate::flow)
                .filter(f -> experimentKey.equals(f.getExperimentKey()))
                .toList();
        TurChatFlow forced = forceVariant(group, forcedVariant);
        if (forced == null) {
            log.warn("[A/B] Experiment '{}': forced variant label '{}' not found among {} "
                    + "arm(s) — falling back to normal assignment",
                    experimentKey, forcedVariant, group.size());
            return Optional.empty();
        }
        Optional<ChatFlowGraph> forcedGraph = forced.getId().equals(routerFlow.getId())
                ? Optional.of(routerGraph)
                : parseGraph(forced);
        if (forcedGraph.isEmpty()) {
            log.warn("[A/B] Experiment '{}': forced variant '{}' has an unparseable graph "
                    + "— falling back to normal assignment", experimentKey, forcedVariant);
            return Optional.empty();
        }
        log.info("[A/B] Experiment '{}': FORCED variant '{}' (flow {}) via _ab_variant "
                + "— bypassing hash/bandit + schedule window",
                experimentKey, forced.getVariantLabel(), forced.getId());
        chatAnalyticsService.recordExperimentAssignment(conversationId,
                experimentKey, forced.getVariantLabel());
        return Optional.of(new AbResolved(forced, forcedGraph.get()));
    }

    /**
     * Assigns one of the in-window {@code variants} via Thompson sampling (T70,
     * when any arm is bandit-enabled) or the deterministic hash, recording the
     * assignment. Falls back to the router pick when the assigned graph won't parse.
     */
    private AbResolved assignExperimentVariant(List<TurChatFlow> variants, TurChatFlow routerFlow,
            ChatFlowGraph routerGraph, String experimentKey, String conversationId) {
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
        TurChatFlowState saved = saveState(prior);
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
        saveState(walked);
    }

    record RouterCandidate(TurChatFlow flow, ChatFlowGraph graph) {
    }

    /**
     * Procedural pre-route — thin static entry point delegating to
     * {@link TurChatFlowTriggerRouter#tryProceduralRoute(List, String)} for
     * callers/tests that score a candidate list without a cached per-agent
     * index. The Lucene/BM25 implementation lives in the router (S6539 split).
     */
    static Optional<String> tryProceduralRoute(List<RouterCandidate> candidates,
            String userMessage) {
        return TurChatFlowTriggerRouter.tryProceduralRoute(candidates, userMessage);
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
     * Procedural-router delegation. The Lucene/BM25 trigger-matching subsystem
     * lives in {@link TurChatFlowTriggerRouter} (S6539 split); these thin
     * delegators preserve the engine's existing call/test surface.
     */
    Optional<String> tryProceduralRoute(String agentId, List<RouterCandidate> candidates,
            String userMessage) {
        return triggerRouter.tryProceduralRoute(agentId, candidates, userMessage);
    }

    Optional<ProceduralOutcome> proceduralRouteExplained(String agentId,
            List<RouterCandidate> candidates, String userMessage) {
        return triggerRouter.proceduralRouteExplained(agentId, candidates, userMessage);
    }

    /**
     * Drops every flow-derived cache on a {@link TurChatFlow} write: the
     * per-agent router index plus the Spring caches that used to be evicted by
     * the (now removed, Block AC / T486) {@code @CacheEvict} on
     * {@code TurChatFlowRepository} — the router-decision cache, the static
     * head/tail prompt-addendum caches, and the T487 parsed-graph read-model
     * cache ({@link TurChatFlowGraphCache#CACHE_NAME}). Invoked by
     * {@link TurChatFlowRouterEvictionListener} on persist/update/remove and at
     * the one bulk-DML delete site, so admin flow edits propagate without
     * restart.
     */
    public void evictFlowDerivedCaches() {
        triggerRouter.evictRouterIndexes();
        clearCache(CACHE_ROUTER_DECISION);
        clearCache(TurChatFlowStaticPromptCache.HEAD_CACHE);
        clearCache(TurChatFlowStaticPromptCache.TAIL_CACHE);
        clearCache(TurChatFlowGraphCache.CACHE_NAME);
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    /** Test-visible accessor for the per-agent index cache size. */
    int routerIndexCacheSize() {
        return triggerRouter.routerIndexCacheSize();
    }

    /**
     * LLM router with manual Spring cache lookup. Keyed by {@code agentId :::
     * sha256(normalized(userMessage))} — same agent + same wording (modulo
     * casing/whitespace) returns the cached decision without the 1-3s LLM
     * round-trip. {@code null} (router said "none") is also cached so
     * repeated "off-topic" messages don't burn LLM calls.
     *
     * <p>The cache is evicted on any {@link TurChatFlow} save/delete via the
     * {@code TurChatFlowRouterEvictionListener} JPA callback, so admin edits to
     * {@code triggerDescription} propagate without restart.
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
        String sys = buildRouterSystemPrompt(candidates);
        String userText = userMessage == null ? "" : userMessage;
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(sys),
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
            return matchRouterReply(reply, candidates);
        } catch (Exception e) {
            log.warn("[FlowEngine] Router call failed: {}", e.getMessage());
            return null;
        }
    }

    /** Builds the router system prompt listing the candidate flows and the allowed reply ids. */
    private String buildRouterSystemPrompt(List<RouterCandidate> candidates) {
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
        return sys.toString();
    }

    /** Resolves the model's free-text reply to a candidate flow id, or null when none match. */
    private String matchRouterReply(String reply, List<RouterCandidate> candidates) {
        String normalized = reply.replaceAll("[^A-Za-z0-9\\-]", " ").trim();
        for (String token : normalized.split("\\s+")) {
            for (RouterCandidate c : candidates) {
                if (token.equalsIgnoreCase(c.flow().getId())) {
                    return c.flow().getId();
                }
            }
        }
        return null;
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
        submission.setCompletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        submission.setUserId(resolveUsername());
        submission.setEndNodeId(currentNodeId);
        // T237 — snapshot the accumulated per-turn node-visit path (null when
        // the opt-in log is off) so the path-aware funnel can compute per-node
        // reached / drop-off from terminal submissions.
        submission.setNodeVisitPath(state.getNodeVisitPath());
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
            if ("functionCall".equals(current.type()) || SCHEDULE_AGENT.equals(current.type())) {
                ChatFlowNode.formatNodeVariantTrace(current, state.getConversationId())
                        .ifPresent(tr -> log.info("[A/B Node Trace] {}", tr));
            }

            WalkStep step = dispatchTransparentNode(state, graph, current, auxModel);
            state = step.state();
            if (step.done()) {
                return state;
            }
        }
        log.warn("[FlowEngine] walkTransparentNodes hit the {}-hop safety cap on conversation '{}'",
                TRANSPARENT_WALK_MAX, state.getConversationId());
        return state;
    }

    /**
     * One step of {@link #walkTransparentNodes}: the (possibly advanced/descended/
     * ascended) state and whether the walk should stop here ({@code done=true}
     * mirrors the old {@code return state}; {@code false} mirrors {@code continue}).
     *
     * @since 2026.3.1
     */
    private record WalkStep(TurChatFlowState state, boolean done) {
    }

    /**
     * Dispatches one transparent node to its handler. A non-transparent
     * (interactive / unknown) node matches nothing and stops the walk.
     *
     * @since 2026.3.1
     */
    private WalkStep dispatchTransparentNode(TurChatFlowState state, ChatFlowGraph graph,
            ChatFlowNode current, ChatModel auxModel) {
        WalkStep step = dispatchStructuralNode(state, graph, current, auxModel);
        if (step == null) {
            step = dispatchExecutorNode(state, graph, current);
        }
        return step != null ? step : new WalkStep(state, true);
    }

    /**
     * Structural transparent nodes (sub-flow descent, switches, slot writes,
     * planning). Returns {@code null} when {@code current} isn't one of these.
     *
     * @since 2026.3.1
     */
    private WalkStep dispatchStructuralNode(TurChatFlowState state, ChatFlowGraph graph,
            ChatFlowNode current, ChatModel auxModel) {
        if ("subFlow".equals(current.type())) {
            return handleSubFlowNode(state, graph, current);
        }
        if ("subFlowSwitch".equals(current.type())) {
            return handleSubFlowSwitchNode(state, graph, current, auxModel);
        }
        if ("persona".equals(current.type())) {
            return handlePersonaNode(state, graph, current);
        }
        if ("slot".equals(current.type())) {
            return handleSlotNode(state, graph, current);
        }
        if ("writeSlot".equals(current.type())) {
            return handleWriteSlotNode(state, graph, current);
        }
        if ("planningStep".equals(current.type())) {
            return handlePlanningStepNode(state, graph, current, auxModel);
        }
        if ("iteratePlan".equals(current.type())) {
            return handleIteratePlanNode(state, graph, current);
        }
        return null;
    }

    /**
     * Executor / control transparent nodes (function call, webhook, scheduled
     * agent, sub-flow ascent, human approval, suspend). Returns {@code null}
     * when {@code current} isn't one of these.
     *
     * @since 2026.3.1
     */
    private WalkStep dispatchExecutorNode(TurChatFlowState state, ChatFlowGraph graph,
            ChatFlowNode current) {
        if ("functionCall".equals(current.type())) {
            return handleFunctionCallNode(state, graph, current);
        }
        if ("webhook".equals(current.type())) {
            return handleWebhookNode(state, graph, current);
        }
        if (SCHEDULE_AGENT.equals(current.type())) {
            return handleScheduleAgentNode(state, graph, current);
        }
        if ("end".equals(current.type()) && state.getParentStateId() != null) {
            return handleSubFlowAscent(state);
        }
        if (HUMAN_APPROVAL.equals(current.type())) {
            return handleHumanApprovalNode(state, graph, current);
        }
        if ("suspend".equals(current.type())) {
            // T121 — `suspend` parks the cursor indefinitely; the walker stops
            // here and the cursor doesn't move until POST /api/chat/resume.
            return new WalkStep(state, true);
        }
        return null;
    }

    private WalkStep handleSubFlowNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        Optional<TurChatFlowState> child = enterSubFlow(state, current);
        if (child.isPresent()) {
            return new WalkStep(child.get(), false);
        }
        // Sub-flow not configured / not found / would be recursive:
        // skip the node like a no-op so the parent flow doesn't stall.
        ChatFlowOps.advanceToFirstEdge(state, graph, current);
        return new WalkStep(saveState(state), false);
    }

    private WalkStep handleSubFlowSwitchNode(TurChatFlowState state, ChatFlowGraph graph,
            ChatFlowNode current, ChatModel auxModel) {
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
            return new WalkStep(child.get(), false);
        }
        log.info("[FlowEngine] subFlowSwitch '{}' no descent — advancing on outgoing edge",
                current.id());
        ChatFlowOps.advanceToFirstEdge(state, graph, current);
        return new WalkStep(saveState(state), false);
    }

    private WalkStep handlePersonaNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        String personaId = current.personaId();
        if (personaId != null && !personaId.isBlank()) {
            setActivePersonaVariable(state, personaId);
        }
        ChatFlowOps.advanceToFirstEdge(state, graph, current);
        return new WalkStep(saveState(state), false);
    }

    private WalkStep handleSlotNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        ChatFlowOps.applySlotNode(state, current);
        ChatFlowOps.advanceToFirstEdge(state, graph, current);
        state = saveState(state);
        // SSE notification: a flow slot write is functionally identical to
        // slots.set / writeSlot — emit the full merged map so subscribers
        // don't have to special-case the source.
        slotEventBus.publish(state.getConversationId(),
                listSlotsForConversation(state.getConversationId()).slots());
        return new WalkStep(state, false);
    }

    private WalkStep handleWriteSlotNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        ChatFlowOps.applyWriteSlotNode(state, current);
        ChatFlowOps.advanceToFirstEdge(state, graph, current);
        state = saveState(state);
        slotEventBus.publish(state.getConversationId(),
                listSlotsForConversation(state.getConversationId()).slots());
        return new WalkStep(state, false);
    }

    private WalkStep handlePlanningStepNode(TurChatFlowState state, ChatFlowGraph graph,
            ChatFlowNode current, ChatModel auxModel) {
        ChatFlowOps.applyPlanningStepNode(state, current, auxModel);
        ChatFlowOps.advanceToFirstEdge(state, graph, current);
        state = saveState(state);
        slotEventBus.publish(state.getConversationId(),
                listSlotsForConversation(state.getConversationId()).slots());
        return new WalkStep(state, false);
    }

    private WalkStep handleIteratePlanNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        String planSlot = planSlotKeyOf(current);
        Map<String, String> variables = ChatFlowOps.readVariables(state);
        List<ChatFlowOps.PlanItem> plan = ChatFlowOps.parsePlan(variables.get(planSlot));
        Optional<ChatFlowOps.PlanItem> next = ChatFlowOps.firstPendingItem(plan);
        if (next.isEmpty()) {
            ChatFlowOps.clearPlanIterationMarkers(state);
            ChatFlowOps.advanceToFirstEdge(state, graph, current);
            state = saveState(state);
            slotEventBus.publish(state.getConversationId(),
                    listSlotsForConversation(state.getConversationId()).slots());
            return new WalkStep(state, false);
        }
        ChatFlowOps.PlanItem item = next.get();
        ChatFlowOps.setPlanIterationMarkers(state, item);
        state = saveState(state);
        Optional<TurChatFlowState> child = enterSubFlowById(state, current.subFlowId(), current);
        if (child.isPresent()) {
            log.info("[FlowEngine] iteratePlan '{}' → item '{}' ('{}') → body subFlow '{}'",
                    current.id(), item.id(), truncate(item.title(), 60), current.subFlowId());
            return new WalkStep(child.get(), false);
        }
        // No body sub-flow to descend into — complete the item in place
        // (mark_done / remove) so the iteration advances instead of looping
        // on the same pending item.
        log.info("[FlowEngine] iteratePlan '{}' has no usable body subFlow — completing item '{}' in place",
                current.id(), item.id());
        ChatFlowOps.completePlanItem(state, planSlot, item.id(), current.completionMode());
        state = saveState(state);
        slotEventBus.publish(state.getConversationId(),
                listSlotsForConversation(state.getConversationId()).slots());
        return new WalkStep(state, false);
    }

    private WalkStep handleFunctionCallNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        TurFunctionCallNodeExecutor.ExecutionResult fnResult =
                functionCallExecutor.execute(state, current);
        if (!fnResult.ok() && Boolean.TRUE.equals(current.continueOnFailure())) {
            ChatFlowOps.advanceOnFailure(state, graph, current);
        } else {
            ChatFlowOps.advanceToFirstEdge(state, graph, current);
        }
        state = saveState(state);
        slotEventBus.publish(state.getConversationId(),
                listSlotsForConversation(state.getConversationId()).slots());
        return new WalkStep(state, false);
    }

    private WalkStep handleWebhookNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        TurChatWebhookNodeExecutor.ExecutionResult whResult =
                webhookNodeExecutor.execute(state, current);
        if (!whResult.ok() && Boolean.TRUE.equals(current.continueOnFailure())) {
            ChatFlowOps.advanceOnFailure(state, graph, current);
        } else {
            ChatFlowOps.advanceToFirstEdge(state, graph, current);
        }
        return new WalkStep(saveState(state), false);
    }

    private WalkStep handleScheduleAgentNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        TurScheduleAgentNodeExecutor.Outcome outcome =
                scheduleAgentExecutor.execute(state, current);
        switch (outcome) {
            case WAITING_FIRED -> {
                state = saveState(state);
                // Fresh enqueue → publish so SSE subscribers (chat UI's waiting
                // indicator, slot inspector) see the
                // __scheduleAgent_pending_<nodeId> marker the executor wrote.
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
                return new WalkStep(state, true);
            }
            case WAITING_POLL -> {
                // Markers already set on a previous turn — no slot changed, so
                // do NOT republish. Re-publishing would feed the auto-resume
                // listener → resumeParkedScheduleAgents → re-enter here →
                // WAITING_POLL again → publish again, looping forever.
                return new WalkStep(state, true);
            }
            case COMPLETED -> {
                ChatFlowOps.advanceToFirstEdge(state, graph, current);
                state = saveState(state);
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
            }
            case TIMEOUT -> {
                TurScheduleAgentNodeExecutor.advanceOnTimeout(state, graph, current);
                state = saveState(state);
                slotEventBus.publish(state.getConversationId(),
                        listSlotsForConversation(state.getConversationId()).slots());
            }
            case FAILED -> {
                if (Boolean.TRUE.equals(current.continueOnFailure())) {
                    ChatFlowOps.advanceOnFailure(state, graph, current);
                } else {
                    ChatFlowOps.advanceToFirstEdge(state, graph, current);
                }
                state = saveState(state);
            }
        }
        return new WalkStep(state, false);
    }

    private WalkStep handleSubFlowAscent(TurChatFlowState state) {
        TurChatFlowState parent = ascendFromSubFlow(state);
        if (parent == null) {
            return new WalkStep(state, true);
        }
        return new WalkStep(parent, false);
    }

    private WalkStep handleHumanApprovalNode(TurChatFlowState state, ChatFlowGraph graph, ChatFlowNode current) {
        // T119 — first entry raises a pending-approval record + fires a
        // notification; onEnter returns true only once the operator's decision
        // has landed in the approval slot, then the engine advances.
        if (humanApprovalNodeExecutor.onEnter(state, current)) {
            ChatFlowOps.advanceToFirstEdge(state, graph, current);
            return new WalkStep(saveState(state), false);
        }
        return new WalkStep(state, true);
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
        // findByIdInitialized (JPQL), not findById: the sub-flow may already be
        // an uninitialized LAZY proxy in the persistence context, and it is
        // stored on the child state + read outside the session by callers.
        Optional<TurChatFlow> subFlowOpt = chatFlowRepository.findByIdInitialized(subFlowId);
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
        TurChatFlowState saved = saveState(child);
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
            TurChatFlowState saved = saveState(parent);
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
        return saveState(parent);
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
