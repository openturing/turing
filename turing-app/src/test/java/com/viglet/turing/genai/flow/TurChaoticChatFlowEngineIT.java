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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil;
import com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry;
import com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ImportedBundle;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * Stress integration test for the chat-flow engine driven by the
 * "Chaotic Chatflow" bundle — a deliberately nonsensical, deliberately
 * gigantic 7-flow graph that touches every node type, attribute, edge,
 * persona register and sub-flow mechanic the engine supports.
 *
 * <p>The bundle is authored with {@code @viglet/turing-flow-dsl} and
 * transpiled to {@code harness-it/chaotic-chatflow.chat-flow.json}; see
 * {@code frontend/packages/flow-dsl/test/fixtures/chaotic-chatflow.ts}.
 *
 * <p>Most assertions are <strong>deterministic</strong> — they ride the
 * engine's transparent-node walker ({@code loadOrInitState}, which runs with
 * a {@code null} aux model) and {@link ChatFlowOps}, so they run under
 * {@code mvn verify} with no LLM/API key. One end-to-end turn is gated on
 * {@code OPENAI_API_KEY} like the sibling engine ITs.
 *
 * <p>What's covered deterministically:
 * <ul>
 *   <li>Bundle import wiring: every {@code subFlowId} resolves to a persisted
 *       flow, personas/slots persisted, personaIds remapped.</li>
 *   <li>The main flow's long transparent golden path in ONE pass: persona
 *       switches, slot SET/DELETE, writeSlot interpolation, two conditions
 *       (YES + a NO branch), exact-match + wildcard switches, a sub-flow
 *       descend+ascend with variable propagation, and a satisfied-question
 *       skip — landing on the first unfilled question.</li>
 *   <li>subFlowSwitch descent + 2-level nesting (sandwich → abyss).</li>
 *   <li>functionCall and scheduleAgent failure-edge routing (T49).</li>
 *   <li>suspend park + resume (T121).</li>
 *   <li>sub-flow cycle detection (self-reference).</li>
 *   <li>switch resolution tiers (exact / substring / wildcard) via
 *       {@link ChatFlowOps#resolveSwitchOption}.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurChaoticChatFlowEngineIT extends AbstractTuringSpringIT {

    private static final String BUNDLE = "/harness-it/chaotic-chatflow.chat-flow.json";

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
    @Autowired
    private TurChatFlowStateRepository stateRepository;
    @Autowired
    private TurAIAgentRepository agentRepository;
    @Autowired
    private TurAIAgentSlotRepository slotRepository;
    @Autowired
    private TurPersonaRepository personaRepository;

    private TurAIAgent agent;
    private ImportedBundle imported;

    // ─────────────────────────── Setup ───────────────────────────

    @BeforeAll
    void importChaoticBundle() throws IOException {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("chaotic-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);

        List<ExportEntry> bundle = ChatFlowImportTestUtil.loadBundleFromClasspath(BUNDLE);
        imported = ChatFlowImportTestUtil.importBundleIntoAgent(agent, bundle,
                new ChatFlowImportTestUtil.Repos(
                        agentRepository, chatFlowRepository, slotRepository, personaRepository));
    }

    // ─────────────────────────── Structural ───────────────────────────

    @Test
    void importsAllSevenFlowsWithPersonasAndSlots() {
        assertThat(imported.flows()).hasSize(7);
        assertThat(imported.byTransientId().keySet())
                .containsExactlyInAnyOrder("chaos-main", "chaos-sandwich", "chaos-abyss",
                        "chaos-paperwork", "chaos-ouroboros", "chaos-scheduler", "chaos-suspend");
        // 5 distinct personas declared on the main flow.
        assertThat(imported.personas()).hasSize(5);
        assertThat(personaRepository.findByNameIgnoreCase("Stamp Clerk")).isPresent();
        assertThat(personaRepository.findByNameIgnoreCase("Abyss Oracle")).isPresent();
        // Slots upserted on the agent (a representative sample).
        assertThat(slotRepository.findByTurAIAgent_IdAndName(agent.getId(), "chaos_key")).isPresent();
        assertThat(slotRepository.findByTurAIAgent_IdAndName(agent.getId(), "email")).isPresent();
    }

    @Test
    void everyFlowParses_everyNodeTypePresent_andEverySubFlowIdResolves() {
        Set<String> persistedIds = new HashSet<>();
        imported.flows().forEach(f -> persistedIds.add(f.getId()));

        Set<String> nodeTypes = new HashSet<>();
        List<String> danglingRefs = new ArrayList<>();
        for (TurChatFlow flow : imported.flows()) {
            ChatFlowGraph graph = engine.parseGraph(flow).orElseThrow(
                    () -> new AssertionError("Flow did not parse: " + flow.getName()));
            for (ChatFlowNode node : graph.nodes()) {
                nodeTypes.add(node.type());
                // subFlow node target must be a real persisted flow id.
                if ("subFlow".equals(node.type()) && node.subFlowId() != null
                        && !node.subFlowId().isBlank() && !persistedIds.contains(node.subFlowId())) {
                    danglingRefs.add(flow.getName() + "/" + node.id() + " → " + node.subFlowId());
                }
                // subFlowSwitch option targets too.
                for (ChatFlowNode.SwitchOption opt : node.switchOptions()) {
                    if (opt.subFlowId() != null && !opt.subFlowId().isBlank()
                            && !persistedIds.contains(opt.subFlowId())) {
                        danglingRefs.add(flow.getName() + "/" + node.id() + " opt " + opt.id()
                                + " → " + opt.subFlowId());
                    }
                }
            }
        }

        assertThat(danglingRefs)
                .as("every subFlowId reference must resolve to a persisted flow after bundle import")
                .isEmpty();
        assertThat(nodeTypes).contains(
                "start", "end", "aiQuestion", "formCapture", "condition", "functionCall",
                "scheduleAgent", "subFlow", "subFlowSwitch", "persona", "switch", "slot",
                "writeSlot", "suspend");
    }

    // ─────────────────────────── Golden path (main flow) ───────────────────────────

    /**
     * The headline test: a single {@code loadOrInitState} on the main flow
     * must walk the entire transparent golden path — persona switches, slot
     * SET/DELETE, writeSlot interpolation, condition YES, condition NO,
     * exact-match switch, wildcard switch, a sub-flow descend+ascend with
     * variable propagation, and a satisfied-question skip — and stop on the
     * first UNFILLED question with a precisely predictable variable map.
     */
    @Test
    void mainFlow_goldenPath_walksTransparentlyToFirstUnfilledQuestion() {
        TurChatFlow main = flow("chaos-main");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, main,
                engine.parseGraph(main).orElseThrow()).orElseThrow();

        // Landed back on the ROOT (main) flow at the first unfilled question.
        assertThat(leaf.getFlow().getId()).isEqualTo(main.getId());
        assertThat(leaf.getParentStateId()).as("ascended fully out of the sub-flow").isNull();
        assertThat(leaf.getCurrentNodeId()).isEqualTo("ask-final");

        Map<String, String> vars = ChatFlowOps.readVariables(leaf);
        assertThat(vars)
                .containsEntry("chaos_key", "alpha")
                .containsEntry("greeting", "Hail alpha traveler")   // writeSlot interpolation
                .containsEntry("second", "from-no")                 // condition NO branch ran
                .containsEntry("sw", "exact")                       // exact-match switch branch
                .containsEntry("stamp", "approved")                 // from descended paperwork sub-flow
                .containsEntry("paperwork", "stamped")              // paperwork condition YES
                .containsEntry("paper_summary", "Form approved for alpha"); // cross-scope interpolation
        // slot DELETE removed the marker the YES branch set earlier.
        assertThat(vars).doesNotContainKey("marker");

        // Last persona walked was the Stamp Clerk inside the paperwork sub-flow.
        TurPersona clerk = personaRepository.findByNameIgnoreCase("Stamp Clerk").orElseThrow();
        assertThat(vars).containsEntry("__activePersonaId", clerk.getId());
    }

    // ─────────────────────────── Sub-flow nesting + functionCall failure ───────────────────────────

    /**
     * Initializing directly on the sandwich flow exercises subFlowSwitch
     * descent (sandwich → abyss), a functionCall whose tool is missing
     * (failure-edge routing), and two-level ascent with variable
     * propagation.
     */
    @Test
    void sandwichFlow_subFlowSwitchDescendsIntoAbyss_andFunctionCallFailureRecovers() {
        TurChatFlow sandwich = flow("chaos-sandwich");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, sandwich,
                engine.parseGraph(sandwich).orElseThrow()).orElseThrow();

        // Walk ran sandwich → (subFlowSwitch) abyss → ascend back to sandwich's end.
        assertThat(leaf.getFlow().getId()).isEqualTo(sandwich.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("end-sandwich");

        Map<String, String> vars = ChatFlowOps.readVariables(leaf);
        assertThat(vars)
                .containsEntry("sandwich", "club")
                .containsEntry("order", "club on rye")          // writeSlot interpolation
                .containsEntry("abyss_depth", "3")              // set INSIDE the descended abyss
                .containsEntry("abyss_fn", "recovered");        // functionCall failure-edge routing
        // The functionCall failed (missing tool) so its outputVariable was never written.
        assertThat(vars).doesNotContainKey("abyss_out");
    }

    // ─────────────────────────── scheduleAgent failure edge ───────────────────────────

    @Test
    void schedulerFlow_scheduleAgentFailure_routesToRecoveryEdge() {
        TurChatFlow scheduler = flow("chaos-scheduler");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, scheduler,
                engine.parseGraph(scheduler).orElseThrow()).orElseThrow();

        // Missing routine → FAILED → continueOnFailure routes the "failure" edge,
        // no JMS enqueue, no parking.
        assertThat(leaf.getCurrentNodeId()).isEqualTo("end-sched");
        Map<String, String> vars = ChatFlowOps.readVariables(leaf);
        assertThat(vars).containsEntry("sched_status", "recovered");
        assertThat(vars).doesNotContainKey("sched_out");
        // No pending marker left behind (fire() bailed before enqueue).
        assertThat(vars.keySet()).noneMatch(k -> k.startsWith("__scheduleAgent_pending_"));
    }

    // ─────────────────────────── suspend + resume ───────────────────────────

    @Test
    void suspendFlow_parksOnSuspendNode_thenResumeWalksPastIt() {
        TurChatFlow suspend = flow("chaos-suspend");
        String conv = newConv();

        TurChatFlowState parked = engine.loadOrInitState(conv, suspend,
                engine.parseGraph(suspend).orElseThrow()).orElseThrow();

        // Parked on the suspend node; the pre-slot ran, the post-slot did not.
        assertThat(parked.getCurrentNodeId()).isEqualTo("suspend-gate");
        assertThat(ChatFlowOps.readVariables(parked))
                .containsEntry("suspend_pre", "before")
                .doesNotContainKey("suspend_post");
        assertThat(engine.findSuspendedReason(conv)).contains("Held for absurd approval");

        // Resume with an external slot update; the walker advances past suspend to the end.
        TurChatFlowEngineService.ResumeResult result =
                engine.resumeSuspendedFlow(conv, Map.of("approval", "granted"), "approved");
        assertThat(result.resumed()).isEqualTo(1);

        TurChatFlowState resumed = stateRepository
                .findByConversationIdAndFlow_Id(conv, suspend.getId()).orElseThrow();
        assertThat(resumed.getCurrentNodeId()).isEqualTo("end-suspend");
        assertThat(ChatFlowOps.readVariables(resumed))
                .containsEntry("suspend_post", "after")   // post-suspend slot ran on resume
                .containsEntry("approval", "granted");     // external slotUpdate applied
        assertThat(engine.findSuspendedReason(conv)).isEmpty();
    }

    // ─────────────────────────── cycle detection ───────────────────────────

    @Test
    void ouroborosFlow_selfReferenceCycle_isRefusedAndTreatedAsNoOp() {
        TurChatFlow ouroboros = flow("chaos-ouroboros");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, ouroboros,
                engine.parseGraph(ouroboros).orElseThrow()).orElseThrow();

        // Cycle refused → subFlow treated as no-op → advance to the end. No child state spawned.
        assertThat(leaf.getFlow().getId()).isEqualTo(ouroboros.getId());
        assertThat(leaf.getParentStateId()).isNull();
        assertThat(leaf.getCurrentNodeId()).isEqualTo("end-ouro");
    }

    // ─────────────────────────── writeSlot missing-var interpolation ───────────────────────────

    @Test
    void paperworkFlow_standalone_writeSlotInterpolatesMissingParentVarAsEmpty() {
        TurChatFlow paperwork = flow("chaos-paperwork");
        String conv = newConv();

        TurChatFlowState leaf = engine.loadOrInitState(conv, paperwork,
                engine.parseGraph(paperwork).orElseThrow()).orElseThrow();

        assertThat(leaf.getCurrentNodeId()).isEqualTo("end-paper");
        Map<String, String> vars = ChatFlowOps.readVariables(leaf);
        // chaos_key is unset when paperwork runs standalone → interpolated to "".
        assertThat(vars)
                .containsEntry("stamp", "approved")
                .containsEntry("paperwork", "stamped")
                .containsEntry("paper_summary", "Form approved for ");
    }

    // ─────────────────────────── switch resolution tiers ───────────────────────────

    @Test
    void switchResolution_exactSubstringWildcard_areDeterministicWithoutAnAuxModel() {
        TurChatFlow main = flow("chaos-main");
        ChatFlowGraph graph = engine.parseGraph(main).orElseThrow();
        ChatFlowNode switchExact = graph.nodeById("switch-exact").orElseThrow();
        ChatFlowNode switchWild = graph.nodeById("switch-wild").orElseThrow();

        // Tier 1 — exact label match (no aux model).
        Optional<ChatFlowNode.SwitchOption> exact =
                ChatFlowOps.resolveSwitchOption(null, switchExact, Map.of("chaos_key", "alpha"));
        assertThat(exact).map(ChatFlowNode.SwitchOption::id).contains("opt-alpha");

        // Tier 2 — substring match: value contains the option label.
        Optional<ChatFlowNode.SwitchOption> substring = ChatFlowOps.resolveSwitchOption(
                null, switchExact, Map.of("chaos_key", "the alpha key, definitely"));
        assertThat(substring).map(ChatFlowNode.SwitchOption::id).contains("opt-alpha");

        // No usable value → empty (the engine then falls through to the wildcard edge).
        Optional<ChatFlowNode.SwitchOption> wildcard =
                ChatFlowOps.resolveSwitchOption(null, switchWild, Map.of());
        assertThat(wildcard).isEmpty();
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private TurChatFlow flow(String transientId) {
        TurChatFlow f = imported.byTransientId().get(transientId);
        if (f == null) {
            throw new IllegalStateException("No imported flow for transient id " + transientId);
        }
        return f;
    }

    private static String newConv() {
        return "conv-" + UUID.randomUUID();
    }
}
