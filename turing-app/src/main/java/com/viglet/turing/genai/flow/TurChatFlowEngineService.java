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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Phase B runtime for chat flows: parses the persisted graph, persists which
 * node a given conversation is on, augments the system prompt for the current
 * node, and advances state after the LLM replies.
 *
 * <p>Behavior dispatches by {@link TurChatFlow#getGuardrailMethod()}. Step 1
 * ships {@link TurChatFlowGuardrailMethod#HEURISTIC}: prompt-only contract +
 * regex/keyword heuristics on user input — no extra LLM calls. Future
 * methodologies (LLM judge, structured output) will add new branches without
 * touching this contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Service
public class TurChatFlowEngineService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> VARIABLES_TYPE = new TypeReference<>() {
    };

    /** Validation patterns understood by the HEURISTIC strategy. */
    private static final Map<String, Pattern> VALIDATION_PATTERNS = Map.of(
            "email", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
            "phone", Pattern.compile("\\+?[0-9][0-9 ()\\-]{6,}\\b"),
            "url", Pattern.compile("https?://[\\w.\\-/?#=&%+]+", Pattern.CASE_INSENSITIVE),
            "number", Pattern.compile("-?\\d+(?:[.,]\\d+)?"),
            "date", Pattern.compile("\\b\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}\\b"));

    private final TurChatFlowStateRepository stateRepository;
    private final TurChatFlowRepository chatFlowRepository;

    public TurChatFlowEngineService(TurChatFlowStateRepository stateRepository,
            TurChatFlowRepository chatFlowRepository) {
        this.stateRepository = stateRepository;
        this.chatFlowRepository = chatFlowRepository;
    }

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
     * strategy ever sets {@code assistantOverride}; other strategies leave
     * it null and the original reply flows through unchanged.
     */
    public record AdvanceResult(TurChatFlowState state, String assistantOverride) {
        public static AdvanceResult of(TurChatFlowState state) {
            return new AdvanceResult(state, null);
        }
    }

    /**
     * Decides which flow (if any) should govern the current chat turn for an
     * agent. Resolution order:
     *
     * <ol>
     *   <li>If any flow already has an in-progress state for this conversation
     *       (i.e. its current node is not the end node), continue with it —
     *       finishing what's started always wins.</li>
     *   <li>Otherwise, list flows that:
     *     <ul>
     *       <li>belong to this agent and are enabled,</li>
     *       <li>have a non-blank {@code triggerDescription},</li>
     *       <li>have a parseable graph,</li>
     *       <li>are eligible: {@code ALWAYS} flows always are; {@code ONCE}
     *           flows only when no completed state exists for the
     *           conversation.</li>
     *     </ul>
     *     If none qualify, returns empty.</li>
     *   <li>Ask the LLM router (caller-provided {@link ChatModel}) to pick a
     *       flow id from the candidates given the user's last message. If the
     *       router replies "none" or anything unrecognized, returns empty.</li>
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

        // 1) Continuation: any flow whose state isn't at end takes priority.
        for (TurChatFlowState state : existingStates) {
            Optional<ChatFlowGraph> graphOpt = parseGraph(state.getFlow());
            if (graphOpt.isEmpty()) {
                continue;
            }
            Optional<ChatFlowNode> currentOpt = graphOpt.get().nodeById(state.getCurrentNodeId());
            if (currentOpt.isPresent() && !"end".equals(currentOpt.get().type())) {
                log.info("[FlowEngine] Continuation: flow '{}' is in progress for conversation '{}'",
                        state.getFlow().getId(), conversationId);
                return Optional.of(new FlowSelection(state.getFlow(), graphOpt.get(), state));
            }
        }

        // 2) Build candidate list (eligible flows with trigger descriptions).
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

        // 3) Ask the router LLM which (if any) flow matches the message.
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

        // 4) For ALWAYS mode with a previous END state, drop it so the new run
        //    starts at the first interactive node.
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
     * Single-shot LLM call that asks the router to pick a flow id from the
     * candidate list (or "none"). Returns the picked id, or null on "none" /
     * any failure.
     */
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
                    .append(" | name: ").append(safe(c.flow().getName()))
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
            // Be lenient: routers sometimes wrap in quotes or backticks, or add
            // trailing punctuation. Take the first alphanumeric+dash token that
            // matches a candidate id.
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
     * a fresh one anchored at the graph's START node. Returns empty if the
     * flow has no graph or no Start node.
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
        // Anchor the new state on the first node *after* START (if any) — the
        // Start node is structural, not interactive, so the user's first
        // turn should already be guarded by the first real step.
        ChatFlowNode anchor = firstInteractiveNode(graph, startNode.get()).orElse(startNode.get());
        TurChatFlowState created = new TurChatFlowState();
        created.setConversationId(conversationId);
        created.setFlow(flow);
        created.setCurrentNodeId(anchor.id());
        created.setVariablesJson("{}");
        return Optional.of(stateRepository.save(created));
    }

    /**
     * Drops the persisted state for {@code (conversationId, flowId)}. The
     * next chat turn will rebuild it from the START node.
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

    /** Returns the node currently active in the conversation, if resolvable. */
    public Optional<ChatFlowNode> currentNode(TurChatFlowState state, ChatFlowGraph graph) {
        if (state == null || graph == null) {
            return Optional.empty();
        }
        return graph.nodeById(state.getCurrentNodeId());
    }

    /**
     * Renders the system prompt addendum for the current node. The exact text
     * depends on {@code flow.getGuardrailMethod()} — step 1 only handles
     * {@link TurChatFlowGuardrailMethod#HEURISTIC}.
     */
    public String buildSystemPromptAddendum(TurChatFlow flow,
            TurChatFlowState state,
            ChatFlowGraph graph) {
        Optional<ChatFlowNode> node = currentNode(state, graph);
        if (node.isEmpty()) {
            return "";
        }
        TurChatFlowGuardrailMethod method = flow.getGuardrailMethod() != null
                ? flow.getGuardrailMethod()
                : TurChatFlowGuardrailMethod.HEURISTIC;
        // HEURISTIC + LLM_JUDGE share the layer-1 prompt contract — LLM_JUDGE
        // just adds a layer-2 verdict call inside `advance`. STRUCTURED_OUTPUT
        // bundles the verdict into the same chat call, so the prompt asks for
        // a single JSON object.
        return switch (method) {
            case HEURISTIC, LLM_JUDGE -> buildAddendumHeuristic(node.get(), state);
            case STRUCTURED_OUTPUT -> buildAddendumStructuredOutput(node.get(), state);
        };
    }

    /**
     * Decides the next node given the latest user/assistant exchange and
     * persists the updated state. Returns the (possibly unchanged) state and,
     * for {@code LLM_JUDGE} strategy, an optional override that replaces the
     * assistant's text when judged off-topic.
     */
    public AdvanceResult advance(TurChatFlow flow,
            TurChatFlowState state,
            ChatFlowGraph graph,
            String userMessage,
            String assistantMessage,
            ChatModel judgeModel) {
        if (state == null || graph == null) {
            return AdvanceResult.of(state);
        }
        TurChatFlowGuardrailMethod method = flow.getGuardrailMethod() != null
                ? flow.getGuardrailMethod()
                : TurChatFlowGuardrailMethod.HEURISTIC;
        return switch (method) {
            case HEURISTIC -> AdvanceResult.of(
                    advanceHeuristic(state, graph, userMessage, assistantMessage));
            case LLM_JUDGE -> advanceLlmJudge(state, graph, userMessage, assistantMessage,
                    judgeModel);
            case STRUCTURED_OUTPUT -> advanceStructuredOutput(state, graph, userMessage,
                    assistantMessage);
        };
    }

    // ─────────────────────────── HEURISTIC strategy ───────────────────────────

    private String buildAddendumHeuristic(ChatFlowNode node, TurChatFlowState state) {
        // Once the flow has reached its END node, drop the guard rail entirely
        // so the user can keep chatting freely with the agent. The captured
        // variables remain in the conversation history, so the LLM still has
        // context if the user follows up about them.
        if ("end".equals(node.type())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== ACTIVE FLOW STEP: ")
                .append(safe(node.label()))
                .append(" ===\n");
        if (node.aiInstruction() != null && !node.aiInstruction().isBlank()) {
            sb.append("Goal: ").append(node.aiInstruction().trim()).append('\n');
        }
        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            sb.append("Collect: ").append(node.outputVariable().trim()).append('\n');
        }
        if (node.validationRule() != null && !node.validationRule().isBlank()
                && !"none".equalsIgnoreCase(node.validationRule())) {
            sb.append("Validate the collected value as: ")
                    .append(node.validationRule().trim()).append('\n');
        }
        Map<String, String> variables = readVariables(state);
        if (!variables.isEmpty()) {
            sb.append("Already collected: ").append(variables).append('\n');
        }
        sb.append("""

                Hard rules:
                - Stay strictly on the goal above. Do not answer off-topic questions.
                - Accept short, bare answers as valid replies. If you asked for a name and the
                  user replies with just "Alexandre", that IS the name — acknowledge it and
                  move on. The same applies to emails, phone numbers, dates, etc. Never ask the
                  same question again when the user already supplied a value, even briefly.
                - If the user deviates, briefly redirect them back to this step.
                - Reply only in the language the user used.
                """);
        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            sb.append("- Do not advance until a valid ")
                    .append(node.outputVariable().trim())
                    .append(" has been collected.\n");
        }
        return sb.toString();
    }

    private TurChatFlowState advanceHeuristic(TurChatFlowState state,
            ChatFlowGraph graph,
            String userMessage,
            String assistantMessage) {
        Optional<ChatFlowNode> nodeOpt = currentNode(state, graph);
        if (nodeOpt.isEmpty()) {
            return state;
        }
        ChatFlowNode node = nodeOpt.get();

        // Terminal nodes: nothing to do.
        if ("end".equals(node.type())) {
            return state;
        }

        boolean shouldAdvance;
        Map<String, String> variables = readVariables(state);

        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            String collected = extractValue(node.validationRule(), userMessage);
            if (collected != null) {
                variables.put(node.outputVariable().trim(), collected);
                shouldAdvance = true;
            } else {
                shouldAdvance = false;
            }
        } else {
            // No specific value to collect: advance whenever the assistant
            // produced a substantive reply that mentions something from the
            // node's instruction (very weak signal — fine for step 1).
            shouldAdvance = looksAligned(node.aiInstruction(), assistantMessage);
        }

        if (!shouldAdvance) {
            log.info("[FlowEngine] Staying on node '{}' — no advance signal", node.id());
            writeVariables(state, variables);
            return stateRepository.save(state);
        }

        List<ChatFlowEdge> outgoing = graph.outgoingEdges(node.id());
        if (outgoing.isEmpty()) {
            log.info("[FlowEngine] Node '{}' has no outgoing edges — staying", node.id());
            writeVariables(state, variables);
            return stateRepository.save(state);
        }
        // Step 1 strategy: first outgoing edge wins. Branching by
        // conditionExpression / functionCall comes in later steps.
        ChatFlowEdge nextEdge = outgoing.get(0);
        Optional<ChatFlowNode> targetNode = graph.nodeById(nextEdge.target());
        if (targetNode.isEmpty()) {
            log.warn("[FlowEngine] Edge '{}' points at missing node '{}'",
                    nextEdge.id(), nextEdge.target());
            writeVariables(state, variables);
            return stateRepository.save(state);
        }
        log.info("[FlowEngine] Advancing '{}' → '{}' on conversation '{}'",
                node.id(), targetNode.get().id(), state.getConversationId());
        state.setCurrentNodeId(targetNode.get().id());
        writeVariables(state, variables);
        return stateRepository.save(state);
    }

    // ─────────────────────────── STRUCTURED_OUTPUT strategy ───────────────────────────

    /**
     * System-prompt addendum for STRUCTURED_OUTPUT — instructs the chat LLM
     * to wrap its reply and verdict in a single JSON object so the engine
     * can advance state without a second call.
     */
    private String buildAddendumStructuredOutput(ChatFlowNode node, TurChatFlowState state) {
        if ("end".equals(node.type())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== ACTIVE FLOW STEP: ")
                .append(safe(node.label()))
                .append(" ===\n");
        if (node.aiInstruction() != null && !node.aiInstruction().isBlank()) {
            sb.append("Goal: ").append(node.aiInstruction().trim()).append('\n');
        }
        if (node.outputVariable() != null && !node.outputVariable().isBlank()) {
            sb.append("Collect: ").append(node.outputVariable().trim()).append('\n');
        }
        if (node.validationRule() != null && !node.validationRule().isBlank()
                && !"none".equalsIgnoreCase(node.validationRule())) {
            sb.append("Validate the collected value as: ")
                    .append(node.validationRule().trim()).append('\n');
        }
        Map<String, String> variables = readVariables(state);
        if (!variables.isEmpty()) {
            sb.append("Already collected: ").append(variables).append('\n');
        }
        sb.append("""

                You MUST reply with a SINGLE JSON OBJECT on one line — nothing else, no
                code fences, no prose around it. Schema:

                {
                  "reply": "<the message the user will see, written in their language>",
                  "on_topic": <true|false>,
                  "collected_value": "<value extracted from the user's last message, or null>",
                  "ready_to_advance": <true|false>,
                  "abandoned": <true|false>
                }

                Rules for each field:
                - reply: friendly conversational message addressed to the user, in their
                  language. Keep it natural — never mention internal terms like "step",
                  "goal" or "output variable". Accept short bare answers ("Alexandre",
                  "foo@bar.com") as valid replies and acknowledge them; never ask the same
                  question twice when the user already supplied a value.
                - on_topic: false ONLY when YOUR reply went off-goal. If the USER asked
                  something off-goal but YOUR reply politely brought them back, that is
                  on_topic=true.
                - collected_value: be LIBERAL — bare values count. Return null only when
                  the user truly said nothing of the requested kind.
                - ready_to_advance: true when collected_value is non-null and (when a
                  validation rule applies) matches the rule. For steps without an output
                  variable, true when the goal is clearly addressed.
                - abandoned: true ONLY when the user clearly wants to quit / cancel /
                  skip ("desisto", "cancelar", "no thanks"). When true, write a graceful
                  goodbye in `reply`.

                Example: {"reply":"Prazer, Alexandre! Qual seu e-mail?","on_topic":true,"collected_value":"Alexandre","ready_to_advance":true,"abandoned":false}
                """);
        return sb.toString();
    }

    /**
     * Parses the structured-output JSON the chat LLM emitted, applies the
     * verdict to state, and exposes {@code reply} as the assistant override
     * (so the user sees the natural-language message, not the raw JSON).
     */
    private AdvanceResult advanceStructuredOutput(TurChatFlowState state,
            ChatFlowGraph graph,
            String userMessage,
            String assistantMessage) {
        Optional<ChatFlowNode> nodeOpt = currentNode(state, graph);
        if (nodeOpt.isEmpty()) {
            return AdvanceResult.of(state);
        }
        ChatFlowNode node = nodeOpt.get();
        if ("end".equals(node.type())) {
            return AdvanceResult.of(state);
        }

        StructuredVerdict verdict = parseStructuredVerdict(assistantMessage);
        if (verdict == null) {
            // LLM didn't follow the JSON contract this turn — degrade to
            // heuristic advance so the conversation keeps moving. Original
            // raw text passes through; logged so it can be diagnosed.
            log.warn("[FlowEngine] STRUCTURED_OUTPUT: could not parse JSON, falling back to heuristic");
            return AdvanceResult.of(advanceHeuristic(state, graph, userMessage, assistantMessage));
        }

        Map<String, String> variables = readVariables(state);
        String reply = verdict.reply() == null || verdict.reply().isBlank()
                ? assistantMessage
                : verdict.reply().trim();

        if (verdict.abandoned()) {
            log.info("[FlowEngine] STRUCTURED_OUTPUT: user abandoned on node '{}'", node.id());
            writeVariables(state, variables);
            transitionToEnd(state, graph);
            stateRepository.save(state);
            return new AdvanceResult(state, reply);
        }

        if (verdict.collectedValue() != null && !verdict.collectedValue().isBlank()
                && node.outputVariable() != null && !node.outputVariable().isBlank()) {
            variables.put(node.outputVariable().trim(), verdict.collectedValue().trim());
        }

        if (!verdict.readyToAdvance()) {
            log.info("[FlowEngine] STRUCTURED_OUTPUT: stay on node '{}'", node.id());
            writeVariables(state, variables);
            stateRepository.save(state);
            return new AdvanceResult(state, reply);
        }

        List<ChatFlowEdge> outgoing = graph.outgoingEdges(node.id());
        if (outgoing.isEmpty()) {
            writeVariables(state, variables);
            stateRepository.save(state);
            return new AdvanceResult(state, reply);
        }
        Optional<ChatFlowNode> targetNode = graph.nodeById(outgoing.get(0).target());
        if (targetNode.isEmpty()) {
            writeVariables(state, variables);
            stateRepository.save(state);
            return new AdvanceResult(state, reply);
        }
        log.info("[FlowEngine] STRUCTURED_OUTPUT: advancing '{}' → '{}'",
                node.id(), targetNode.get().id());
        state.setCurrentNodeId(targetNode.get().id());
        writeVariables(state, variables);
        stateRepository.save(state);
        return new AdvanceResult(state, reply);
    }

    /** Fields extracted from the structured-output JSON. */
    private record StructuredVerdict(String reply, boolean onTopic, String collectedValue,
            boolean readyToAdvance, boolean abandoned) {
    }

    private static StructuredVerdict parseStructuredVerdict(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = stripFences(raw);
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart < 0 || braceEnd <= braceStart) {
            return null;
        }
        json = json.substring(braceStart, braceEnd + 1);
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            String reply = parsed.get("reply") instanceof String s ? s : null;
            boolean onTopic = parsed.get("on_topic") instanceof Boolean b ? b : true;
            String collected = parsed.get("collected_value") instanceof String s ? s : null;
            boolean ready = parsed.get("ready_to_advance") instanceof Boolean b && b;
            boolean abandoned = parsed.get("abandoned") instanceof Boolean b && b;
            return new StructuredVerdict(reply, onTopic, collected, ready, abandoned);
        } catch (JacksonException e) {
            log.warn("[FlowEngine] STRUCTURED_OUTPUT JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────── LLM_JUDGE strategy ───────────────────────────

    /**
     * One-line JSON returned by the judge. Defensively typed: any field can
     * be missing, the parser tolerates code fences and extra prose.
     *
     * <p>{@code redirectMessage} is only meaningful when {@code onTopic} is
     * false — in that case the judge wrote a short polite nudge back to the
     * goal in the user's language. When blank, the engine keeps the LLM's
     * original reply (which is usually already redirecting via layer 1).
     *
     * <p>{@code abandoned} is the escape valve: when the user clearly wants
     * to quit/cancel/skip the flow, the judge sets it true and writes a
     * goodbye in {@code redirectMessage}; the engine then transitions to
     * the END node so the conversation can continue freely.
     */
    private record JudgeVerdict(boolean onTopic, String collectedValue, boolean readyToAdvance,
            String redirectMessage, boolean abandoned) {
    }

    private AdvanceResult advanceLlmJudge(TurChatFlowState state,
            ChatFlowGraph graph,
            String userMessage,
            String assistantMessage,
            ChatModel judgeModel) {
        Optional<ChatFlowNode> nodeOpt = currentNode(state, graph);
        if (nodeOpt.isEmpty()) {
            return AdvanceResult.of(state);
        }
        ChatFlowNode node = nodeOpt.get();

        // Terminal nodes: nothing to judge.
        if ("end".equals(node.type())) {
            return AdvanceResult.of(state);
        }

        // No model available → fall back to heuristics so the conversation
        // never gets stuck on a configuration gap.
        if (judgeModel == null) {
            log.warn("[FlowEngine] LLM_JUDGE selected but no model available — falling back to heuristic");
            return AdvanceResult.of(advanceHeuristic(state, graph, userMessage, assistantMessage));
        }

        JudgeVerdict verdict = askJudge(judgeModel, node, userMessage, assistantMessage);
        if (verdict == null) {
            log.warn("[FlowEngine] Judge produced no usable verdict — falling back to heuristic");
            return AdvanceResult.of(advanceHeuristic(state, graph, userMessage, assistantMessage));
        }

        Map<String, String> variables = readVariables(state);

        // User asked to quit / cancel / skip → end the flow gracefully so
        // the conversation can move on. Use the judge's farewell message as
        // the assistant text when provided.
        if (verdict.abandoned()) {
            log.info("[FlowEngine] Judge detected abandon on node '{}' — ending flow", node.id());
            writeVariables(state, variables);
            transitionToEnd(state, graph);
            stateRepository.save(state);
            String farewell = verdict.redirectMessage();
            if (farewell != null && !farewell.isBlank()) {
                return new AdvanceResult(state, farewell.trim());
            }
            return AdvanceResult.of(state);
        }

        // Off-topic → state stays. Substitute the assistant text only when
        // the judge wrote a polite, user-language redirect; otherwise keep
        // the LLM's reply (layer 1 already nudged it on-topic in most cases).
        if (!verdict.onTopic()) {
            writeVariables(state, variables);
            stateRepository.save(state);
            String redirect = verdict.redirectMessage();
            if (redirect != null && !redirect.isBlank()) {
                log.info("[FlowEngine] Judge flagged off-topic on node '{}' — using judge-supplied redirect",
                        node.id());
                return new AdvanceResult(state, redirect.trim());
            }
            log.info("[FlowEngine] Judge flagged off-topic on node '{}' but did not supply a redirect — keeping LLM reply",
                    node.id());
            return AdvanceResult.of(state);
        }

        // Capture the value, if the judge extracted one and the node collects.
        if (verdict.collectedValue() != null && !verdict.collectedValue().isBlank()
                && node.outputVariable() != null && !node.outputVariable().isBlank()) {
            variables.put(node.outputVariable().trim(), verdict.collectedValue().trim());
        }

        if (!verdict.readyToAdvance()) {
            log.info("[FlowEngine] Judge: stay on node '{}' (not ready to advance)", node.id());
            writeVariables(state, variables);
            return AdvanceResult.of(stateRepository.save(state));
        }

        List<ChatFlowEdge> outgoing = graph.outgoingEdges(node.id());
        if (outgoing.isEmpty()) {
            log.info("[FlowEngine] Node '{}' has no outgoing edges — staying", node.id());
            writeVariables(state, variables);
            return AdvanceResult.of(stateRepository.save(state));
        }
        ChatFlowEdge nextEdge = outgoing.get(0);
        Optional<ChatFlowNode> targetNode = graph.nodeById(nextEdge.target());
        if (targetNode.isEmpty()) {
            log.warn("[FlowEngine] Edge '{}' points at missing node '{}'",
                    nextEdge.id(), nextEdge.target());
            writeVariables(state, variables);
            return AdvanceResult.of(stateRepository.save(state));
        }
        log.info("[FlowEngine] Judge advanced '{}' → '{}' on conversation '{}'",
                node.id(), targetNode.get().id(), state.getConversationId());
        state.setCurrentNodeId(targetNode.get().id());
        writeVariables(state, variables);
        return AdvanceResult.of(stateRepository.save(state));
    }

    /**
     * Single-shot judge call. Returns null when the call fails or the reply
     * cannot be parsed as the expected JSON shape — caller falls back to
     * heuristic advance to keep the conversation moving.
     */
    private JudgeVerdict askJudge(ChatModel model,
            ChatFlowNode node,
            String userMessage,
            String assistantMessage) {
        String sys = """
                You are a strict conversation auditor. You receive the active step of a guided
                chat flow plus the most recent user/assistant exchange and decide five things:

                1. on_topic: did the assistant stay on the goal of the active step?
                2. collected_value: did the *user's last message* supply a value the step is
                   collecting? Be LIBERAL — a bare reply that fits the value type counts:
                     - "Alexandre" or "Alexandre Oliveira" for a name
                     - "foo@bar.com" for an email
                     - "+5511999999999" or "11 99999-9999" for a phone
                     - "2026-04-28" or "28/04/2026" for a date
                   The user does NOT need to use a full sentence like "my name is X" or
                   "meu email é Y". Extract the substring that is the value.
                   Return null only when the user truly said nothing of the requested kind.
                3. ready_to_advance: should the conversation move to the next step?
                   true when collected_value is non-null AND, when a validation rule applies,
                   the value matches the rule (a syntactically valid email for the "email"
                   rule, etc.). For steps without an output variable, true when the goal is
                   clearly addressed by the assistant.
                4. abandoned: true ONLY when the user clearly wants to quit, cancel, give up
                   on, or skip this flow ("desisto", "cancelar", "quero parar", "no thanks",
                   "I don't want to continue", etc.). When abandoned is true, the flow ends
                   and the conversation continues freely.
                5. redirect_message: written in the SAME LANGUAGE as the user's last message,
                   short and polite, addressed to the user. Use it for:
                   - on_topic=false (not abandoned): a one-sentence nudge back to the goal.
                     Do NOT quote the goal verbatim, do NOT mention internal terms like
                     "step", "goal" or "output variable".
                   - abandoned=true: a graceful goodbye that lets them go without pressure
                     (e.g. "Sem problema, qualquer coisa é só me chamar.").
                   Empty string in all other cases.

                Reply with EXACTLY one JSON object on a single line, no prose, no code fences,
                no explanations. Example:
                {"on_topic": true, "collected_value": "x@y.com", "ready_to_advance": true, "abandoned": false, "redirect_message": ""}

                Active step: %s
                Goal: %s
                Output variable to collect: %s
                Validation rule: %s
                """.formatted(
                safe(node.label()),
                node.aiInstruction() == null ? "(none)" : node.aiInstruction().trim(),
                node.outputVariable() == null || node.outputVariable().isBlank()
                        ? "(none)" : node.outputVariable().trim(),
                node.validationRule() == null || node.validationRule().isBlank()
                        ? "(none)" : node.validationRule().trim());

        String user = "User said: " + (userMessage == null ? "" : userMessage.trim()) + "\n"
                + "Assistant said: " + (assistantMessage == null ? "" : assistantMessage.trim());

        String reply;
        try {
            Prompt prompt = new Prompt(List.of(new SystemMessage(sys), new UserMessage(user)));
            var response = model.call(prompt);
            reply = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText().trim()
                            : "";
        } catch (Exception e) {
            log.warn("[FlowEngine] Judge call failed: {}", e.getMessage());
            return null;
        }
        if (reply.isEmpty()) {
            return null;
        }
        log.info("[FlowEngine] Judge raw reply: '{}'", reply);
        return parseJudgeVerdict(reply);
    }

    /**
     * Parses the judge reply tolerantly. Strips code fences, extracts the
     * outer JSON object if extra prose was emitted, then defaults missing
     * fields conservatively (off-topic / no advance / no value).
     */
    private static JudgeVerdict parseJudgeVerdict(String reply) {
        String json = stripFences(reply);
        int braceStart = json.indexOf('{');
        int braceEnd = json.lastIndexOf('}');
        if (braceStart < 0 || braceEnd <= braceStart) {
            return null;
        }
        json = json.substring(braceStart, braceEnd + 1);
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            boolean onTopic = parsed.get("on_topic") instanceof Boolean b && b;
            String collected = parsed.get("collected_value") instanceof String s ? s : null;
            boolean ready = parsed.get("ready_to_advance") instanceof Boolean b && b;
            String redirect = parsed.get("redirect_message") instanceof String s ? s : null;
            boolean abandoned = parsed.get("abandoned") instanceof Boolean b && b;
            return new JudgeVerdict(onTopic, collected, ready, redirect, abandoned);
        } catch (JacksonException e) {
            log.warn("[FlowEngine] Could not parse judge JSON '{}': {}", json, e.getMessage());
            return null;
        }
    }

    private static String stripFences(String s) {
        String trimmed = s.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closing = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && closing > firstNewline) {
                return trimmed.substring(firstNewline + 1, closing).trim();
            }
        }
        return trimmed;
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /**
     * Moves the state to the graph's end node so the conversation continues
     * freely (the addendum returns empty for end nodes). Falls back to a
     * synthetic "__abandoned__" id when the graph has no end node — same net
     * effect: {@link #currentNode} returns empty and the addendum is skipped.
     */
    private static void transitionToEnd(TurChatFlowState state, ChatFlowGraph graph) {
        Optional<ChatFlowNode> endNode = graph.nodes().stream()
                .filter(n -> "end".equals(n.type()))
                .findFirst();
        state.setCurrentNodeId(endNode.map(ChatFlowNode::id).orElse("__abandoned__"));
    }

    private static Optional<ChatFlowNode> firstInteractiveNode(ChatFlowGraph graph, ChatFlowNode start) {
        List<ChatFlowEdge> outgoing = graph.outgoingEdges(start.id());
        if (outgoing.isEmpty()) {
            return Optional.empty();
        }
        return graph.nodeById(outgoing.get(0).target());
    }

    /**
     * Tries to extract a value matching the node's validation rule from the
     * user message. Returns the matched substring, or null if nothing fits.
     */
    private static String extractValue(String validationRule, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        if (validationRule == null || validationRule.isBlank()
                || "none".equalsIgnoreCase(validationRule)) {
            // No rule → accept any non-trivial input as the captured value.
            String trimmed = userMessage.trim();
            return trimmed.length() >= 2 ? trimmed : null;
        }
        Pattern pattern = VALIDATION_PATTERNS.get(validationRule.trim().toLowerCase());
        if (pattern == null) {
            // Unknown rule → degrade to "any non-trivial input".
            String trimmed = userMessage.trim();
            return trimmed.length() >= 2 ? trimmed : null;
        }
        var matcher = pattern.matcher(userMessage);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Very small "did the assistant address the goal?" heuristic: at least
     * one informational word from {@code aiInstruction} appears in the
     * response. Good enough for step 1; replaced by a real classifier later.
     */
    private static boolean looksAligned(String aiInstruction, String assistantMessage) {
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return false;
        }
        if (aiInstruction == null || aiInstruction.isBlank()) {
            return assistantMessage.length() >= 10;
        }
        String lowerReply = assistantMessage.toLowerCase();
        for (String token : aiInstruction.toLowerCase().split("\\W+")) {
            if (token.length() >= 5 && lowerReply.contains(token)) {
                return true;
            }
        }
        return assistantMessage.length() >= 40;
    }

    private static Map<String, String> readVariables(TurChatFlowState state) {
        if (state.getVariablesJson() == null || state.getVariablesJson().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, String> parsed = OBJECT_MAPPER.readValue(state.getVariablesJson(), VARIABLES_TYPE);
            return parsed != null ? new LinkedHashMap<>(parsed) : new LinkedHashMap<>();
        } catch (JacksonException e) {
            log.warn("[FlowEngine] Variables JSON for state '{}' is invalid; resetting", state.getId());
            return new LinkedHashMap<>();
        }
    }

    private static void writeVariables(TurChatFlowState state, Map<String, String> variables) {
        try {
            state.setVariablesJson(OBJECT_MAPPER.writeValueAsString(
                    variables != null ? variables : new HashMap<>()));
        } catch (JacksonException e) {
            log.warn("[FlowEngine] Failed to serialize variables for state '{}'", state.getId());
            state.setVariablesJson("{}");
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
