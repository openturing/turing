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
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.ObjectMapper;

/**
 * Integration test for the {@code persona} node runtime: a transparent step that writes
 * {@code __activePersonaId} into the flow state so {@code TurAgentPersonaResolver} can
 * pick up the new voice on the next turn. Covers entry-time activation, mid-flow
 * persona switches, persistence across question turns, the no-op case (blank id),
 * and persona propagation through a sub-flow descent / ascent.
 *
 * <p>Like the other engine ITs, drives the engine with real OpenAI {@code gpt-4o-mini}
 * (temperature 0). Skipped automatically when {@code OPENAI_API_KEY} is not set.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurChatFlowPersonaEngineIT extends AbstractTuringSpringIT {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_MODEL = "gpt-4o-mini";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Mirror of {@code TurAgentPersonaResolver.ACTIVE_PERSONA_VAR} — kept as a literal
     * here because the resolver lives in a different package and we want the test to
     * fail loudly if the variable name ever drifts.
     */
    private static final String ACTIVE_PERSONA_VAR = "__activePersonaId";

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurChatFlowStateRepository stateRepository;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
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
        a.setTitle("persona-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
        System.out.println();
        System.out.println("=== " + info.getDisplayName() + " ===");
    }

    private static void trace(String fmt, Object... args) {
        System.out.println("  · " + String.format(fmt, args));
    }

    private static void traceState(String label, TurChatFlowState state) {
        System.out.printf("  · %-12s flow=%s node=%s vars=%s%n",
                label,
                state.getFlow() == null ? "?" : state.getFlow().getName(),
                state.getCurrentNodeId(),
                state.getVariablesJson());
    }

    // ─────────────────────────── Tests ───────────────────────────

    /**
     * Flow {@code start → persona(persona-a) → askName → end}: at {@code loadOrInitState}
     * the engine walks past the persona node transparently, writing the active-persona
     * marker before settling on the first interactive node ({@code askName}). No LLM
     * call is needed for the persona itself — only the AI Question downstream would
     * run a strategy turn.
     */
    @Test
    void personaAtStart_writesActivePersonaAndAdvancesToFirstQuestion() {
        TurChatFlow flow = saveFlow("persona-entry",
                graphPersonaThenAsk("persona-a", "askName",
                        "Ask the user for their name."),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: start → persona(persona-a) → askName → end");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        traceState("after init", leaf);

        assertThat(leaf.getCurrentNodeId())
                .as("Persona node is transparent; engine should land on the AI Question")
                .isEqualTo("askName");
        assertThat(readVar(leaf, ACTIVE_PERSONA_VAR))
                .as("Active persona must be set on entry")
                .isEqualTo("persona-a");
    }

    /**
     * Flow {@code start → persona(p1) → askA → persona(p2) → askB → end}: after the user
     * answers {@code askA}, the engine traverses persona(p2) on its way to {@code askB},
     * overwriting the active persona. End state should carry {@code p2}, not {@code p1}.
     */
    @Test
    void personaSwitchedMidFlow_overwritesActivePersona() {
        TurChatFlow flow = saveFlow("persona-mid",
                graphTwoPersonas("persona-1", "askA", "Ask the user for value A.", "valueA",
                        "persona-2", "askB", "Ask the user for value B.", "valueB"),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: persona-1 → askA → persona-2 → askB");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        traceState("after init", leaf);
        assertThat(leaf.getCurrentNodeId()).isEqualTo("askA");
        assertThat(readVar(leaf, ACTIVE_PERSONA_VAR)).isEqualTo("persona-1");

        trace("turn 1: user='alpha' → judge captures, walk traverses persona-2 → askB");
        AdvanceResult result = advance(leaf, "alpha", "Got it.");
        TurChatFlowState newLeaf = result.state();
        traceState("after t1", newLeaf);

        assertThat(newLeaf.getCurrentNodeId()).isEqualTo("askB");
        assertThat(readVar(newLeaf, ACTIVE_PERSONA_VAR))
                .as("Second persona node must overwrite the first")
                .isEqualTo("persona-2");
        assertThat(readVar(newLeaf, "valueA"))
                .as("askA's capture must remain alongside the new persona")
                .isEqualToIgnoringCase("alpha");
    }

    /**
     * Persona node with a blank {@code personaId} is a no-op: the engine still advances
     * past it, but does not write {@code __activePersonaId} — exposing an authoring
     * mistake silently would risk picking up a stale active persona from earlier in
     * the conversation.
     */
    @Test
    void personaWithBlankId_isNoOp_andDoesNotPolluteVariables() {
        TurChatFlow flow = saveFlow("persona-blank",
                graphPersonaThenAsk("", "askName", "Ask the user for their name."),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: persona(\"\") → askName (engine should skip persona silently)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        traceState("after init", leaf);

        assertThat(leaf.getCurrentNodeId())
                .as("Blank-id persona is still transparent — should land on askName")
                .isEqualTo("askName");
        assertThat(readVar(leaf, ACTIVE_PERSONA_VAR))
                .as("Blank persona id must not be written into variables")
                .isNull();
    }

    /**
     * Persona variables are sticky across normal interactive turns: once written, a
     * subsequent question does not erase them. Validates that the strategy's variable
     * merging preserves {@code __activePersonaId} when the AI Question writes its own
     * capture.
     */
    @Test
    void activePersona_persistsAcrossQuestionTurns() {
        TurChatFlow flow = saveFlow("persona-sticky",
                graphPersonaAskAsk("persona-sticky", "askA", "Ask for value A.", "valueA",
                        "askB", "Ask for value B.", "valueB"),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: persona(persona-sticky) → askA → askB (no second persona node)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        assertThat(readVar(leaf, ACTIVE_PERSONA_VAR)).isEqualTo("persona-sticky");

        trace("turn 1: user='alpha' → judge captures, advance to askB");
        TurChatFlowState afterT1 = advance(leaf, "alpha", "Got it.").state();
        traceState("after t1", afterT1);
        assertThat(afterT1.getCurrentNodeId()).isEqualTo("askB");
        assertThat(readVar(afterT1, ACTIVE_PERSONA_VAR))
                .as("Persona must survive one question turn (no other persona node passed)")
                .isEqualTo("persona-sticky");

        trace("turn 2: user='beta' → judge captures, advance to end");
        TurChatFlowState afterT2 = advance(afterT1, "beta", "Anotado.").state();
        traceState("after t2", afterT2);
        assertThat(readVar(afterT2, ACTIVE_PERSONA_VAR))
                .as("Persona still alive after the second question turn")
                .isEqualTo("persona-sticky");
    }

    /**
     * Persona node inside a sub-flow: when the child sets a persona then reaches end,
     * the ascent must carry the {@code __activePersonaId} back to the parent state.
     * This is essential for the "switch routes to persona-then-subflow" pattern in the
     * Cosmic Concierge example, where the parent inherits the voice the sub-flow
     * picked.
     */
    @Test
    void personaInsideSubFlow_propagatesBackToParentOnAscent() {
        TurChatFlow child = saveFlow("persona-child",
                graphPersonaThenAsk("persona-from-child", "askChild",
                        "Ask the user for a child value."),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        TurChatFlow parent = saveFlow("persona-parent",
                graphStartSubFlowEnd(child.getId(), child.getName()),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: parent (start→SubFlow→end) → child (persona→askChild→end)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, parent,
                engine.parseGraph(parent).orElseThrow()).orElseThrow();
        traceState("after init", leaf);
        // The child's persona node fires during descent, so even before the user types
        // anything, the leaf state already carries the active persona.
        assertThat(leaf.getCurrentNodeId()).isEqualTo("askChild");
        assertThat(readVar(leaf, ACTIVE_PERSONA_VAR)).isEqualTo("persona-from-child");

        trace("turn 1: user='child value' → judge captures, child ends, ascent to parent");
        AdvanceResult result = advance(leaf, "child value", "Anotado.");
        TurChatFlowState rootLeaf = result.state();
        traceState("after t1", rootLeaf);

        assertThat(rootLeaf.getFlow().getId())
                .as("Ascent should land back on the parent flow")
                .isEqualTo(parent.getId());
        assertThat(readVar(rootLeaf, ACTIVE_PERSONA_VAR))
                .as("Persona set inside the child must survive the ascent into the parent")
                .isEqualTo("persona-from-child");
    }

    /**
     * Three consecutive persona nodes — common when the author wants to layer
     * conditional voice changes. Each one overwrites the previous; only the LAST
     * persona before an interactive node wins.
     */
    @Test
    void personaChain_lastWriterWins() {
        TurChatFlow flow = saveFlow("persona-chain",
                graphPersonaChain(List.of("first", "second", "third"),
                        "askLeaf", "Ask for the leaf value.", "leaf_value"),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: start → persona(first) → persona(second) → persona(third) → askLeaf");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        traceState("after init", leaf);

        assertThat(leaf.getCurrentNodeId()).isEqualTo("askLeaf");
        assertThat(readVar(leaf, ACTIVE_PERSONA_VAR))
                .as("Last persona node in the chain must be the surviving active persona")
                .isEqualTo("third");
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

    // ─────────── Graph builders ───────────

    /** {@code start → persona(personaId) → ask(outputVariable='value') → end}. */
    private static String graphPersonaThenAsk(String personaId, String askId, String aiInstruction) {
        List<Map<String, Object>> nodes = List.of(
                node("start", "start", nodeData("START", "start", null)),
                node("persona", "persona", nodeData("PERSONA", "persona",
                        Map.of("personaId", personaId))),
                node(askId, "aiQuestion", nodeData("ASK", "aiQuestion",
                        Map.of("aiInstruction", aiInstruction, "outputVariable", "value"))),
                node("end", "end", nodeData("END", "end", null)));
        List<Map<String, Object>> edges = List.of(
                edge("e1", "start", "persona"),
                edge("e2", "persona", askId),
                edge("e3", askId, "end"));
        return serialize(nodes, edges);
    }

    /** {@code start → persona(p1) → askA → persona(p2) → askB → end}. */
    private static String graphTwoPersonas(String personaA, String askAId, String askAInstr, String askAVar,
            String personaB, String askBId, String askBInstr, String askBVar) {
        List<Map<String, Object>> nodes = List.of(
                node("start", "start", nodeData("START", "start", null)),
                node("personaA", "persona", nodeData("PERSONA-A", "persona",
                        Map.of("personaId", personaA))),
                node(askAId, "aiQuestion", nodeData("ASK-A", "aiQuestion",
                        Map.of("aiInstruction", askAInstr, "outputVariable", askAVar))),
                node("personaB", "persona", nodeData("PERSONA-B", "persona",
                        Map.of("personaId", personaB))),
                node(askBId, "aiQuestion", nodeData("ASK-B", "aiQuestion",
                        Map.of("aiInstruction", askBInstr, "outputVariable", askBVar))),
                node("end", "end", nodeData("END", "end", null)));
        List<Map<String, Object>> edges = List.of(
                edge("e1", "start", "personaA"),
                edge("e2", "personaA", askAId),
                edge("e3", askAId, "personaB"),
                edge("e4", "personaB", askBId),
                edge("e5", askBId, "end"));
        return serialize(nodes, edges);
    }

    /** {@code start → persona(p) → askA → askB → end} (single persona, two questions). */
    private static String graphPersonaAskAsk(String personaId,
            String askAId, String askAInstr, String askAVar,
            String askBId, String askBInstr, String askBVar) {
        List<Map<String, Object>> nodes = List.of(
                node("start", "start", nodeData("START", "start", null)),
                node("persona", "persona", nodeData("PERSONA", "persona",
                        Map.of("personaId", personaId))),
                node(askAId, "aiQuestion", nodeData("ASK-A", "aiQuestion",
                        Map.of("aiInstruction", askAInstr, "outputVariable", askAVar))),
                node(askBId, "aiQuestion", nodeData("ASK-B", "aiQuestion",
                        Map.of("aiInstruction", askBInstr, "outputVariable", askBVar))),
                node("end", "end", nodeData("END", "end", null)));
        List<Map<String, Object>> edges = List.of(
                edge("e1", "start", "persona"),
                edge("e2", "persona", askAId),
                edge("e3", askAId, askBId),
                edge("e4", askBId, "end"));
        return serialize(nodes, edges);
    }

    /** Several persona nodes in a row, then a single AI Question. */
    private static String graphPersonaChain(List<String> personaIds,
            String askId, String aiInstruction, String outputVariable) {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();
        nodes.add(node("start", "start", nodeData("START", "start", null)));
        String previousId = "start";
        for (int i = 0; i < personaIds.size(); i++) {
            String pid = "persona-" + i;
            nodes.add(node(pid, "persona", nodeData("PERSONA-" + i, "persona",
                    Map.of("personaId", personaIds.get(i)))));
            edges.add(edge("e-" + i, previousId, pid));
            previousId = pid;
        }
        nodes.add(node(askId, "aiQuestion", nodeData("ASK", "aiQuestion",
                Map.of("aiInstruction", aiInstruction, "outputVariable", outputVariable))));
        edges.add(edge("e-final-ask", previousId, askId));
        nodes.add(node("end", "end", nodeData("END", "end", null)));
        edges.add(edge("e-end", askId, "end"));
        return serialize(nodes, edges);
    }

    /** {@code start → subFlow1(child) → endParent}, mirrors the SubFlow IT helper. */
    private static String graphStartSubFlowEnd(String subFlowId, String subFlowName) {
        List<Map<String, Object>> nodes = List.of(
                node("startParent", "start", nodeData("START", "start", null)),
                node("subFlow1", "subFlow", nodeData("SUB FLOW", "subFlow",
                        Map.of("subFlowId", subFlowId, "subFlowName", subFlowName))),
                node("endParent", "end", nodeData("END", "end", null)));
        List<Map<String, Object>> edges = List.of(
                edge("eStart", "startParent", "subFlow1"),
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
