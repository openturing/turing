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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps.PlanItem;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Pure-function tests for the T108 {@code planningStep} helpers in
 * {@link ChatFlowOps}: the tolerant plan parser ({@link ChatFlowOps#parsePlan},
 * which doubles as the read path for a persisted plan slot), the canonical
 * serializer ({@link ChatFlowOps#serializePlan}), LLM plan generation against a
 * mocked {@code ChatModel}, and {@link ChatFlowOps#applyPlanningStepNode}'s
 * slot-write + override semantics.
 *
 * <p>No Spring context — exercises only static methods + a bare
 * {@link TurChatFlowState} so the suite stays fast.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class PlanningStepTest {

    // ─────────────────────────── parsePlan ───────────────────────────

    @Test
    @DisplayName("parses a clean JSON array into canonical PlanItems")
    void parsePlan_cleanArray() {
        List<PlanItem> plan = ChatFlowOps.parsePlan(
                "[{\"id\":\"1\",\"title\":\"Gather requirements\",\"status\":\"pending\"},"
                        + "{\"id\":\"2\",\"title\":\"Draft proposal\",\"status\":\"done\"}]");
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).id()).isEqualTo("1");
        assertThat(plan.get(0).title()).isEqualTo("Gather requirements");
        assertThat(plan.get(0).status()).isEqualTo(PlanItem.PENDING);
        assertThat(plan.get(0).isPending()).isTrue();
        assertThat(plan.get(1).status()).isEqualTo(PlanItem.DONE);
        assertThat(plan.get(1).isPending()).isFalse();
    }

    @Test
    @DisplayName("strips a ```json markdown fence before parsing")
    void parsePlan_strippedFence() {
        String fenced = "```json\n[{\"title\":\"Step one\"}]\n```";
        List<PlanItem> plan = ChatFlowOps.parsePlan(fenced);
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).title()).isEqualTo("Step one");
    }

    @Test
    @DisplayName("isolates the array when the model wraps it in prose")
    void parsePlan_proseWrapped() {
        String reply = "Sure! Here is the plan:\n[{\"title\":\"Do the thing\"}]\nLet me know.";
        List<PlanItem> plan = ChatFlowOps.parsePlan(reply);
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).title()).isEqualTo("Do the thing");
    }

    @Test
    @DisplayName("fills a sequential id when the model omits one")
    void parsePlan_sequentialIdWhenMissing() {
        List<PlanItem> plan = ChatFlowOps.parsePlan(
                "[{\"title\":\"first\"},{\"title\":\"second\"},{\"title\":\"third\"}]");
        assertThat(plan).extracting(PlanItem::id).containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("defaults a missing or unrecognized status to pending")
    void parsePlan_statusDefaultsToPending() {
        List<PlanItem> plan = ChatFlowOps.parsePlan(
                "[{\"title\":\"no status\"},{\"title\":\"weird\",\"status\":\"in_progress\"}]");
        assertThat(plan).isNotEmpty().allSatisfy(item ->
                assertThat(item.status()).isEqualTo(PlanItem.PENDING));
    }

    @Test
    @DisplayName("drops items with a blank title")
    void parsePlan_dropsBlankTitle() {
        List<PlanItem> plan = ChatFlowOps.parsePlan(
                "[{\"title\":\"keep\"},{\"title\":\"  \"},{\"id\":\"x\"}]");
        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).title()).isEqualTo("keep");
    }

    @Test
    @DisplayName("garbage / null / non-array input yields an empty plan")
    void parsePlan_garbageYieldsEmpty() {
        assertThat(ChatFlowOps.parsePlan(null)).isEmpty();
        assertThat(ChatFlowOps.parsePlan("")).isEmpty();
        assertThat(ChatFlowOps.parsePlan("not json at all")).isEmpty();
        assertThat(ChatFlowOps.parsePlan("{\"title\":\"object not array\"}")).isEmpty();
        assertThat(ChatFlowOps.parsePlan("[]")).isEmpty();
    }

    @Test
    @DisplayName("caps an over-long plan at the MAX_PLAN_ITEMS limit")
    void parsePlan_capsAtLimit() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"title\":\"step ").append(i).append("\"}");
        }
        sb.append(']');
        assertThat(ChatFlowOps.parsePlan(sb.toString())).hasSize(24);
    }

    // ─────────────────────────── serializePlan ───────────────────────────

    @Test
    @DisplayName("serialize → parse round-trips a plan")
    void serializePlan_roundTrip() {
        List<PlanItem> original = List.of(
                new PlanItem("1", "Alpha", PlanItem.PENDING),
                new PlanItem("2", "Beta", PlanItem.DONE));
        String json = ChatFlowOps.serializePlan(original);
        List<PlanItem> reparsed = ChatFlowOps.parsePlan(json);
        assertThat(reparsed).isEqualTo(original);
    }

    @Test
    @DisplayName("serialize of an empty/null plan is a valid empty array")
    void serializePlan_empty() {
        assertThat(ChatFlowOps.serializePlan(null)).isEqualTo("[]");
        assertThat(ChatFlowOps.serializePlan(List.of())).isEqualTo("[]");
    }

    // ─────────────────────────── generatePlan ───────────────────────────

    @Test
    @DisplayName("null aux model yields an empty plan (graceful fallback)")
    void generatePlan_nullModelEmpty() {
        assertThat(ChatFlowOps.generatePlan(null, planningNode("__plan", null), Map.of()))
                .isEmpty();
    }

    @Test
    @DisplayName("parses the model's fenced JSON reply into PlanItems")
    void generatePlan_parsesModelReply() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(chatResponse(
                """
                ```json
                [{"id":"1","title":"Collect docs","status":"pending"},{"id":"2","title":"Review","status":"pending"}]
                ```"""));
        List<PlanItem> plan = ChatFlowOps.generatePlan(model, planningNode("__plan", null), Map.of());
        assertThat(plan).extracting(PlanItem::title).containsExactly("Collect docs", "Review");
    }

    @Test
    @DisplayName("a model failure degrades to an empty plan, never throws")
    void generatePlan_modelFailureEmpty() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
        assertThat(ChatFlowOps.generatePlan(model, planningNode("__plan", null), Map.of()))
                .isEmpty();
    }

    // ─────────────────────────── applyPlanningStepNode ───────────────────────────

    @Test
    @DisplayName("writes the generated plan into the default __plan slot when outputVariable is blank")
    void apply_writesDefaultSlot() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(chatResponse(
                "[{\"title\":\"Step A\"},{\"title\":\"Step B\"}]"));
        TurChatFlowState state = stateWith("{}");

        ChatFlowOps.applyPlanningStepNode(state, planningNode(null, null), model);

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsKey(ChatFlowOps.DEFAULT_PLAN_SLOT);
        List<PlanItem> plan = ChatFlowOps.parsePlan(vars.get(ChatFlowOps.DEFAULT_PLAN_SLOT));
        assertThat(plan).extracting(PlanItem::title).containsExactly("Step A", "Step B");
    }

    @Test
    @DisplayName("writes into the author-named slot when outputVariable is set")
    void apply_writesNamedSlot() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(chatResponse("[{\"title\":\"Only step\"}]"));
        TurChatFlowState state = stateWith("{}");

        ChatFlowOps.applyPlanningStepNode(state, planningNode("roadmap", null), model);

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsKey("roadmap").doesNotContainKey(ChatFlowOps.DEFAULT_PLAN_SLOT);
    }

    @Test
    @DisplayName("override=false preserves a plan already present in the slot")
    void apply_overrideFalseKeepsExisting() {
        ChatModel model = mock(ChatModel.class);
        // Model would return a new plan, but the slot is already filled.
        when(model.call(any(Prompt.class))).thenReturn(chatResponse("[{\"title\":\"NEW\"}]"));
        TurChatFlowState state = stateWith("{\"__plan\":\"[{\\\"id\\\":\\\"1\\\",\\\"title\\\":\\\"OLD\\\",\\\"status\\\":\\\"pending\\\"}]\"}");

        ChatFlowOps.applyPlanningStepNode(state, planningNode("__plan", null), model);

        List<PlanItem> plan = ChatFlowOps.parsePlan(
                ChatFlowOps.readVariables(state).get("__plan"));
        assertThat(plan).extracting(PlanItem::title).containsExactly("OLD");
    }

    @Test
    @DisplayName("override=true regenerates over an existing plan")
    void apply_overrideTrueRegenerates() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(chatResponse("[{\"title\":\"NEW\"}]"));
        TurChatFlowState state = stateWith("{\"__plan\":\"[{\\\"id\\\":\\\"1\\\",\\\"title\\\":\\\"OLD\\\",\\\"status\\\":\\\"pending\\\"}]\"}");

        ChatFlowOps.applyPlanningStepNode(state, planningNode("__plan", Boolean.TRUE), model);

        List<PlanItem> plan = ChatFlowOps.parsePlan(
                ChatFlowOps.readVariables(state).get("__plan"));
        assertThat(plan).extracting(PlanItem::title).containsExactly("NEW");
    }

    @Test
    @DisplayName("null aux model still writes a well-formed (empty) plan slot")
    void apply_nullModelWritesEmptyArray() {
        TurChatFlowState state = stateWith("{}");
        ChatFlowOps.applyPlanningStepNode(state, planningNode("__plan", null), null);
        assertThat(ChatFlowOps.readVariables(state)).containsEntry("__plan", "[]");
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static TurChatFlowState stateWith(String variablesJson) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        state.setVariablesJson(variablesJson);
        return state;
    }

    /** Builds a {@code planningStep} node with the given plan slot and override flag. */
    private static ChatFlowNode planningNode(String outputVariable, Boolean override) {
        return new ChatFlowNode("plan-1", "planningStep", new NodeData(
                "Plan", "planningStep", "Break the user's goal into 3-6 steps", outputVariable,
                null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), override, null, null, null, null, null, null, null, null));
    }
}
