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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.AdvanceResult;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.ObjectMapper;

/**
 * Integration test for the Sub Flow runtime: a chat flow that invokes
 * another chat flow through a {@code subFlow} node. Exercises the engine
 * end-to-end against a real OpenAI {@code gpt-4o-mini} judge so the
 * guardrail decisions match production behavior.
 *
 * <p>Skipped automatically when {@code OPENAI_API_KEY} is not set in the
 * environment — same pattern as the Testcontainers ITs that disable
 * themselves without Docker. Runs under {@code mvn verify} via Failsafe;
 * {@code mvn test} skips it because of the {@code IT} suffix.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurChatFlowSubFlowEngineIT extends AbstractTuringSpringIT {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_MODEL = "gpt-4o-mini";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurChatFlowStateRepository stateRepository;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
    @Autowired
    private TurChatFlowSubmissionRepository submissionRepository;
    @Autowired
    private TurAIAgentRepository agentRepository;

    private ChatModel judgeModel;
    private TurAIAgent agent;

    // ─────────────────────────── Setup ───────────────────────────

    @BeforeAll
    void buildJudgeModel() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        OpenAIClient sync = OpenAIOkHttpClient.builder()
                .baseUrl(OPENAI_BASE_URL).apiKey(apiKey).build();
        OpenAIClientAsync async = OpenAIOkHttpClientAsync.builder()
                .baseUrl(OPENAI_BASE_URL).apiKey(apiKey).build();
        judgeModel = OpenAiChatModel.builder()
                .openAiClient(sync)
                .openAiClientAsync(async)
                .options(OpenAiChatOptions.builder()
                        .model(CHAT_MODEL)
                        .temperature(0.0)
                        .build())
                .build();
    }

    @BeforeEach
    void newAgent(TestInfo info) {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("subflow-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
        System.out.println();
        System.out.println("=== " + info.getDisplayName() + " ===");
    }

    private static void trace(String fmt, Object... args) {
        System.out.println("  · " + String.format(fmt, args));
    }

    private static void traceState(String label, TurChatFlowState state) {
        System.out.printf("  · %-12s flow=%s node=%s parent=%s vars=%s%n",
                label,
                shortFlow(state),
                state.getCurrentNodeId(),
                state.getParentStateId() == null ? "(root)" : state.getParentStateId().substring(0, 8),
                state.getVariablesJson());
    }

    private static String shortFlow(TurChatFlowState state) {
        if (state.getFlow() == null) {
            return "?";
        }
        // Diagnostic only: use the flow id, which is safe to read on a
        // @ManyToOne(LAZY) proxy (the FK is known without initialization). The
        // states traced here include rows loaded via a raw
        // stateRepository.findById(...) (no JOIN FETCH), whose flow is an
        // uninitialized proxy — reading getName() on it would throw outside the
        // session. Production never does that: it reads flows through the
        // engine's parseGraph/resolveFlowForRead.
        String id = state.getFlow().getId();
        return id == null ? "?" : id;
    }

    // ─────────────────────────── Tests ───────────────────────────

    /**
     * 1. Parent flow whose first interactive node IS the Sub Flow node:
     * loadOrInitState walks transparent nodes and lands the active leaf
     * inside the child flow. No LLM call needed (no strategy runs).
     */
    @Test
    void descendsOnEntry_whenStartGoesDirectlyIntoSubFlow() {
        TurChatFlow child = saveFlow("child-1",
                graphAskOnly("askA", "Ask the user for value A.", "valueA"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        TurChatFlow parent = saveFlow("parent-1",
                graphStartSubFlowEnd(child.getId(), child.getName()),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: parent=%s (start→SubFlow→end), child=%s (askA→end)",
                parent.getName(), child.getName());

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("after init", leaf);

        TurChatFlowState parentState = stateRepository.findById(leaf.getParentStateId()).orElseThrow();
        traceState("parent row", parentState);

        assertThat(leaf.getFlow().getId()).isEqualTo(child.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("askA");
        assertThat(leaf.getParentStateId()).isNotNull();
        assertThat(parentState.getFlow().getId()).isEqualTo(parent.getId());
        assertThat(parentState.getCurrentNodeId()).isEqualTo("subFlow1");
    }

    /**
     * 2. Parent flow {@code askName → SubFlow → end}: when the user replies
     * with a name, the LLM judge captures it, the strategy moves the parent
     * onto the SubFlow node, and the engine descends into the child flow.
     */
    @Test
    void descendsAfterParentCapturesValue_andLeafIsSubFlow() {
        TurChatFlow child = saveFlow("child-2",
                graphAskOnly("askEmail", "Ask the user for an email.", "email"),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        TurChatFlow parent = saveFlow("parent-2",
                graphAskThenSubFlow("askName", "Ask the user for a name.", "name",
                        child.getId(), child.getName()),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: parent=%s (askName→SubFlow→end) [LLM_JUDGE], child=%s (askEmail→end) [LLM_JUDGE]",
                parent.getName(), child.getName());

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("after init", leaf);
        assertThat(leaf.getCurrentNodeId()).isEqualTo("askName");

        trace("turn 1: user='Alexandre', assistant='Olá Alexandre!' → calling Judge");
        AdvanceResult result = advance(leaf, "Alexandre", "Olá Alexandre!");
        TurChatFlowState newLeaf = result.state();
        traceState("after t1", newLeaf);

        assertThat(newLeaf.getFlow().getId()).isEqualTo(child.getId());
        assertThat(newLeaf.getCurrentNodeId()).isEqualTo("askEmail");
        assertThat(readVar(newLeaf, "name")).isEqualTo("Alexandre");
        assertThat(newLeaf.getParentStateId()).isNotNull();
    }

    /**
     * 3. Sub-flow chain {@code parent: askName → SubFlow → end},
     * {@code child: askEmail → end}: when the child reaches its end, the
     * engine pops, propagates variables back to the parent, advances the
     * parent past the SubFlow node onto its own end, and records ONE
     * submission for the root flow. Variables collected in both parent and
     * child are visible on the submission.
     */
    @Test
    void ascendsWhenSubFlowReachesEnd_andSharesVariablesBackToParent() {
        TurChatFlow child = saveFlow("child-3",
                graphAskOnly("askEmail", "Ask the user for an email.", "email"),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        TurChatFlow parent = saveFlow("parent-3",
                graphAskThenSubFlow("askName", "Ask the user for a name.", "name",
                        child.getId(), child.getName()),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: parent=%s (askName→SubFlow→end), child=%s (askEmail→end), both LLM_JUDGE",
                parent.getName(), child.getName());

        String conv = newConversationId();
        TurChatFlowState parentLeaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("after init", parentLeaf);

        trace("turn 1: user='Alexandre' → expect descent into child");
        TurChatFlowState afterDescend = advance(parentLeaf, "Alexandre", "Olá Alexandre!").state();
        traceState("after t1", afterDescend);

        trace("turn 2: user='alexandre@viglet.com' → expect ascend + cascade to parent's end");
        AdvanceResult ascended = advance(afterDescend, "alexandre@viglet.com", "Pronto, anotei.");
        TurChatFlowState rootLeaf = ascended.state();
        traceState("after t2", rootLeaf);
        trace("child row deleted? %s", stateRepository.findById(afterDescend.getId()).isEmpty());
        trace("submissions(parent)=%d, submissions(child)=%d",
                submissionRepository.findByFlow_IdOrderByCompletedAtDesc(parent.getId()).size(),
                submissionRepository.findByFlow_IdOrderByCompletedAtDesc(child.getId()).size());

        assertThat(rootLeaf.getFlow().getId()).isEqualTo(parent.getId());
        assertThat(rootLeaf.getParentStateId()).isNull();
        assertThat(rootLeaf.getCurrentNodeId()).isEqualTo("endParent");
        assertThat(readVar(rootLeaf, "name")).isEqualTo("Alexandre");
        assertThat(readVar(rootLeaf, "email")).isEqualTo("alexandre@viglet.com");

        assertThat(stateRepository.findById(afterDescend.getId())).isEmpty();
        List<TurChatFlowSubmission> submissions = submissionRepository
                .findByFlow_IdOrderByCompletedAtDesc(parent.getId());
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getEndNodeId()).isEqualTo("endParent");
        assertThat(submissionRepository.findByFlow_IdOrderByCompletedAtDesc(child.getId()))
                .as("sub-flow should not record a submission")
                .isEmpty();
    }

    /**
     * 4. Three-level chain {@code A.start → SubFlow(B); B.start → SubFlow(C);
     * C.start → askX → end}: a single {@code loadOrInitState} on A descends
     * straight through B into C, building the parent chain A→B→C.
     */
    @Test
    void descendsThroughThreeLevels_whenStartChainsAreSubFlows() {
        TurChatFlow flowC = saveFlow("flow-C",
                graphAskOnly("askX", "Ask the user for X.", "x"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        TurChatFlow flowB = saveFlow("flow-B",
                graphStartSubFlowEnd(flowC.getId(), flowC.getName()),
                TurChatFlowGuardrailMethod.HEURISTIC);
        TurChatFlow flowA = saveFlow("flow-A",
                graphStartSubFlowEnd(flowB.getId(), flowB.getName()),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: A→SubFlow(B), B→SubFlow(C), C→askX→end");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flowA,
                engine.parseGraph(flowA).orElseThrow()).orElseThrow();
        traceState("leaf", leaf);

        TurChatFlowState bState = stateRepository.findById(leaf.getParentStateId()).orElseThrow();
        traceState("level B", bState);
        TurChatFlowState aState = stateRepository.findById(bState.getParentStateId()).orElseThrow();
        traceState("level A", aState);

        assertThat(leaf.getFlow().getId()).isEqualTo(flowC.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("askX");
        assertThat(bState.getFlow().getId()).isEqualTo(flowB.getId());
        assertThat(aState.getFlow().getId()).isEqualTo(flowA.getId());
        assertThat(aState.getParentStateId()).isNull();
    }

    /**
     * 5. {@code parent: SubFlow → end}, {@code child: askY → end}: when the
     * child reaches its end, the cascade ascent moves the parent past the
     * SubFlow node onto its own end in the same turn and records the root
     * submission immediately.
     */
    @Test
    void cascadeAscent_whenSubFlowEndsAndParentNextIsAlsoEnd_recordsRootSubmission() {
        TurChatFlow child = saveFlow("child-5",
                graphAskOnly("askY", "Ask the user for Y.", "y"),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        TurChatFlow parent = saveFlow("parent-5",
                graphStartSubFlowEnd(child.getId(), child.getName()),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: parent=%s (start→SubFlow→end) [LLM_JUDGE], child=%s (askY→end) [LLM_JUDGE]",
                parent.getName(), child.getName());

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("after init", leaf);
        assertThat(leaf.getFlow().getId()).isEqualTo(child.getId());

        trace("turn 1: user='the y value' → expect cascade ascent (child end → parent end)");
        AdvanceResult result = advance(leaf, "the y value", "Anotado.");
        TurChatFlowState rootLeaf = result.state();
        traceState("after t1", rootLeaf);
        trace("submissions(parent)=%d (expect 1)",
                submissionRepository.findByFlow_IdOrderByCompletedAtDesc(parent.getId()).size());

        assertThat(rootLeaf.getFlow().getId()).isEqualTo(parent.getId());
        assertThat(rootLeaf.getParentStateId()).isNull();
        assertThat(rootLeaf.getCurrentNodeId()).isEqualTo("endParent");
        assertThat(submissionRepository.findByFlow_IdOrderByCompletedAtDesc(parent.getId()))
                .hasSize(1);
    }

    /**
     * 6. Sub Flow node pointing back at its own owning flow: cycle detection
     * refuses the descent and the engine treats the node as a no-op,
     * advancing the parent past it.
     */
    @Test
    void refusesDirectRecursion_andTreatsSubFlowAsNoOp() {
        TurChatFlow self = saveFlow("self-6",
                graphStartSubFlowEnd("__SELF__", "self"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        self.setDefinitionJson(self.getDefinitionJson().replace("__SELF__", self.getId()));
        self = chatFlowRepository.save(self);
        trace("Setup: flow %s has SubFlow pointing at itself (cycle)", self.getName());

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, self,
                engine.parseGraph(self).orElseThrow()).orElseThrow();
        traceState("leaf", leaf);
        trace("expectation: cycle refused, SubFlow treated as no-op, leaf at endParent");

        assertThat(leaf.getFlow().getId()).isEqualTo(self.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("endParent");
        assertThat(leaf.getParentStateId()).isNull();
    }

    /**
     * 7. Indirect recursion through a second flow: A → B → A. Detection
     * fires on the second descent attempt and B's SubFlow node is treated
     * as a no-op.
     */
    @Test
    void refusesIndirectRecursion_throughSecondaryFlow() {
        TurChatFlow flowA = saveFlow("A-7",
                graphStartSubFlowEnd("__B__", "B"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        TurChatFlow flowB = saveFlow("B-7",
                graphStartSubFlowEnd(flowA.getId(), flowA.getName()),
                TurChatFlowGuardrailMethod.HEURISTIC);
        flowA.setDefinitionJson(flowA.getDefinitionJson().replace("__B__", flowB.getId()));
        flowA = chatFlowRepository.save(flowA);
        trace("Setup: A→SubFlow(B), B→SubFlow(A)  (indirect cycle)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flowA,
                engine.parseGraph(flowA).orElseThrow()).orElseThrow();
        traceState("leaf", leaf);
        trace("expected path: descend A→B, refuse B→A, ascend B→A; leaf at A's endParent");

        assertThat(leaf.getFlow().getId()).isEqualTo(flowA.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("endParent");
        assertThat(leaf.getParentStateId()).isNull();
    }

    /**
     * 8. Sub Flow node with a blank {@code subFlowId} (author started
     * dragging but never picked a flow): the engine treats it as a no-op
     * and advances past it.
     */
    @Test
    void treatsSubFlowWithoutIdAsNoOp_andAdvancesPastIt() {
        TurChatFlow flow = saveFlow("blank-8",
                graphStartSubFlowEnd("", ""),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: SubFlow node with subFlowId='' (author hasn't picked yet)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        traceState("leaf", leaf);

        assertThat(leaf.getFlow().getId()).isEqualTo(flow.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("endParent");
    }

    /**
     * 9. Sub Flow node referencing a disabled flow: skipped as no-op.
     */
    @Test
    void treatsSubFlowReferencingDisabledFlowAsNoOp() {
        TurChatFlow disabled = saveFlow("disabled-9",
                graphAskOnly("askZ", "Ask Z.", "z"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        disabled.setEnabled(0);
        disabled = chatFlowRepository.save(disabled);

        TurChatFlow parent = saveFlow("parent-9",
                graphStartSubFlowEnd(disabled.getId(), disabled.getName()),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: SubFlow targets a disabled flow (enabled=0)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("leaf", leaf);

        assertThat(leaf.getFlow().getId()).isEqualTo(parent.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("endParent");
    }

    /**
     * 10. Sub Flow node referencing a non-existent flow id: skipped as no-op.
     */
    @Test
    void treatsSubFlowReferencingMissingFlowAsNoOp() {
        TurChatFlow parent = saveFlow("parent-10",
                graphStartSubFlowEnd("ghost-flow-id-that-doesnt-exist", "ghost"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: SubFlow targets a non-existent flow id");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("leaf", leaf);

        assertThat(leaf.getFlow().getId()).isEqualTo(parent.getId());
        assertThat(leaf.getCurrentNodeId()).isEqualTo("endParent");
    }

    /**
     * 11. {@code selectActiveFlow} continuation: when a sub-flow chain is
     * already in progress, the router returns the leaf (child) state,
     * not the parked parent. The router LLM is never consulted.
     */
    @Test
    void selectActiveFlowReturnsLeaf_notRoot_whenChainExists() {
        TurChatFlow child = saveFlow("child-11",
                graphAskOnly("askW", "Ask W.", "w"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        TurChatFlow parent = saveFlow("parent-11",
                graphStartSubFlowEnd(child.getId(), child.getName()),
                TurChatFlowGuardrailMethod.HEURISTIC);

        String conv = newConversationId();
        TurChatFlowState seeded = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("seeded", seeded);
        assertThat(seeded.getFlow().getId()).isEqualTo(child.getId());

        Optional<TurChatFlowEngineService.FlowSelection> picked = engine
                .selectActiveFlow(agent, conv, "any user message", null);
        trace("selectActiveFlow returned: present=%s, flow=%s, stateId=%s",
                picked.isPresent(),
                picked.map(s -> s.flow().getName()).orElse("?"),
                picked.map(s -> s.state().getId()).orElse("?"));

        assertThat(picked).isPresent();
        assertThat(picked.get().flow().getId()).isEqualTo(child.getId());
        assertThat(picked.get().state().getId()).isEqualTo(seeded.getId());
    }

    /**
     * 12. {@code resetAllStatesForAgent} cleans the full chain (parent +
     * children) — important for the public "New chat" reset path that
     * doesn't know which flow auto-router picked.
     */
    @Test
    void resetAllStatesForAgent_deletesEveryStateInTheChain() {
        TurChatFlow child = saveFlow("child-12",
                graphAskOnly("askV", "Ask V.", "v"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        TurChatFlow parent = saveFlow("parent-12",
                graphStartSubFlowEnd(child.getId(), child.getName()),
                TurChatFlowGuardrailMethod.HEURISTIC);

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("leaf (child)", leaf);
        String parentStateId = leaf.getParentStateId();
        String childStateId = leaf.getId();
        trace("rows before reset: parent=%s, child=%s",
                parentStateId.substring(0, 8), childStateId.substring(0, 8));

        int deleted = engine.resetAllStatesForAgent(conv, agent.getId());
        trace("resetAllStatesForAgent deleted %d row(s)", deleted);

        assertThat(deleted).isEqualTo(2);
        assertThat(stateRepository.findById(parentStateId)).isEmpty();
        assertThat(stateRepository.findById(childStateId)).isEmpty();
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private AdvanceResult advance(TurChatFlowState leaf,
            String userMessage, String assistantMessage) {
        TurChatFlow leafFlow = leaf.getFlow();
        ChatFlowGraph leafGraph = engine.parseGraph(leafFlow).orElseThrow();
        return engine.advance(leafFlow, leaf, leafGraph, userMessage, assistantMessage, judgeModel);
    }

    private static String readVar(TurChatFlowState state, String key) {
        @SuppressWarnings("unchecked")
        Map<String, String> vars = JSON.readValue(
                state.getVariablesJson() == null ? "{}" : state.getVariablesJson(),
                Map.class);
        return vars.get(key);
    }

    private static String newConversationId() {
        return "conv-" + UUID.randomUUID();
    }

    private TurChatFlow saveFlow(String name, String definitionJson,
            TurChatFlowGuardrailMethod guardrail) {
        TurChatFlow f = new TurChatFlow();
        f.setName(name + "-" + UUID.randomUUID().toString().substring(0, 6));
        f.setDefinitionJson(definitionJson);
        f.setEnabled(1);
        f.setGuardrailMethod(guardrail);
        f.setTurAIAgent(agent);
        return chatFlowRepository.save(f);
    }

    // ─────────── Graph builders (JSON definition) ───────────

    /** {@code start → ask{x} → endChild}. Single AI Question step. */
    private static String graphAskOnly(String askId, String aiInstruction, String outputVariable) {
        Map<String, Object> startData = nodeData("START", "start", null);
        Map<String, Object> askData = nodeData("ASK", "aiQuestion",
                Map.of("aiInstruction", aiInstruction, "outputVariable", outputVariable));
        Map<String, Object> endData = nodeData("END", "end", null);

        List<Map<String, Object>> nodes = List.of(
                node("startChild", "start", startData),
                node(askId, "aiQuestion", askData),
                node("endChild", "end", endData));
        List<Map<String, Object>> edges = List.of(
                edge("eStart", "startChild", askId),
                edge("eEnd", askId, "endChild"));
        return serialize(nodes, edges);
    }

    /** {@code start → subFlow1(child) → endParent}. */
    private static String graphStartSubFlowEnd(String subFlowId, String subFlowName) {
        Map<String, Object> startData = nodeData("START", "start", null);
        Map<String, Object> subFlowData = nodeData("SUB FLOW", "subFlow",
                Map.of("subFlowId", subFlowId, "subFlowName", subFlowName));
        Map<String, Object> endData = nodeData("END", "end", null);

        List<Map<String, Object>> nodes = List.of(
                node("startParent", "start", startData),
                node("subFlow1", "subFlow", subFlowData),
                node("endParent", "end", endData));
        List<Map<String, Object>> edges = List.of(
                edge("eStart", "startParent", "subFlow1"),
                edge("eEnd", "subFlow1", "endParent"));
        return serialize(nodes, edges);
    }

    /** {@code start → ask{x} → subFlow1(child) → endParent}. */
    private static String graphAskThenSubFlow(String askId, String aiInstruction,
            String outputVariable, String subFlowId, String subFlowName) {
        Map<String, Object> startData = nodeData("START", "start", null);
        Map<String, Object> askData = nodeData("ASK", "aiQuestion",
                Map.of("aiInstruction", aiInstruction, "outputVariable", outputVariable));
        Map<String, Object> subFlowData = nodeData("SUB FLOW", "subFlow",
                Map.of("subFlowId", subFlowId, "subFlowName", subFlowName));
        Map<String, Object> endData = nodeData("END", "end", null);

        List<Map<String, Object>> nodes = List.of(
                node("startParent", "start", startData),
                node(askId, "aiQuestion", askData),
                node("subFlow1", "subFlow", subFlowData),
                node("endParent", "end", endData));
        List<Map<String, Object>> edges = List.of(
                edge("eStart", "startParent", askId),
                edge("eAsk", askId, "subFlow1"),
                edge("eEnd", "subFlow1", "endParent"));
        return serialize(nodes, edges);
    }

    private static Map<String, Object> nodeData(String label, String type, Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", label);
        data.put("type", type);
        if (extra != null) {
            data.putAll(extra);
        }
        return data;
    }

    private static Map<String, Object> node(String id, String type, Map<String, Object> data) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("data", data);
        return node;
    }

    private static Map<String, Object> edge(String id, String source, String target) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("sourceHandle", null);
        edge.put("targetHandle", null);
        edge.put("label", null);
        return edge;
    }

    private static String serialize(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return JSON.writeValueAsString(graph);
    }
}
