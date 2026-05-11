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
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.ObjectMapper;

/**
 * Integration test for the {@code switch} node runtime: multi-way branching driven by an
 * upstream variable that classifies into one of several option labels. Covers the three
 * resolution tiers ({@code exact match}, {@code substring match}, {@code LLM classifier}),
 * the wildcard fallback, missing-variable defensive behavior, nested switches, and
 * interop with the AI Question that captures the routing variable.
 *
 * <p>Like {@link TurChatFlowSubFlowEngineIT}, the test drives the engine with real OpenAI
 * {@code gpt-4o-mini} (temperature 0) so guardrail decisions match production and the
 * LLM classifier exercises a real model. Skipped automatically when
 * {@code OPENAI_API_KEY} is not set.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TurChatFlowSwitchEngineIT extends AbstractTuringSpringIT {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_MODEL = "gpt-4o-mini";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TurChatFlowEngineService engine;
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
        a.setTitle("switch-it-" + UUID.randomUUID().toString().substring(0, 8));
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
     * Tier 1 of the switch classifier: when the variable value matches an option's label
     * exactly (case-insensitively, trimmed), routing must follow that option's edge with
     * NO LLM round-trip. The IT uses the AI question to capture the variable from a clean
     * "red" reply; the judge sets {@code color=red} and the engine's
     * {@code walkThroughSwitches} should immediately resolve to the {@code opt-red} branch.
     */
    @Test
    void switchExactMatch_routesToConfiguredBranch() {
        TurChatFlow flow = saveFlow("switch-exact",
                graphAskThenSwitchTwoBranches(
                        "color",
                        "What is your favorite color, red or blue?",
                        List.of(option("opt-red", "red"), option("opt-blue", "blue")),
                        Map.of("opt-red", "askRed", "opt-blue", "askBlue"),
                        Map.of("askRed", "Tell me which shade of red.",
                                "askBlue", "Tell me which shade of blue.")),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: askColor → switch(opt-red|opt-blue) → askRed|askBlue → end");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        assertThat(leaf.getCurrentNodeId()).isEqualTo("askColor");

        trace("turn 1: user='red' → judge captures color='red' → switch exact match → askRed");
        AdvanceResult result = advance(leaf, "red", "Cor anotada.");
        TurChatFlowState newLeaf = result.state();
        traceState("after t1", newLeaf);

        assertThat(readVar(newLeaf, "color")).isEqualToIgnoringCase("red");
        assertThat(newLeaf.getCurrentNodeId())
                .as("Exact label match should route to opt-red's edge target")
                .isEqualTo("askRed");
    }

    /**
     * Tier 2: variable value contains the option's label as a substring (or vice-versa).
     * The classifier picks the longest matching option so "Beach Getaway" beats "Beach"
     * — but here we keep labels simple and test that a multi-word user reply still
     * matches a single-word option.
     */
    @Test
    void switchSubstringMatch_routesToBestFit() {
        TurChatFlow flow = saveFlow("switch-substring",
                graphAskThenSwitchTwoBranches(
                        "fruit",
                        "What fruit do you crave today, mango or banana?",
                        List.of(option("opt-mango", "mango"), option("opt-banana", "banana")),
                        Map.of("opt-mango", "askMango", "opt-banana", "askBanana"),
                        Map.of("askMango", "Tell me about the mango.",
                                "askBanana", "Tell me about the banana.")),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: askFruit → switch(opt-mango|opt-banana) → askMango|askBanana → end");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();

        // Long answer; even if the judge captures the whole sentence verbatim, the
        // substring rule will pick "mango" out of it without an LLM round-trip.
        trace("turn 1: user='I would love a juicy mango please' → substring match → askMango");
        AdvanceResult result = advance(leaf,
                "I would love a juicy mango please", "Pedido anotado.");
        TurChatFlowState newLeaf = result.state();
        traceState("after t1", newLeaf);

        assertThat(newLeaf.getCurrentNodeId()).isEqualTo("askMango");
    }

    /**
     * Tier 3: the user phrases the answer in a way the cheap matches can't catch
     * ("I want to climb something high" vs. "Mountain Expedition"). The LLM classifier
     * should pick {@code opt-mountain} and the engine should route accordingly.
     *
     * <p>Uses {@code HEURISTIC} guardrail on the upstream AI Question so the captured
     * variable contains the raw user phrase — the strict LLM-Judge contract would
     * otherwise reject any answer that doesn't echo one of the option labels verbatim,
     * leaving the variable unset and the switch with nothing to classify. This test's
     * focus is the switch's classifier, not the question's judge.
     */
    @Test
    void switchLlmClassifier_fallbackForFuzzyAnswer() {
        TurChatFlow flow = saveFlow("switch-llm",
                graphAskThenSwitchTwoBranches(
                        "adventure_type",
                        "What kind of adventure are you craving — a beach getaway or a mountain expedition?",
                        List.of(option("opt-beach", "Beach Getaway"),
                                option("opt-mountain", "Mountain Expedition")),
                        Map.of("opt-beach", "askBeach", "opt-mountain", "askMountain"),
                        Map.of("askBeach", "Tell me the beach destination.",
                                "askMountain", "Tell me the climbing skill level.")),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: askAdventure → switch(opt-beach|opt-mountain) → askBeach|askMountain");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();

        trace("turn 1: user='I want to climb something high' → LLM classifier → askMountain");
        AdvanceResult result = advance(leaf,
                "I want to climb something high", "Anotado.");
        TurChatFlowState newLeaf = result.state();
        traceState("after t1", newLeaf);

        assertThat(newLeaf.getCurrentNodeId())
                .as("LLM classifier should map 'climb something high' to opt-mountain")
                .isEqualTo("askMountain");
    }

    /**
     * When the configured {@code switchVariable} is not in {@code state.variables},
     * the switch must fall through to the wildcard rather than stall. We test this by
     * placing the switch right after START — at init time no variable has been
     * collected yet, so the switch resolves with no value at all.
     */
    @Test
    void switchMissingVariable_fallsThroughToWildcard() {
        TurChatFlow flow = saveFlow("switch-missing-var",
                graphSwitchAtStartWithWildcard(
                        "never_collected",
                        List.of(option("opt-a", "alpha"), option("opt-b", "beta")),
                        Map.of("opt-a", "askA", "opt-b", "askB"),
                        "askFallback",
                        Map.of("askA", "Alpha branch.",
                                "askB", "Beta branch.",
                                "askFallback", "Fallback branch — variable was missing.")),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: start → switch(opt-a|opt-b|wildcard→askFallback) (no upstream question)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        traceState("after init", leaf);

        // walkTransparentNodes runs at init; the switch sees no value and routes wildcard.
        assertThat(leaf.getCurrentNodeId())
                .as("Missing routing variable should land on the wildcard target")
                .isEqualTo("askFallback");
    }

    /**
     * A switch whose matched branch points at another switch must walk through both in
     * a single transparent pass — {@code walkThroughSwitches} loops up to its safety
     * cap, and the engine's outer {@code walkTransparentNodes} also runs both walkers
     * each iteration so chained transparents collapse correctly.
     */
    @Test
    void switchChained_walksThroughMultipleHops() {
        // start → askChoice(=outer) → switch1(picks 'group-a') → switch2(picks 'first')
        //   → askFirstA (interactive leaf)
        TurChatFlow flow = saveFlow("switch-chained",
                graphChainedSwitches(),
                TurChatFlowGuardrailMethod.LLM_JUDGE);
        trace("Setup: askGroup → switch1 → switch2 → askLeaf (chained transparents)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        assertThat(leaf.getCurrentNodeId()).isEqualTo("askGroup");

        // Collecting both routing variables in a single user reply tests two-hop walk.
        // The aiInstruction asks for BOTH group and slot; the judge captures both.
        trace("turn 1: user='group A first slot' → captures group + slot → switch1 → switch2 → askFirstA");
        AdvanceResult result = advance(leaf, "group A first slot", "Anotado.");
        TurChatFlowState newLeaf = result.state();
        traceState("after t1", newLeaf);

        assertThat(newLeaf.getCurrentNodeId())
                .as("Chained switches should both fire and land on the leaf interactive node")
                .isEqualTo("askFirstA");
    }

    /**
     * Switch with no options at all — defensive path. The classifier returns null and
     * the engine still resolves to the wildcard edge so an authoring mistake doesn't
     * stall the conversation. The state must not blank out the user's collected
     * variable in the process.
     */
    @Test
    void switchWithoutOptions_stillFallsThroughToWildcard() {
        TurChatFlow flow = saveFlow("switch-no-options",
                graphSwitchAtStartWithWildcard(
                        "color",
                        List.of(), // no options
                        Map.of(),
                        "askFallback",
                        Map.of("askFallback", "Fallback — switch had no options.")),
                TurChatFlowGuardrailMethod.HEURISTIC);
        trace("Setup: start → switch(no options, wildcard→askFallback)");

        String conv = newConversationId();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        traceState("after init", leaf);

        assertThat(leaf.getCurrentNodeId())
                .as("Switch with no options should resolve via wildcard")
                .isEqualTo("askFallback");
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

    /**
     * {@code start → askQuestion(outputVariable) → switch(options[, wildcard]) → ...branches → ends}.
     * Each branch is a single aiQuestion node whose id is {@code targetByOption.get(optionId)}; the
     * {@code branchInstructions} map gives every branch its own aiInstruction.
     */
    private static String graphAskThenSwitchTwoBranches(String outputVariable, String questionInstruction,
            List<Map<String, Object>> options,
            Map<String, String> targetByOption,
            Map<String, String> branchInstructions) {
        return graphAskThenSwitchWithWildcard(outputVariable, questionInstruction, options,
                targetByOption, null, branchInstructions);
    }

    private static String graphAskThenSwitchWithWildcard(String outputVariable, String questionInstruction,
            List<Map<String, Object>> options,
            Map<String, String> targetByOption,
            String wildcardTargetId,
            Map<String, String> branchInstructions) {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();

        nodes.add(node("start", "start", nodeData("START", "start", null)));
        nodes.add(node("askColor", "aiQuestion", nodeData("ASK", "aiQuestion",
                Map.of("aiInstruction", questionInstruction, "outputVariable", outputVariable))));
        nodes.add(node("switch", "switch", nodeData("SWITCH", "switch",
                Map.of("switchVariable", outputVariable, "switchOptions", options))));
        edges.add(edge("eStart", "start", "askColor", null));
        edges.add(edge("eAsk", "askColor", "switch", null));

        addSwitchBranches(nodes, edges, targetByOption, wildcardTargetId, branchInstructions);

        return serialize(nodes, edges);
    }

    /** Switch directly after start (no upstream question). */
    private static String graphSwitchAtStartWithWildcard(String switchVariable,
            List<Map<String, Object>> options,
            Map<String, String> targetByOption,
            String wildcardTargetId,
            Map<String, String> branchInstructions) {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();

        nodes.add(node("start", "start", nodeData("START", "start", null)));
        nodes.add(node("switch", "switch", nodeData("SWITCH", "switch",
                Map.of("switchVariable", switchVariable, "switchOptions", options))));
        edges.add(edge("eStart", "start", "switch", null));

        addSwitchBranches(nodes, edges, targetByOption, wildcardTargetId, branchInstructions);

        return serialize(nodes, edges);
    }

    private static void addSwitchBranches(List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges,
            Map<String, String> targetByOption,
            String wildcardTargetId,
            Map<String, String> branchInstructions) {
        int i = 0;
        for (Map.Entry<String, String> entry : targetByOption.entrySet()) {
            String optionId = entry.getKey();
            String targetId = entry.getValue();
            String instr = branchInstructions.getOrDefault(targetId, "Branch " + targetId);
            nodes.add(node(targetId, "aiQuestion",
                    nodeData(targetId.toUpperCase(java.util.Locale.ROOT), "aiQuestion",
                            Map.of("aiInstruction", instr, "outputVariable", "branch_value"))));
            edges.add(edge("eSwitch-" + i++, "switch", targetId, optionId));
            String endId = "end-" + targetId;
            nodes.add(node(endId, "end", nodeData("END", "end", null)));
            edges.add(edge("e-" + targetId + "-end", targetId, endId, null));
        }
        if (wildcardTargetId != null) {
            String instr = branchInstructions.getOrDefault(wildcardTargetId, "Wildcard.");
            nodes.add(node(wildcardTargetId, "aiQuestion",
                    nodeData(wildcardTargetId.toUpperCase(java.util.Locale.ROOT), "aiQuestion",
                            Map.of("aiInstruction", instr, "outputVariable", "branch_value"))));
            edges.add(edge("eSwitch-wildcard", "switch", wildcardTargetId, null));
            String endId = "end-" + wildcardTargetId;
            nodes.add(node(endId, "end", nodeData("END", "end", null)));
            edges.add(edge("e-" + wildcardTargetId + "-end", wildcardTargetId, endId, null));
        }
    }

    /**
     * {@code askGroup → switch1(group) → askSlot → switch2(slot) → askLeaf}.
     * Two routing variables in one user turn; both switches resolve transparently.
     */
    private static String graphChainedSwitches() {
        List<Map<String, Object>> nodes = new java.util.ArrayList<>();
        List<Map<String, Object>> edges = new java.util.ArrayList<>();

        nodes.add(node("start", "start", nodeData("START", "start", null)));
        nodes.add(node("askGroup", "aiQuestion", nodeData("ASK GROUP", "aiQuestion", Map.of(
                "aiInstruction",
                "Ask the user for two values together: which group (A or B) and which slot (first or second). "
                        + "Capture them into the variable named 'group_and_slot' as a single string like 'group A first slot'.",
                "outputVariable", "group_and_slot"))));
        nodes.add(node("switch1", "switch", nodeData("SWITCH 1", "switch", Map.of(
                "switchVariable", "group_and_slot",
                "switchOptions", List.of(option("opt-a", "group A"), option("opt-b", "group B"))))));

        // Branch A: chain into switch2
        nodes.add(node("switch2", "switch", nodeData("SWITCH 2", "switch", Map.of(
                "switchVariable", "group_and_slot",
                "switchOptions", List.of(option("opt-first", "first slot"),
                        option("opt-second", "second slot"))))));
        nodes.add(node("askFirstA", "aiQuestion", nodeData("FIRST IN A", "aiQuestion",
                Map.of("aiInstruction", "Group A, first slot leaf.",
                        "outputVariable", "leaf_value"))));
        nodes.add(node("askSecondA", "aiQuestion", nodeData("SECOND IN A", "aiQuestion",
                Map.of("aiInstruction", "Group A, second slot leaf.",
                        "outputVariable", "leaf_value"))));
        nodes.add(node("askB", "aiQuestion", nodeData("GROUP B", "aiQuestion",
                Map.of("aiInstruction", "Group B leaf.",
                        "outputVariable", "leaf_value"))));
        nodes.add(node("end", "end", nodeData("END", "end", null)));

        edges.add(edge("e0", "start", "askGroup", null));
        edges.add(edge("e1", "askGroup", "switch1", null));
        edges.add(edge("e2a", "switch1", "switch2", "opt-a"));
        edges.add(edge("e2b", "switch1", "askB", "opt-b"));
        edges.add(edge("e3a", "switch2", "askFirstA", "opt-first"));
        edges.add(edge("e3b", "switch2", "askSecondA", "opt-second"));
        edges.add(edge("e4a", "askFirstA", "end", null));
        edges.add(edge("e4b", "askSecondA", "end", null));
        edges.add(edge("e4c", "askB", "end", null));

        return serialize(nodes, edges);
    }

    private static Map<String, Object> option(String id, String label) {
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("id", id);
        opt.put("label", label);
        return opt;
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

    private static Map<String, Object> edge(String id, String source, String target, String sourceHandle) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("sourceHandle", sourceHandle);
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
