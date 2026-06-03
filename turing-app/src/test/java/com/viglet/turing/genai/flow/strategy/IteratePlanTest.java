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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps.PlanItem;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Pure-function tests for the T108-2 {@code iteratePlan} helpers in
 * {@link ChatFlowOps}: next-pending selection, the reserved item markers
 * exposed to the body sub-flow, and the {@code completePlanItem} mutation that
 * makes the iteration terminate (mark_done / remove, idempotent, not-found).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class IteratePlanTest {

    // ─────────────────────────── firstPendingItem ───────────────────────────

    @Test
    @DisplayName("returns the first pending item, skipping completed ones")
    void firstPending_skipsDone() {
        List<PlanItem> plan = List.of(
                new PlanItem("1", "Alpha", PlanItem.DONE),
                new PlanItem("2", "Beta", PlanItem.PENDING),
                new PlanItem("3", "Gamma", PlanItem.PENDING));
        assertThat(ChatFlowOps.firstPendingItem(plan))
                .get().extracting(PlanItem::id).isEqualTo("2");
    }

    @Test
    @DisplayName("empty when every item is done, or the plan is null/empty")
    void firstPending_emptyWhenExhausted() {
        assertThat(ChatFlowOps.firstPendingItem(List.of(
                new PlanItem("1", "Alpha", PlanItem.DONE)))).isEmpty();
        assertThat(ChatFlowOps.firstPendingItem(List.of())).isEmpty();
        assertThat(ChatFlowOps.firstPendingItem(null)).isEmpty();
    }

    // ─────────────────────────── markers ───────────────────────────

    @Test
    @DisplayName("set/clear write and remove the reserved item-id / item-title slots")
    void markers_setAndClear() {
        TurChatFlowState state = stateWith("{}");
        ChatFlowOps.setPlanIterationMarkers(state, new PlanItem("7", "Do the thing", PlanItem.PENDING));

        Map<String, String> vars = ChatFlowOps.readVariables(state);
        assertThat(vars).containsEntry(ChatFlowOps.PLAN_ITEM_ID_SLOT, "7")
                .containsEntry(ChatFlowOps.PLAN_ITEM_TITLE_SLOT, "Do the thing");

        ChatFlowOps.clearPlanIterationMarkers(state);
        assertThat(ChatFlowOps.readVariables(state))
                .doesNotContainKey(ChatFlowOps.PLAN_ITEM_ID_SLOT)
                .doesNotContainKey(ChatFlowOps.PLAN_ITEM_TITLE_SLOT);
    }

    // ─────────────────────────── completePlanItem ───────────────────────────

    @Test
    @DisplayName("mark_done (default null mode) flips the item's status to done")
    void complete_markDoneDefault() {
        TurChatFlowState state = stateWith(planVars("__plan",
                "[{\"id\":\"1\",\"title\":\"A\",\"status\":\"pending\"},"
                        + "{\"id\":\"2\",\"title\":\"B\",\"status\":\"pending\"}]"));

        boolean changed = ChatFlowOps.completePlanItem(state, "__plan", "1", null);

        assertThat(changed).isTrue();
        List<PlanItem> plan = ChatFlowOps.parsePlan(ChatFlowOps.readVariables(state).get("__plan"));
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).status()).isEqualTo(PlanItem.DONE);
        assertThat(plan.get(1).status()).isEqualTo(PlanItem.PENDING);
    }

    @Test
    @DisplayName("remove mode drops the completed item from the plan")
    void complete_removeMode() {
        TurChatFlowState state = stateWith(planVars("__plan",
                "[{\"id\":\"1\",\"title\":\"A\",\"status\":\"pending\"},"
                        + "{\"id\":\"2\",\"title\":\"B\",\"status\":\"pending\"}]"));

        boolean changed = ChatFlowOps.completePlanItem(state, "__plan", "1",
                ChatFlowOps.COMPLETION_MODE_REMOVE);

        assertThat(changed).isTrue();
        List<PlanItem> plan = ChatFlowOps.parsePlan(ChatFlowOps.readVariables(state).get("__plan"));
        assertThat(plan).extracting(PlanItem::id).containsExactly("2");
    }

    @Test
    @DisplayName("re-completing an already-done item is idempotent (still changed=true, stays done)")
    void complete_idempotent() {
        TurChatFlowState state = stateWith(planVars("__plan",
                "[{\"id\":\"1\",\"title\":\"A\",\"status\":\"done\"}]"));

        boolean changed = ChatFlowOps.completePlanItem(state, "__plan", "1", null);

        // The item matched, so we report changed (the rewrite is harmless) — and
        // crucially it is NOT pending afterwards, which is what guarantees the
        // iteration terminates.
        assertThat(changed).isTrue();
        List<PlanItem> plan = ChatFlowOps.parsePlan(ChatFlowOps.readVariables(state).get("__plan"));
        assertThat(plan.get(0).isPending()).isFalse();
    }

    @Test
    @DisplayName("unknown item id leaves the plan untouched and returns false")
    void complete_notFound() {
        TurChatFlowState state = stateWith(planVars("__plan",
                "[{\"id\":\"1\",\"title\":\"A\",\"status\":\"pending\"}]"));

        boolean changed = ChatFlowOps.completePlanItem(state, "__plan", "999", null);

        assertThat(changed).isFalse();
        assertThat(ChatFlowOps.parsePlan(ChatFlowOps.readVariables(state).get("__plan")).get(0)
                .isPending()).isTrue();
    }

    @Test
    @DisplayName("empty plan / blank item id are graceful no-ops returning false")
    void complete_emptyAndBlank() {
        TurChatFlowState empty = stateWith("{}");
        assertThat(ChatFlowOps.completePlanItem(empty, "__plan", "1", null)).isFalse();

        TurChatFlowState filled = stateWith(planVars("__plan",
                "[{\"id\":\"1\",\"title\":\"A\",\"status\":\"pending\"}]"));
        assertThat(ChatFlowOps.completePlanItem(filled, "__plan", "  ", null)).isFalse();
        assertThat(ChatFlowOps.completePlanItem(filled, "__plan", null, null)).isFalse();
    }

    @Test
    @DisplayName("blank planSlot falls back to the default __plan slot")
    void complete_defaultSlot() {
        TurChatFlowState state = stateWith(planVars(ChatFlowOps.DEFAULT_PLAN_SLOT,
                "[{\"id\":\"1\",\"title\":\"A\",\"status\":\"pending\"}]"));

        boolean changed = ChatFlowOps.completePlanItem(state, null, "1", null);

        assertThat(changed).isTrue();
        assertThat(ChatFlowOps.parsePlan(
                ChatFlowOps.readVariables(state).get(ChatFlowOps.DEFAULT_PLAN_SLOT)).get(0)
                .isPending()).isFalse();
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** Builds a variables-JSON document with a single plan slot holding {@code planJson}. */
    private static String planVars(String slot, String planJson) {
        // Hand-roll the JSON-in-JSON escaping so the test reads the same array
        // shape the engine persists.
        String escaped = planJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"" + slot + "\":\"" + escaped + "\"}";
    }

    private static TurChatFlowState stateWith(String variablesJson) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        state.setVariablesJson(variablesJson);
        return state;
    }
}
