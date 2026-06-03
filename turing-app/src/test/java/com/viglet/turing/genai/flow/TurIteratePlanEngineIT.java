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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.flow.strategy.ChatFlowOps.PlanItem;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * End-to-end engine IT for the T108-2 {@code iteratePlan} node — the
 * descend→ascend→complete loop that the deterministic
 * {@code IteratePlanTest}/{@code TurChatFlowLinterServiceTest} unit suites only
 * exercise piecewise.
 *
 * <p>Drives the real engine through {@link TurChatFlowEngineService#loadOrInitState}
 * with a <strong>null aux model</strong> (no LLM/API key), so the whole loop
 * runs deterministically under {@code mvn verify}:
 *
 * <ol>
 *   <li>A {@code slot} node seeds the {@code __plan} slot with a literal
 *       2-item plan (a {@code planningStep} would need an LLM — out of scope
 *       for a deterministic IT; the seed is equivalent for the loop's
 *       purposes).</li>
 *   <li>An {@code iteratePlan} node descends, once per pending item, into a
 *       fully-transparent body sub-flow (slot + writeSlot + end), which the
 *       engine collapses in the same pass.</li>
 *   <li>On each ascent the engine completes the in-flight item
 *       (mark_done / remove) and re-enters {@code iteratePlan} to pick the
 *       next, finally clearing the iteration markers and advancing on the
 *       outgoing edge.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurIteratePlanEngineIT extends AbstractTuringSpringIT {

    private static final String PLAN_JSON =
            "[{\"id\":\"1\",\"title\":\"First\",\"status\":\"pending\"},"
                    + "{\"id\":\"2\",\"title\":\"Second\",\"status\":\"pending\"}]";

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
    @Autowired
    private TurChatFlowStateRepository stateRepository;
    @Autowired
    private TurAIAgentRepository agentRepository;

    @Test
    void iteratePlan_markDone_walksEveryItemThroughTheBodySubFlow_thenAdvancesOut() {
        TurAIAgent agent = newAgent();
        String bodyId = persistBodyFlow(agent);
        TurChatFlow main = persistMainFlow(agent, bodyId, "mark_done");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, main,
                engine.parseGraph(main).orElseThrow()).orElseThrow();

        // The loop ran to completion and ascended fully back to the main flow,
        // landing on the node after iteratePlan.
        assertThat(leaf.getFlow().getId()).isEqualTo(main.getId());
        assertThat(leaf.getParentStateId()).as("ascended fully out of the body sub-flow").isNull();
        assertThat(leaf.getCurrentNodeId()).isEqualTo("end-main");

        Map<String, String> vars = ChatFlowOps.readVariables(leaf);

        // Every plan item was visited and marked done (the plan is preserved).
        List<PlanItem> plan = ChatFlowOps.parsePlan(vars.get("__plan"));
        assertThat(plan).hasSize(2);
        assertThat(plan).allSatisfy(item -> assertThat(item.isPending()).isFalse());
        assertThat(plan).extracting(PlanItem::title).containsExactly("First", "Second");

        // The body sub-flow ran (at least once) and saw the per-item marker —
        // last_item holds the LAST iterated item's title (override writeSlot).
        assertThat(vars).containsEntry("body_ran", "yes");
        assertThat(vars).containsEntry("last_item", "Second");

        // The reserved iteration markers were cleared on completion.
        assertThat(vars)
                .doesNotContainKey(ChatFlowOps.PLAN_ITEM_ID_SLOT)
                .doesNotContainKey(ChatFlowOps.PLAN_ITEM_TITLE_SLOT);

        // No orphaned child state left behind.
        assertThat(stateRepository.findByConversationIdAndFlow_Id(conv, bodyId)).isEmpty();
    }

    @Test
    void iteratePlan_removeMode_drainsThePlanToEmpty_thenAdvancesOut() {
        TurAIAgent agent = newAgent();
        String bodyId = persistBodyFlow(agent);
        TurChatFlow main = persistMainFlow(agent, bodyId, "remove");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, main,
                engine.parseGraph(main).orElseThrow()).orElseThrow();

        assertThat(leaf.getCurrentNodeId()).isEqualTo("end-main");
        Map<String, String> vars = ChatFlowOps.readVariables(leaf);
        // remove mode drains the plan entirely.
        assertThat(ChatFlowOps.parsePlan(vars.get("__plan"))).isEmpty();
        assertThat(vars).containsEntry("body_ran", "yes");
        assertThat(vars).doesNotContainKey(ChatFlowOps.PLAN_ITEM_ID_SLOT);
    }

    @Test
    void iteratePlan_emptyPlan_advancesOutWithoutDescending() {
        TurAIAgent agent = newAgent();
        String bodyId = persistBodyFlow(agent);
        // Seed an empty plan so iteratePlan finds nothing to iterate.
        TurChatFlow main = persistMainFlow(agent, bodyId, "mark_done", "[]");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, main,
                engine.parseGraph(main).orElseThrow()).orElseThrow();

        assertThat(leaf.getCurrentNodeId()).isEqualTo("end-main");
        // Body never ran (no pending item to descend for).
        assertThat(ChatFlowOps.readVariables(leaf)).doesNotContainKey("body_ran");
    }

    // ─────────────────────────── fixtures ───────────────────────────

    private TurAIAgent newAgent() {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("iterate-plan-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        return agentRepository.save(a);
    }

    /**
     * A fully-transparent body sub-flow: start → slot(body_ran=yes) →
     * writeSlot(last_item={{__planItemTitle}}, override) → end. No interactive
     * node, so the engine collapses the whole body in one transparent pass —
     * letting the iteratePlan loop complete inside a single loadOrInitState.
     */
    private String persistBodyFlow(TurAIAgent agent) {
        String id = "body-" + UUID.randomUUID().toString().substring(0, 8);
        String def = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start-body\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"mark-ran\",\"type\":\"slot\",\"data\":{"
                + "      \"slotName\":\"body_ran\",\"slotOperation\":\"SET\",\"slotValue\":\"yes\"}},"
                + "  {\"id\":\"write-last\",\"type\":\"writeSlot\",\"data\":{"
                + "      \"slotName\":\"last_item\",\"slotValue\":\"{{__planItemTitle}}\","
                + "      \"overrideExistingValue\":true}},"
                + "  {\"id\":\"end-body\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"be1\",\"source\":\"start-body\",\"target\":\"mark-ran\"},"
                + "  {\"id\":\"be2\",\"source\":\"mark-ran\",\"target\":\"write-last\"},"
                + "  {\"id\":\"be3\",\"source\":\"write-last\",\"target\":\"end-body\"}"
                + "]}";
        return persistFlow(agent, id, "body", def).getId();
    }

    private TurChatFlow persistMainFlow(TurAIAgent agent, String bodyId, String completionMode) {
        return persistMainFlow(agent, bodyId, completionMode, PLAN_JSON);
    }

    /**
     * start → slot(seed __plan) → iteratePlan(body=bodyId) → end-main. The seed
     * slot stands in for an upstream planningStep (which would need an LLM).
     */
    private TurChatFlow persistMainFlow(TurAIAgent agent, String bodyId, String completionMode,
            String planJson) {
        String id = "main-" + UUID.randomUUID().toString().substring(0, 8);
        String def = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start-main\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"seed-plan\",\"type\":\"slot\",\"data\":{"
                + "      \"slotName\":\"__plan\",\"slotOperation\":\"SET\",\"slotValue\":\""
                + esc(planJson) + "\"}},"
                + "  {\"id\":\"iter\",\"type\":\"iteratePlan\",\"data\":{"
                + "      \"outputVariable\":\"__plan\",\"subFlowId\":\"" + bodyId + "\","
                + "      \"completionMode\":\"" + completionMode + "\"}},"
                + "  {\"id\":\"end-main\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"me1\",\"source\":\"start-main\",\"target\":\"seed-plan\"},"
                + "  {\"id\":\"me2\",\"source\":\"seed-plan\",\"target\":\"iter\"},"
                + "  {\"id\":\"me3\",\"source\":\"iter\",\"target\":\"end-main\"}"
                + "]}";
        return persistFlow(agent, id, "main", def);
    }

    private TurChatFlow persistFlow(TurAIAgent agent, String id, String name, String definitionJson) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId(id);
        flow.setName(name);
        flow.setEnabled(1);
        flow.setDefinitionJson(definitionJson);
        flow.setTurAIAgent(agent);
        return chatFlowRepository.save(flow);
    }

    /** Escapes a JSON string for embedding as a slot value inside the flow definition JSON. */
    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String newConv() {
        return "conv-" + UUID.randomUUID();
    }
}
