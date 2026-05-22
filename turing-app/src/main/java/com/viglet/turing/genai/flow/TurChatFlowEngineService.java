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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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
     * Minimum number of overlapping content words between user message and a
     * flow's {@code triggerDescription} required for the procedural pre-route
     * to commit. Below this threshold, the decision falls back to the LLM
     * router. Two tokens is enough signal in practice (e.g. "plano" +
     * "carreira" overlapping the trigger description's keywords) without
     * tripping on accidental single-word matches.
     */
    private static final int PROCEDURAL_MIN_OVERLAP = 2;

    /**
     * Minimum ratio by which the top-scoring flow must beat the runner-up to
     * commit procedurally. When 1.5×, an ambiguous tie (best=3, second=2)
     * still falls back to the LLM — only confident wins (best=3, second≤2)
     * bypass it. Higher ratio = less procedural usage = more correctness;
     * lower ratio = more speed.
     */
    private static final double PROCEDURAL_DOMINANCE_RATIO = 1.5;

    /**
     * Lucene analyzer used to tokenize the user message and each flow's
     * {@code triggerDescription} for the procedural pre-route. Picked
     * {@link PortugueseAnalyzer} because the platform's primary audience is
     * PT-BR and it ships with:
     * <ul>
     *   <li>Lowercase + diacritic-aware normalization out of the box;</li>
     *   <li>~120 Portuguese stopwords (que, com, para, …) — wider than the
     *       hand-rolled list we used before;</li>
     *   <li>Portuguese light stemming so {@code "planos"}, {@code "plano"}
     *       and {@code "planejar"} collapse to the same stem — overlap
     *       scoring now matches morphological variants the previous string-
     *       equality check missed.</li>
     * </ul>
     * English trigger descriptions still tokenize cleanly (no EN stopword
     * filtering, but the loss-bearing keywords pass through untouched, so
     * overlap still works).
     *
     * <p>Lucene {@link Analyzer}s are thread-safe — single instance shared
     * across all routing calls.
     *
     * @since 2026.2.8
     */
    private static final Analyzer FLOW_ROUTER_ANALYZER = new PortugueseAnalyzer();

    private final TurChatFlowStateRepository stateRepository;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowSubmissionRepository submissionRepository;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final TurChatSlotEventBus slotEventBus;
    private final CacheManager cacheManager;
    private final Map<TurChatFlowGuardrailMethod, TurChatFlowGuardrailStrategy> strategyByMethod;

    public TurChatFlowEngineService(TurChatFlowStateRepository stateRepository,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowSubmissionRepository submissionRepository,
            TurChatAnalyticsService chatAnalyticsService,
            TurChatSlotEventBus slotEventBus,
            CacheManager cacheManager,
            List<TurChatFlowGuardrailStrategy> strategies) {
        this.stateRepository = stateRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.submissionRepository = submissionRepository;
        this.chatAnalyticsService = chatAnalyticsService;
        this.slotEventBus = slotEventBus;
        this.cacheManager = cacheManager;
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
        return resolveStrategy(flow).buildSystemPromptAddendum(node.get(), state, graph);
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
        AdvanceContext ctx = new AdvanceContext(state, graph, nodeOpt.get(),
                userMessage, assistantMessage, auxiliaryModel);
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
        }
        return new AdvanceResult(leaf, override);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
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
            String variantLabel) {
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
            return new ConversationStateDto(conversationId, null, null, null, null, null, null);
        }
        List<TurChatFlowState> states = stateRepository.findByConversationId(conversationId);
        if (states.isEmpty()) {
            return new ConversationStateDto(conversationId, null, null, null, null, null, null);
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
        return new ConversationStateDto(
                conversationId,
                flow == null ? null : flow.getId(),
                flow == null ? null : flow.getName(),
                leaf.getCurrentNodeId(),
                flow == null || flow.getGuardrailMethod() == null
                        ? null : flow.getGuardrailMethod().name(),
                flow == null ? null : flow.getExperimentKey(),
                flow == null ? null : flow.getVariantLabel());
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

        // 3a) Procedural pre-route — fast, deterministic, no LLM. When the
        //     user message has 2+ content words overlapping a flow's
        //     triggerDescription AND that flow dominates the runner-up by
        //     1.5×, commit straight to it. Cuts ~1-3s of LLM latency from
        //     freshly-triggered turns where the trigger is obvious. Falls
        //     back to the LLM router when the procedural pass is ambiguous
        //     or no description has enough keyword density.
        String proceduralPick = tryProceduralRoute(candidates, lastUserMessage).orElse(null);
        final String pickedId;
        if (proceduralPick != null) {
            log.info("[FlowEngine] Procedural router picked '{}' (LLM skipped)", proceduralPick);
            pickedId = proceduralPick;
        } else {
            // 3b) Cached LLM router. Same {agentId, userMessage} pair returns
            //     a cached decision on repeated calls (e.g. canned UI prompts,
            //     test runs, retries) so the LLM round-trip pays off once per
            //     unique message per agent. Cache is wiped on any
            //     TurChatFlow.save/delete so admin edits propagate.
            pickedId = askRouterCached(agent.getId(), routerModel, candidates, lastUserMessage);
        }
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
        AbResolved ab = resolveAbVariant(picked.get(), candidates, conversationId);
        final TurChatFlow flow = ab.flow();
        final ChatFlowGraph pickedGraph = ab.graph();

        if (completedFlowIds.contains(flow.getId())
                && flow.getTriggerMode() == TurChatFlowTriggerMode.ALWAYS) {
            stateRepository.findByConversationIdAndFlow_Id(conversationId, flow.getId())
                    .ifPresent(prior -> resetForAlwaysRetrigger(prior, pickedGraph));
            stateRepository.flush();
        }

        Optional<TurChatFlowState> newState = loadOrInitState(conversationId, flow, pickedGraph);
        // loadOrInitState walks transparent nodes; the leaf may belong to a
        // sub-flow rather than the picked root flow. Align the FlowSelection
        // with the leaf so the prompt addendum and strategy run against the
        // correct graph.
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
            List<RouterCandidate> candidates, String conversationId) {
        TurChatFlow routerFlow = picked.flow();
        ChatFlowGraph routerGraph = picked.graph();
        String experimentKey = routerFlow.getExperimentKey();
        if (experimentKey == null || experimentKey.isBlank()) {
            return new AbResolved(routerFlow, routerGraph);
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
            TurChatFlow assigned = assignVariant(variants, conversationId, experimentKey);
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

    private record RouterCandidate(TurChatFlow flow, ChatFlowGraph graph) {
    }

    /**
     * Procedural pre-route — token-overlap score between the user message and
     * each candidate's {@code triggerDescription}. Returns the picked flow id
     * when one candidate dominates clearly (≥{@link #PROCEDURAL_MIN_OVERLAP}
     * overlapping tokens AND ≥{@link #PROCEDURAL_DOMINANCE_RATIO}× the next
     * best); otherwise {@link Optional#empty()} so the caller can fall back
     * to the LLM router.
     *
     * <p>Stopword set ({@link #STOPWORDS}) covers PT and EN connectives —
     * works on bilingual deployments without configuration. Doesn't help for
     * languages outside that set; add stopwords as needed.
     *
     * @since 2026.2.8
     */
    private Optional<String> tryProceduralRoute(List<RouterCandidate> candidates,
            String userMessage) {
        if (userMessage == null || userMessage.isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }
        Set<String> userTokens = tokenize(userMessage);
        if (userTokens.size() < PROCEDURAL_MIN_OVERLAP) {
            return Optional.empty();
        }
        int bestScore = 0;
        int secondScore = 0;
        String bestId = null;
        for (RouterCandidate c : candidates) {
            Set<String> flowTokens = tokenize(c.flow().getTriggerDescription());
            if (flowTokens.isEmpty()) {
                continue;
            }
            Set<String> overlap = new HashSet<>(userTokens);
            overlap.retainAll(flowTokens);
            int score = overlap.size();
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                bestId = c.flow().getId();
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        if (bestScore < PROCEDURAL_MIN_OVERLAP) {
            return Optional.empty();
        }
        if (secondScore > 0
                && (double) bestScore < secondScore * PROCEDURAL_DOMINANCE_RATIO) {
            log.debug("[FlowEngine] Procedural router undecided "
                    + "(best={}, second={}, ratio<{}) — falling back to LLM",
                    bestScore, secondScore, PROCEDURAL_DOMINANCE_RATIO);
            return Optional.empty();
        }
        return Optional.ofNullable(bestId);
    }

    /**
     * Tokenize through Lucene's {@link #FLOW_ROUTER_ANALYZER} — applies
     * lowercase, diacritic folding, Portuguese stopword filtering and light
     * stemming in a single pipeline. Returns the deduplicated set of stems
     * (e.g. "plano de carreira" → {@code {plan, carreir}}, "uns planos para
     * carreiras" → same {@code {plan, carreir}}). Empty for blank/null
     * input. The {@link IOException} declared by {@code TokenStream} is
     * impossible in practice with the in-memory string source — the catch
     * is for API compliance; we log and degrade to an empty set so the
     * caller falls back to the LLM router rather than crashing.
     */
    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        try (TokenStream stream = FLOW_ROUTER_ANALYZER.tokenStream("text", text)) {
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
    private String askRouterCached(String agentId, ChatModel routerModel,
            List<RouterCandidate> candidates, String userMessage) {
        Cache cache = cacheManager.getCache(CACHE_ROUTER_DECISION);
        String key = buildRouterCacheKey(agentId, userMessage);
        if (cache != null && key != null) {
            Cache.ValueWrapper hit = cache.get(key);
            if (hit != null) {
                Object cached = hit.get();
                log.info("[FlowEngine] Router cache HIT for agent='{}' key='{}' → '{}' (LLM skipped)",
                        agentId, key, cached);
                return (String) cached;
            }
            log.info("[FlowEngine] Router cache MISS for agent='{}' key='{}' — calling LLM",
                    agentId, key);
        }
        String picked = askRouter(routerModel, candidates, userMessage);
        if (cache != null && key != null) {
            cache.put(key, picked);
        }
        return picked;
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
            ChatFlowNode current = currentOpt.get();

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

            if ("end".equals(current.type()) && state.getParentStateId() != null) {
                TurChatFlowState parent = ascendFromSubFlow(state);
                if (parent == null) {
                    return state;
                }
                state = parent;
                continue;
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
        String subFlowId = subFlowNode.subFlowId();
        if (subFlowId == null || subFlowId.isBlank()) {
            log.warn("[FlowEngine] Sub Flow node '{}' has no subFlowId — skipping",
                    subFlowNode.id());
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
                    subFlowId, subFlowNode.id());
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
        ChatFlowOps.advanceToFirstEdge(parent, parentGraph, subFlowNodeOpt.get());
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
