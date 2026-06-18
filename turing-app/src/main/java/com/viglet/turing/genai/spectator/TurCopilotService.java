/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.spectator;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T120 — Co-pilot ("take the wheel") turn handling for spectated conversations.
 *
 * <p>Where {@code humanApproval} (T119) is an async, graph-declared pause,
 * Spectator + Co-pilot mode is the live, in-the-loop complement: an operator
 * watching a conversation over the slot / workspace / message SSE buses can
 * inject a turn manually. The LLM step is skipped — the operator's typed text
 * <em>becomes</em> the assistant message — but the turn still flows through the
 * <strong>same flow-advance + telemetry path</strong> a normal turn would, so
 * slots, analytics, chat memory, and the spectator stream all stay consistent.
 *
 * <p>A manual turn is the production turn pipeline minus the LLM call:
 * <ol>
 *   <li>If the operator paired their reply with the visitor's message and a flow
 *       governs the conversation, run {@link TurChatFlowEngineService#advance}
 *       with {@code assistantMessage = null} — exactly the pre-LLM early-advance
 *       a normal turn does (§I.5 step 1). Slot captures and cursor moves publish
 *       to the slot bus from inside the engine.</li>
 *   <li>Record the turn into chat memory and analytics (token counts are zero —
 *       no model ran).</li>
 *   <li>Tee both messages onto the {@link TurChatMessageEventBus} with
 *       {@code manual=true} on the assistant message so the spectator UI badges
 *       it as operator-authored.</li>
 * </ol>
 *
 * <p><b>Scope (V1).</b> This is an <em>operator-initiated</em> turn: it does not
 * preempt an in-flight LLM call on the visitor's own chat request. The operator,
 * watching live, authors a reply that the visitor's client picks up from the
 * conversation transcript / message stream. A future enhancement could add a
 * per-conversation "manual hold" that parks the visitor endpoint until the
 * operator replies.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCopilotService {

    private final TurAIAgentRepository agentRepository;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurChatMemoryService chatMemoryService;
    private final TurChatAnalyticsService chatAnalyticsService;
    private final TurChatMessageEventBus chatMessageEventBus;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService turSecretCryptoService;

    public TurCopilotService(TurAIAgentRepository agentRepository,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowEngineService chatFlowEngineService,
            TurChatMemoryService chatMemoryService,
            TurChatAnalyticsService chatAnalyticsService,
            TurChatMessageEventBus chatMessageEventBus,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService turSecretCryptoService) {
        this.agentRepository = agentRepository;
        this.chatFlowRepository = chatFlowRepository;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatMemoryService = chatMemoryService;
        this.chatAnalyticsService = chatAnalyticsService;
        this.chatMessageEventBus = chatMessageEventBus;
        this.llmModelFactory = llmModelFactory;
        this.turSecretCryptoService = turSecretCryptoService;
    }

    /**
     * Outcome of a manual turn. {@code ok=false} carries a human-readable
     * {@code error}; the remaining fields are then empty.
     *
     * @param suggestedOptions chips for the next visitor turn, derived from the
     *                         post-advance node (empty when no flow governs)
     */
    public record ManualTurnResult(boolean ok, String assistantMessage, String currentNodeId,
            List<String> suggestedOptions, String error) {

        static ManualTurnResult error(String message) {
            return new ManualTurnResult(false, null, null, List.of(), message);
        }
    }

    /**
     * Inject an operator-authored assistant turn into a conversation.
     *
     * @param conversationId  the session being co-piloted (required)
     * @param agentId         the agent that owns the conversation (required)
     * @param flowId          optional flow to advance; when blank the
     *                        conversation's active leaf flow is used
     * @param userMessage     the visitor message being answered; when blank the
     *                        flow is not advanced (a pure operator interjection)
     * @param assistantMessage the operator's reply — becomes the assistant
     *                        message (required, non-blank)
     */
    @Transactional
    public ManualTurnResult manualTurn(String conversationId, String agentId, String flowId,
            String userMessage, String assistantMessage) {
        if (conversationId == null || conversationId.isBlank()) {
            return ManualTurnResult.error("conversationId is required");
        }
        if (assistantMessage == null || assistantMessage.isBlank()) {
            return ManualTurnResult.error("assistantMessage is required");
        }
        Optional<TurAIAgent> agentOpt = agentId == null ? Optional.empty()
                : agentRepository.findById(agentId);
        if (agentOpt.isEmpty()) {
            return ManualTurnResult.error("AI Agent not found: " + agentId);
        }
        TurAIAgent agent = agentOpt.get();

        // 1) Advance the flow exactly as a normal turn's pre-LLM early-advance
        //    would, so slots/cursor/analytics stay consistent. Skipped when the
        //    operator did not pair a visitor message (pure interjection) or when
        //    no flow governs the conversation.
        String currentNodeId = null;
        List<String> suggestedOptions = List.of();
        if (userMessage != null && !userMessage.isBlank()) {
            AdvanceOutcome outcome = advanceFlow(agent, conversationId, flowId, userMessage);
            currentNodeId = outcome.currentNodeId();
            suggestedOptions = outcome.suggestedOptions();
        }

        // 2) Telemetry — chat memory + analytics turn counter (zero tokens; no
        //    model ran). Best-effort: never fail the operator's reply over it.
        try {
            chatMemoryService.recordTurn(null, agent, conversationId, null,
                    userMessage, assistantMessage);
        } catch (RuntimeException e) {
            log.debug("[Copilot] chat-memory record failed for conv={}: {}",
                    conversationId, e.getMessage());
        }
        try {
            if (chatAnalyticsService != null && chatAnalyticsService.isEnabled()) {
                chatAnalyticsService.recordTurn(conversationId, 0L, 0L);
            }
        } catch (RuntimeException e) {
            log.debug("[Copilot] analytics record failed for conv={}: {}",
                    conversationId, e.getMessage());
        }

        // 3) Tee onto the spectator message bus. The assistant event is flagged
        //    manual=true so the spectator UI badges it as operator-authored.
        if (userMessage != null && !userMessage.isBlank()) {
            chatMessageEventBus.publish(TurChatMessageEvent.user(conversationId, userMessage));
        }
        chatMessageEventBus.publish(
                TurChatMessageEvent.assistantManual(conversationId, assistantMessage));

        log.info("[Copilot] manual turn for conv='{}' agent='{}' node='{}' ({} chars)",
                conversationId, agent.getId(), currentNodeId, assistantMessage.length());
        return new ManualTurnResult(true, assistantMessage, currentNodeId, suggestedOptions, null);
    }

    private record AdvanceOutcome(String currentNodeId, List<String> suggestedOptions) {
        static AdvanceOutcome empty() {
            return new AdvanceOutcome(null, List.of());
        }
    }

    /**
     * Resolves the governing flow (forced by {@code flowId} or the conversation's
     * active leaf) and runs {@code advance(...)} with the visitor message and a
     * null assistant message — the same call the executor makes pre-LLM. Returns
     * the post-advance cursor + suggested chips. Defensive throughout: any
     * resolution miss returns {@link AdvanceOutcome#empty()} and the manual reply
     * still goes through.
     */
    private AdvanceOutcome advanceFlow(TurAIAgent agent, String conversationId, String flowId,
            String userMessage) {
        String effectiveFlowId = flowId;
        if (effectiveFlowId == null || effectiveFlowId.isBlank()) {
            effectiveFlowId = chatFlowEngineService.getConversationState(conversationId).flowId();
        }
        if (effectiveFlowId == null || effectiveFlowId.isBlank()) {
            return AdvanceOutcome.empty();
        }
        Optional<TurChatFlow> flowOpt = chatFlowRepository.findById(effectiveFlowId);
        if (flowOpt.isEmpty()) {
            return AdvanceOutcome.empty();
        }
        TurChatFlow flow = flowOpt.get();
        // Defense in depth: a flow only governs chats for its owning agent.
        if (flow.getTurAIAgent() == null || !agent.getId().equals(flow.getTurAIAgent().getId())) {
            log.warn("[Copilot] flow '{}' does not belong to agent '{}'", effectiveFlowId, agent.getId());
            return AdvanceOutcome.empty();
        }
        Optional<ChatFlowGraph> graphOpt = chatFlowEngineService.parseGraph(flow);
        if (graphOpt.isEmpty()) {
            return AdvanceOutcome.empty();
        }
        Optional<TurChatFlowState> stateOpt = chatFlowEngineService.loadOrInitState(
                conversationId, flow, graphOpt.get());
        if (stateOpt.isEmpty()) {
            return AdvanceOutcome.empty();
        }
        // Align with the active leaf (the state may sit in a sub-flow).
        TurChatFlowState state = stateOpt.get();
        TurChatFlow leafFlow = state.getFlow();
        ChatFlowGraph leafGraph = leafFlow.getId().equals(flow.getId())
                ? graphOpt.get()
                : chatFlowEngineService.parseGraph(leafFlow).orElse(graphOpt.get());

        // The guardrail strategy (e.g. LLM_JUDGE) may need an auxiliary model to
        // grade the visitor message against the node. Build it from the agent's
        // default LLM, exactly as a normal turn would; KEYWORD-style flows
        // tolerate a null model.
        ChatModel auxiliaryModel = buildAuxiliaryModel(agent);
        chatFlowEngineService.advance(leafFlow, state, leafGraph, userMessage, null, auxiliaryModel);

        // Re-read the post-advance leaf to surface the new cursor + chips.
        var stateDto = chatFlowEngineService.getConversationState(conversationId);
        String currentNodeId = stateDto.currentNodeId();
        List<String> chips = List.of();
        if (currentNodeId != null) {
            ChatFlowGraph postGraph = stateDto.flowId() != null
                    && stateDto.flowId().equals(leafFlow.getId())
                            ? leafGraph
                            : chatFlowRepository.findById(stateDto.flowId())
                                    .flatMap(chatFlowEngineService::parseGraph)
                                    .orElse(leafGraph);
            ChatFlowNode current = postGraph.nodeById(currentNodeId).orElse(null);
            chips = ChatFlowOps.suggestedOptions(postGraph, current);
        }
        return new AdvanceOutcome(currentNodeId, chips);
    }

    /**
     * Builds a transient {@link ChatModel} from the agent's default LLM instance
     * (lowest id — deterministic, matching {@code TurAIAgentChatAPI}'s fallback)
     * for guardrail evaluation only. Returns {@code null} when the agent has no
     * usable LLM or the model build fails — callers tolerate a null model.
     */
    private ChatModel buildAuxiliaryModel(TurAIAgent agent) {
        try {
            Optional<TurLLMInstance> llmOpt = agent.getLlmInstances().stream()
                    .min(Comparator.comparing(TurLLMInstance::getId));
            if (llmOpt.isEmpty()) {
                return null;
            }
            TurLLMInstance llm = llmOpt.get();
            String apiKey = turSecretCryptoService.decrypt(llm.getApiKeyEncrypted());
            return llmModelFactory.createChatModel(llm, apiKey);
        } catch (RuntimeException e) {
            log.debug("[Copilot] could not build auxiliary model for agent '{}': {}",
                    agent.getId(), e.getMessage());
            return null;
        }
    }
}
