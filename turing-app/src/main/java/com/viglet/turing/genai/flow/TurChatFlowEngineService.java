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
import java.util.List;
import java.util.Map;
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
    private final Map<TurChatFlowGuardrailMethod, TurChatFlowGuardrailStrategy> strategyByMethod;

    public TurChatFlowEngineService(TurChatFlowStateRepository stateRepository,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowSubmissionRepository submissionRepository,
            List<TurChatFlowGuardrailStrategy> strategies) {
        this.stateRepository = stateRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.submissionRepository = submissionRepository;
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
     * a fresh one anchored at the graph's first interactive node.
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
            return existing;
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
        return Optional.of(stateRepository.save(created));
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
        return resolveStrategy(flow).buildSystemPromptAddendum(node.get(), state);
    }

    /**
     * Runs one chat turn through the strategy bound to {@code flow}. The
     * strategy may mutate {@code state} (current node, variables) and may
     * return an override string the executor should display in place of the
     * raw LLM reply. The engine then persists the state and, when the turn
     * landed on a terminal node, records an immutable submission row.
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
        AdvanceContext ctx = new AdvanceContext(state, graph, nodeOpt.get(),
                userMessage, assistantMessage, auxiliaryModel);
        String override = resolveStrategy(flow).advance(ctx);
        // Condition nodes are transparent to the chat — the LLM never sees them
        // as a "Goal" because they have none. Walk through any chain of
        // conditions in the same turn so the next chat round lands on the next
        // interactive node.
        ChatFlowOps.walkThroughConditions(state, graph, auxiliaryModel);
        TurChatFlowState saved = saveAndMaybeRecord(flow, state, graph, previousNodeId);
        return new AdvanceResult(saved, override);
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

        // 1) Continuation. We resume an in-progress state UNLESS it is a fresh,
        // empty ALWAYS-mode state — those are most likely zombie rows left by a
        // previous test session and would force the user to "re-enter" the flow
        // from wherever it stalled. Fresh+empty+ALWAYS rows get dropped here so
        // the router (step 3) can pick the right flow for the current message.
        for (TurChatFlowState state : existingStates) {
            Optional<ChatFlowGraph> graphOpt = parseGraph(state.getFlow());
            if (graphOpt.isEmpty()) {
                continue;
            }
            Optional<ChatFlowNode> currentOpt = graphOpt.get().nodeById(state.getCurrentNodeId());
            if (currentOpt.isEmpty() || "end".equals(currentOpt.get().type())) {
                continue;
            }
            if (isStaleAlwaysState(state, currentOpt.get())) {
                log.info("[FlowEngine] Dropping stale ALWAYS state on flow '{}' (no progress yet)",
                        state.getFlow().getId());
                stateRepository.delete(state);
                stateRepository.flush();
                continue;
            }
            log.info("[FlowEngine] Continuation: flow '{}' is in progress for conversation '{}'",
                    state.getFlow().getId(), conversationId);
            return Optional.of(new FlowSelection(state.getFlow(), graphOpt.get(), state));
        }

        // 2) Build candidate list
        Set<String> completedFlowIds = new HashSet<>();
        for (TurChatFlowState state : existingStates) {
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
        return newState.map(s -> new FlowSelection(flow, picked.get().graph(), s));
    }

    private record RouterCandidate(TurChatFlow flow, ChatFlowGraph graph) {
    }

    /**
     * A state is "stale" when its flow runs in {@code ALWAYS} mode and no
     * variables have been collected yet — a zombie row left over from an
     * earlier test that aborted before user input. We drop these so the
     * router can pick the right flow for the current message instead of
     * silently resuming where the previous run stalled.
     */
    private static boolean isStaleAlwaysState(TurChatFlowState state, ChatFlowNode currentNode) {
        if (state.getFlow() == null
                || state.getFlow().getTriggerMode() != TurChatFlowTriggerMode.ALWAYS) {
            return false;
        }
        if (currentNode == null || "end".equals(currentNode.type())) {
            return false;
        }
        String json = state.getVariablesJson();
        return json == null
                || json.isBlank()
                || "{}".equals(json.trim())
                || ChatFlowOps.readVariablesJson(json).isEmpty();
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

    // ─────────────────────────── State + submission lifecycle ───────────────────────────

    /**
     * Saves the (mutated) state and emits a submission row when the strategy
     * just transitioned the conversation onto a terminal node.
     */
    private TurChatFlowState saveAndMaybeRecord(TurChatFlow flow,
            TurChatFlowState state,
            ChatFlowGraph graph,
            String previousNodeId) {
        TurChatFlowState saved = stateRepository.save(state);
        recordSubmissionIfTerminal(flow, saved, graph, previousNodeId);
        return saved;
    }

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
    }

    private static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
