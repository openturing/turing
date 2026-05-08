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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.strategy.AdvanceContext;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy;
import com.viglet.turing.persistence.dto.agent.TurChatFlowSubmissionDto;
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

    private final TurChatFlowStateRepository stateRepository;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowSubmissionRepository submissionRepository;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final Map<TurChatFlowGuardrailMethod, TurChatFlowGuardrailStrategy> strategyByMethod;

    public TurChatFlowEngineService(TurChatFlowStateRepository stateRepository,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowSubmissionRepository submissionRepository,
            TurChatAnalyticsService chatAnalyticsService,
            List<TurChatFlowGuardrailStrategy> strategies) {
        this.stateRepository = stateRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.submissionRepository = submissionRepository;
        this.chatAnalyticsService = chatAnalyticsService;
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
     */
    public record FlowSelection(TurChatFlow flow, ChatFlowGraph graph, TurChatFlowState state) {
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
     */
    public AdvanceResult advance(TurChatFlow flow,
            TurChatFlowState state,
            ChatFlowGraph graph,
            String userMessage,
            String assistantMessage,
            ChatModel auxiliaryModel) {
        if (state == null || graph == null) {
            return AdvanceResult.of(state);
        }
        Optional<ChatFlowNode> nodeOpt = currentNode(state, graph);
        if (nodeOpt.isEmpty()) {
            return AdvanceResult.of(state);
        }
        String previousNodeId = state.getCurrentNodeId();
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
        TurChatFlowState leaf = walkTransparentNodes(state, auxiliaryModel);
        if (leaf != state) {
            leaf = stateRepository.save(leaf);
        }
        // Record a submission only when the ROOT flow has just terminated —
        // sub-flow ends pop instead of recording.
        if (leaf.getParentStateId() == null) {
            ChatFlowGraph leafGraph = parseGraph(leaf.getFlow()).orElse(graph);
            recordSubmissionIfTerminal(leaf.getFlow(), leaf, leafGraph, previousNodeId);
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
            return Optional.of(new FlowSelection(state.getFlow(), graphOpt.get(), state));
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

        // 3) Ask the router LLM
        String pickedId = askRouter(routerModel, candidates, lastUserMessage);
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

        // 4) ALWAYS re-trigger: drop prior end-state so the flow restarts.
        TurChatFlow flow = picked.get().flow();
        if (completedFlowIds.contains(flow.getId())
                && flow.getTriggerMode() == TurChatFlowTriggerMode.ALWAYS) {
            stateRepository.findByConversationIdAndFlow_Id(conversationId, flow.getId())
                    .ifPresent(stateRepository::delete);
            stateRepository.flush();
        }

        Optional<TurChatFlowState> newState = loadOrInitState(conversationId, flow, picked.get().graph());
        // loadOrInitState walks transparent nodes; the leaf may belong to a
        // sub-flow rather than the picked root flow. Align the FlowSelection
        // with the leaf so the prompt addendum and strategy run against the
        // correct graph.
        return newState.map(s -> {
            TurChatFlow activeFlow = s.getFlow();
            ChatFlowGraph activeGraph = activeFlow.getId().equals(flow.getId())
                    ? picked.get().graph()
                    : parseGraph(activeFlow).orElse(picked.get().graph());
            return new FlowSelection(activeFlow, activeGraph, s);
        });
    }

    private record RouterCandidate(TurChatFlow flow, ChatFlowGraph graph) {
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
            // Conditions are a no-LLM transition: re-walk every iteration to
            // collapse `condition → condition → …` chains created by descent.
            ChatFlowOps.walkThroughConditions(state, graph, auxModel);
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
     * Walks {@code state}'s parent chain and returns true if any ancestor
     * (including {@code state} itself) already runs the flow identified by
     * {@code flowId}. Prevents direct or indirect recursion.
     */
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
