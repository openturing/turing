/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.genai.flow.TurChatFlowStaticPromptCache;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowCaptureMode;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * T51 / §VII.4.e — capture-first inversion behaviour of
 * {@link LlmJudgeGuardrailStrategy}. Exercises the strategy through the public
 * {@link LlmJudgeGuardrailStrategy#advance(AdvanceContext)} with a mocked
 * judge {@link ChatModel} (canned JSON verdict), pinning:
 *
 * <ul>
 *   <li>the slot is written BEFORE the judge runs (never null after an answer);</li>
 *   <li>CAPTURE_THEN_GATE still parks the cursor on a failing validationRule;</li>
 *   <li>CAPTURE_THEN_GRADE always advances (unless abandoned);</li>
 *   <li>the judge can no longer block a validation-passing value;</li>
 *   <li>the {@code <slot>__confidence} grade is written high/low;</li>
 *   <li>a protected out-of-band value is preserved, but a prior low-confidence
 *       provisional capture is replaceable on the re-prompt turn;</li>
 *   <li>{@code resolveCaptureMode} defaults to VALIDATE_THEN_CAPTURE.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class LlmJudgeCaptureFirstTest {

    private static final String CONF = LlmJudgeGuardrailStrategy.CONFIDENCE_SUFFIX;
    private static final String HANDOFF_OFFERED = LlmJudgeGuardrailStrategy.HANDOFF_OFFERED_SLOT;

    private LlmJudgeGuardrailStrategy newStrategy() {
        // Capture-first never touches the heuristic fallback or the prompt
        // cache (those serve the legacy path / addendum), so plain mocks are
        // enough — the dispatch returns before either is consulted.
        return new LlmJudgeGuardrailStrategy(
                mock(HeuristicGuardrailStrategy.class),
                mock(TurChatFlowStaticPromptCache.class));
    }

    // ─────────────────────────── CAPTURE_THEN_GATE ───────────────────────────

    @Test
    void gate_judgeRejectsButValueValid_capturesRawAndAdvancesLowConfidence() {
        ChatFlowNode node = aiQuestion("ask-name", "name", null);
        TurChatFlowState state = stateOn("ask-name");
        // Judge is unhappy (off-topic, not ready, no extraction) but the node
        // has no validation rule, so the captured raw reply is "valid".
        ChatModel judge = judge(
                "{\"on_topic\":false,\"collected_value\":null,\"ready_to_advance\":false,"
                        + "\"abandoned\":false,\"redirect_message\":\"Vamos focar no seu nome?\"}");

        String reply = newStrategy().advance(
                ctx(state, graphWith(node), node, "Alexandre", "ok", judge,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GATE)));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsEntry("name", "Alexandre");
        assertThat(vars).containsEntry("name" + CONF, "low");
        assertThat(state.getCurrentNodeId()).isEqualTo("next");
        // Advanced past an off-goal verdict — don't surface the stale redirect.
        assertThat(reply).isNull();
    }

    @Test
    void gate_validationRuleFails_capturesRawButStaysForReprompt() {
        ChatFlowNode node = aiQuestion("ask-email", "email", "email");
        TurChatFlowState state = stateOn("ask-email");
        ChatModel judge = judge(
                "{\"on_topic\":false,\"collected_value\":null,\"ready_to_advance\":false,"
                        + "\"abandoned\":false,\"redirect_message\":\"Pode informar um e-mail válido?\"}");

        String reply = newStrategy().advance(
                ctx(state, graphWith(node), node, "not-an-email", "ok", judge,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GATE)));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        // Captured (never null), but the deterministic rule failed → stay + low.
        assertThat(vars).containsEntry("email", "not-an-email");
        assertThat(vars).containsEntry("email" + CONF, "low");
        assertThat(state.getCurrentNodeId()).isEqualTo("ask-email");
        // Off-topic + staying → surface the judge's redirect.
        assertThat(reply).isEqualTo("Pode informar um e-mail válido?");
    }

    @Test
    void gate_judgeEndorsesAndValid_refinesValueAdvancesHighConfidence() {
        ChatFlowNode node = aiQuestion("ask-email", "email", "email");
        TurChatFlowState state = stateOn("ask-email");
        ChatModel judge = judge(
                "{\"on_topic\":true,\"collected_value\":\"x@y.com\",\"ready_to_advance\":true,"
                        + "\"abandoned\":false,\"redirect_message\":\"\"}");

        newStrategy().advance(
                ctx(state, graphWith(node), node, "meu email é x@y.com", "ok", judge,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GATE)));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        // Refined with the judge's clean extraction, not the raw sentence.
        assertThat(vars).containsEntry("email", "x@y.com");
        assertThat(vars).containsEntry("email" + CONF, "high");
        assertThat(state.getCurrentNodeId()).isEqualTo("next");
    }

    @Test
    void gate_protectedOutOfBandValue_isNotClobberedByNoOpReply() {
        // CV extraction wrote `name` out-of-band (no confidence marker, so it
        // is treated as protected) and the node does not override. A no-op
        // trigger reply must not overwrite it.
        ChatFlowNode node = aiQuestion("ask-name", "name", null);
        TurChatFlowState state = stateOn("ask-name");
        ChatFlowOps.writeVariables(state, Map.of("name", "Maria"));
        ChatModel judge = judge(
                "{\"on_topic\":true,\"collected_value\":null,\"ready_to_advance\":true,"
                        + "\"abandoned\":false,\"redirect_message\":\"\"}");

        newStrategy().advance(
                ctx(state, graphWith(node), node, "oi", "ok", judge,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GATE)));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsEntry("name", "Maria");
        assertThat(state.getCurrentNodeId()).isEqualTo("next");
    }

    @Test
    void gate_priorLowConfidenceValue_isReplaceableOnReprompt() {
        // A prior failed attempt left email="bad" + email__confidence=low. The
        // re-prompt answer must be allowed to replace it even without override.
        ChatFlowNode node = aiQuestion("ask-email", "email", "email");
        TurChatFlowState state = stateOn("ask-email");
        ChatFlowOps.writeVariables(state, Map.of("email", "bad", "email" + CONF, "low"));
        ChatModel judge = judge(
                "{\"on_topic\":true,\"collected_value\":\"x@y.com\",\"ready_to_advance\":true,"
                        + "\"abandoned\":false,\"redirect_message\":\"\"}");

        newStrategy().advance(
                ctx(state, graphWith(node), node, "x@y.com", "ok", judge,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GATE)));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsEntry("email", "x@y.com");
        assertThat(vars).containsEntry("email" + CONF, "high");
        assertThat(state.getCurrentNodeId()).isEqualTo("next");
    }

    @Test
    void gate_blankUserMessageOnSlotNode_staysWithoutCapturing() {
        ChatFlowNode node = aiQuestion("ask-name", "name", null);
        TurChatFlowState state = stateOn("ask-name");

        newStrategy().advance(
                ctx(state, graphWith(node), node, "  ", "Qual é o seu nome?",
                        judge("{\"on_topic\":true,\"ready_to_advance\":true}"),
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GATE)));

        assertThat(ChatFlowOps.readVariables(state)).doesNotContainKey("name");
        assertThat(state.getCurrentNodeId()).isEqualTo("ask-name");
    }

    @Test
    void gate_noJudgeModel_stillCapturesRawAndAdvances() {
        ChatFlowNode node = aiQuestion("ask-name", "name", null);
        TurChatFlowState state = stateOn("ask-name");

        newStrategy().advance(
                ctx(state, graphWith(node), node, "Alexandre", "ok", null,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GATE)));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsEntry("name", "Alexandre");
        assertThat(vars).containsEntry("name" + CONF, "low");
        assertThat(state.getCurrentNodeId()).isEqualTo("next");
    }

    // ─────────────────────────── CAPTURE_THEN_GRADE ───────────────────────────

    @Test
    void grade_validationFailsAndJudgeRejects_stillAdvances() {
        ChatFlowNode node = aiQuestion("ask-email", "email", "email");
        TurChatFlowState state = stateOn("ask-email");
        ChatModel judge = judge(
                "{\"on_topic\":false,\"collected_value\":null,\"ready_to_advance\":false,"
                        + "\"abandoned\":false,\"redirect_message\":\"Hmm?\"}");

        newStrategy().advance(
                ctx(state, graphWith(node), node, "garbage", "ok", judge,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GRADE)));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsEntry("email", "garbage");
        assertThat(vars).containsEntry("email" + CONF, "low");
        // Pure capture-then-grade: never stalls, even on an invalid value.
        assertThat(state.getCurrentNodeId()).isEqualTo("next");
    }

    @Test
    void grade_abandoned_endsFlowWithFarewell() {
        ChatFlowNode node = aiQuestion("ask-email", "email", null);
        TurChatFlowState state = stateOn("ask-email");
        ChatModel judge = judge(
                "{\"on_topic\":false,\"collected_value\":null,\"ready_to_advance\":false,"
                        + "\"abandoned\":true,\"redirect_message\":\"Sem problema, até mais!\"}");

        String reply = newStrategy().advance(
                ctx(state, graphWith(node), node, "desisto", "ok", judge,
                        flow(TurChatFlowCaptureMode.CAPTURE_THEN_GRADE)));

        assertThat(reply).isEqualTo("Sem problema, até mais!");
        assertThat(state.getCurrentNodeId()).isEqualTo("end-1");
        // Legacy close: no handoff offered.
        assertThat(ChatFlowOps.readVariables(state)).doesNotContainKey(HANDOFF_OFFERED);
    }

    // ───────────────── T53 — abandonment auto-escalation (handoff) ─────────────────

    @Test
    void abandoned_withHandoffMessage_offersHandoffAndWritesSlot_captureFirst() {
        ChatFlowNode node = aiQuestion("ask-email", "email", null);
        TurChatFlowState state = stateOn("ask-email");
        ChatModel judge = judge(
                "{\"on_topic\":false,\"collected_value\":null,\"ready_to_advance\":false,"
                        + "\"abandoned\":true,\"redirect_message\":\"Sem problema, até mais!\"}");

        String reply = newStrategy().advance(
                ctx(state, graphWith(node), node, "desisto", "ok", judge,
                        flowWithAbandonHandoff(TurChatFlowCaptureMode.CAPTURE_THEN_GRADE,
                                "Antes de você ir — quer falar com um consultor?")));

        // The configured offer replaces the judge's plain goodbye…
        assertThat(reply).isEqualTo("Antes de você ir — quer falar com um consultor?");
        // …and the handoff_offered tracking slot is written for UI/analytics.
        assertThat(ChatFlowOps.readVariables(state)).containsEntry(HANDOFF_OFFERED, "abandon");
        assertThat(state.getCurrentNodeId()).isEqualTo("end-1");
    }

    @Test
    void abandoned_withHandoffMessage_offersHandoffAndWritesSlot_legacyPath() {
        // VALIDATE_THEN_CAPTURE exercises the legacy advance() abandonment branch.
        ChatFlowNode node = aiQuestion("ask-email", "email", null);
        TurChatFlowState state = stateOn("ask-email");
        ChatModel judge = judge(
                "{\"on_topic\":false,\"collected_value\":null,\"ready_to_advance\":false,"
                        + "\"abandoned\":true,\"redirect_message\":\"Tudo bem, até logo!\"}");

        String reply = newStrategy().advance(
                ctx(state, graphWith(node), node, "cancelar", "ok", judge,
                        flowWithAbandonHandoff(TurChatFlowCaptureMode.VALIDATE_THEN_CAPTURE,
                                "Posso te conectar com um especialista, quer?")));

        assertThat(reply).isEqualTo("Posso te conectar com um especialista, quer?");
        assertThat(ChatFlowOps.readVariables(state)).containsEntry(HANDOFF_OFFERED, "abandon");
        assertThat(state.getCurrentNodeId()).isEqualTo("end-1");
    }

    @Test
    void abandoned_blankHandoffMessage_keepsLegacyFarewellAndNoSlot() {
        ChatFlowNode node = aiQuestion("ask-email", "email", null);
        TurChatFlowState state = stateOn("ask-email");
        ChatModel judge = judge(
                "{\"on_topic\":false,\"collected_value\":null,\"ready_to_advance\":false,"
                        + "\"abandoned\":true,\"redirect_message\":\"Sem problema!\"}");

        String reply = newStrategy().advance(
                ctx(state, graphWith(node), node, "desisto", "ok", judge,
                        flowWithAbandonHandoff(TurChatFlowCaptureMode.CAPTURE_THEN_GRADE, "   ")));

        // Blank message → opt-out → judge's plain farewell, no tracking slot.
        assertThat(reply).isEqualTo("Sem problema!");
        assertThat(ChatFlowOps.readVariables(state)).doesNotContainKey(HANDOFF_OFFERED);
        assertThat(state.getCurrentNodeId()).isEqualTo("end-1");
    }

    // ─────────────────────────── resolveCaptureMode ───────────────────────────

    @Test
    void resolveCaptureMode_nullFlow_defaultsToValidateThenCapture() {
        ChatFlowNode node = aiQuestion("ask-name", "name", null);
        AdvanceContext c = ctx(stateOn("ask-name"), graphWith(node), node, "x", "y", null, null);
        assertThat(LlmJudgeGuardrailStrategy.resolveCaptureMode(c))
                .isEqualTo(TurChatFlowCaptureMode.VALIDATE_THEN_CAPTURE);
    }

    @Test
    void resolveCaptureMode_nullColumn_defaultsToValidateThenCapture() {
        ChatFlowNode node = aiQuestion("ask-name", "name", null);
        TurChatFlow flow = new TurChatFlow();
        flow.setCaptureMode(null);
        AdvanceContext c = ctx(stateOn("ask-name"), graphWith(node), node, "x", "y", null, flow);
        assertThat(LlmJudgeGuardrailStrategy.resolveCaptureMode(c))
                .isEqualTo(TurChatFlowCaptureMode.VALIDATE_THEN_CAPTURE);
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static AdvanceContext ctx(TurChatFlowState state, ChatFlowGraph graph,
            ChatFlowNode node, String userMessage, String assistantMessage,
            ChatModel judge, TurChatFlow flow) {
        return new AdvanceContext(state, graph, node, userMessage, assistantMessage, judge, flow);
    }

    private static TurChatFlow flow(TurChatFlowCaptureMode mode) {
        TurChatFlow flow = new TurChatFlow();
        flow.setCaptureMode(mode);
        return flow;
    }

    private static TurChatFlow flowWithAbandonHandoff(TurChatFlowCaptureMode mode, String message) {
        TurChatFlow flow = flow(mode);
        flow.setAbandonHandoffMessage(message);
        return flow;
    }

    private static TurChatFlowState stateOn(String nodeId) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        state.setCurrentNodeId(nodeId);
        ChatFlowOps.writeVariables(state, Map.of());
        return state;
    }

    private static ChatFlowNode aiQuestion(String id, String outputVar, String validationRule) {
        NodeData data = new NodeData(
                id, "aiQuestion", "goal", outputVar, validationRule,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        return new ChatFlowNode(id, "aiQuestion", data);
    }

    /** Graph: {@code node → "next"}, plus an "end" node for abandon transitions. */
    private static ChatFlowGraph graphWith(ChatFlowNode node) {
        ChatFlowNode next = new ChatFlowNode("next", "aiQuestion", null);
        ChatFlowNode end = new ChatFlowNode("end-1", "end", null);
        ChatFlowEdge edge = new ChatFlowEdge("e1", node.id(), "next", null, null, null);
        return new ChatFlowGraph(List.of(node, next, end), List.of(edge));
    }

    /** A judge {@link ChatModel} whose single reply is {@code json}. */
    private static ChatModel judge(String json) {
        ChatModel model = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = mock(AssistantMessage.class);
        when(model.call(any(Prompt.class))).thenReturn(response);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(message.getText()).thenReturn(json);
        return model;
    }
}
