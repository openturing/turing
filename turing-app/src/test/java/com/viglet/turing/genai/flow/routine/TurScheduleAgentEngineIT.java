/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.model.agent.TurRoutineKind;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurRoutineRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.ObjectMapper;

/**
 * T48 — full end-to-end integration test for the {@code scheduleAgent}
 * chat-flow node. Boots the full Spring context (incl. embedded Artemis +
 * the {@code TurRoutineQueue} JMS listener + the auto-resume slot bus
 * subscriber), saves a real routine + flow, and asserts the engine:
 *
 * <ol>
 *   <li>Parks the flow on the {@code scheduleAgent} node on first walk
 *       (the routine is asynchronous; the chat turn returns immediately).</li>
 *   <li>Dispatches the routine to JMS, which runs the configured NATIVE
 *       tool and writes the result through the slot bus.</li>
 *   <li>Advances the flow past the parked node automatically — driven by
 *       {@code TurChatFlowAutoResumeService}'s slot-event subscription —
 *       so the next time we read the state the cursor has moved on.</li>
 * </ol>
 *
 * <p>Uses the {@code get_current_time} native tool because it's
 * deterministic, has no external dependency, and is always registered.
 *
 * <p>Does NOT depend on {@code OPENAI_API_KEY} — the scheduleAgent path
 * is entirely deterministic at the engine level (no LLM judge needed for
 * a flow whose only interactive node is the parking one).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurScheduleAgentEngineIT extends AbstractTuringSpringIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TurChatFlowEngineService engine;
    @Autowired
    private TurChatFlowStateRepository stateRepository;
    @Autowired
    private TurChatFlowRepository chatFlowRepository;
    @Autowired
    private TurAIAgentRepository agentRepository;
    @Autowired
    private TurRoutineRepository routineRepository;

    private TurAIAgent agent;

    @BeforeEach
    void newAgent(TestInfo info) {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("scheduleAgent-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
        System.out.println();
        System.out.println("=== " + info.getDisplayName() + " ===");
    }

    @Test
    void firesNativeRoutineAndAdvancesPastScheduleAgent() {
        TurRoutine routine = saveRoutine("time_lookup", TurRoutineKind.NATIVE,
                /* nativeToolName */ "get_current_time",
                /* groovyScript   */ null);
        TurChatFlow flow = saveFlow("schedule-it",
                graphStartScheduleAgentEnd(routine.getId(), /* outputVariable */ "now"));

        String conv = "conv-" + UUID.randomUUID();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();
        trace("after init: nodeId=%s vars=%s", leaf.getCurrentNodeId(), leaf.getVariablesJson());

        // The engine should park on the scheduleAgent node and write the
        // pending markers — but the auto-resume listener may already
        // race in by the time we read here, so we accept either parked
        // OR already-advanced as the "fire succeeded" predicate.
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(150))
                .until(() -> {
                    TurChatFlowState fresh = stateRepository.findById(leaf.getId()).orElseThrow();
                    String now = readVar(fresh, "now");
                    return now != null && !now.isBlank();
                });

        TurChatFlowState finalState = stateRepository.findById(leaf.getId()).orElseThrow();
        String now = readVar(finalState, "now");
        trace("final: nodeId=%s now='%s'", finalState.getCurrentNodeId(), now);

        assertThat(now).as("routine should have written the output slot").isNotNull().isNotBlank();
        assertThat(finalState.getCurrentNodeId())
                .as("flow should have advanced past the scheduleAgent node")
                .isEqualTo("endNode");
    }

    @Test
    void firesGroovyRoutineAndAdvancesPastScheduleAgent() {
        TurRoutine routine = saveRoutine("greet_groovy", TurRoutineKind.GROOVY,
                /* nativeToolName */ null,
                /* groovyScript   */ "return \"hello-from-groovy-\" + (args.who ?: 'world')");
        TurChatFlow flow = saveFlow("schedule-groovy-it",
                graphStartScheduleAgentEnd(routine.getId(), "greeting",
                        /* payloadTemplate */ "{\"who\":\"viglet\"}"));

        String conv = "conv-" + UUID.randomUUID();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(150))
                .until(() -> {
                    TurChatFlowState fresh = stateRepository.findById(leaf.getId()).orElseThrow();
                    return readVar(fresh, "greeting") != null;
                });

        TurChatFlowState finalState = stateRepository.findById(leaf.getId()).orElseThrow();
        assertThat(readVar(finalState, "greeting")).isEqualTo("hello-from-groovy-viglet");
        assertThat(finalState.getCurrentNodeId()).isEqualTo("endNode");
    }

    @Test
    void disabledRoutineAdvancesPastScheduleAgentWithoutWriting() {
        TurRoutine routine = saveRoutine("disabled_routine", TurRoutineKind.NATIVE,
                "get_current_time", null);
        routine.setEnabled(false);
        routineRepository.save(routine);

        TurChatFlow flow = saveFlow("schedule-disabled-it",
                graphStartScheduleAgentEnd(routine.getId(), "now"));

        String conv = "conv-" + UUID.randomUUID();
        TurChatFlowState leaf = engine.loadOrInitState(conv, flow,
                engine.parseGraph(flow).orElseThrow()).orElseThrow();

        // FAILED outcome — engine advances synchronously past the node
        // without firing JMS. Allow a brief settling window for the
        // walkTransparentNodes to complete on the calling thread.
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> {
                    TurChatFlowState fresh = stateRepository.findById(leaf.getId()).orElseThrow();
                    return "endNode".equals(fresh.getCurrentNodeId());
                });

        TurChatFlowState finalState = stateRepository.findById(leaf.getId()).orElseThrow();
        assertThat(readVar(finalState, "now"))
                .as("disabled routine must not write the output slot")
                .isNull();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static void trace(String fmt, Object... args) {
        System.out.println("  · " + String.format(fmt, args));
    }

    private TurChatFlow saveFlow(String name, String definitionJson) {
        TurChatFlow f = new TurChatFlow();
        f.setName(name + "-" + UUID.randomUUID().toString().substring(0, 6));
        f.setDefinitionJson(definitionJson);
        f.setEnabled(1);
        f.setGuardrailMethod(TurChatFlowGuardrailMethod.HEURISTIC);
        f.setTurAIAgent(agent);
        return chatFlowRepository.save(f);
    }

    private TurRoutine saveRoutine(String name, TurRoutineKind kind,
            String nativeToolName, String groovyScript) {
        TurRoutine r = new TurRoutine();
        r.setName(name + "-" + UUID.randomUUID().toString().substring(0, 6));
        r.setKind(kind);
        r.setNativeToolName(nativeToolName);
        r.setGroovyScript(groovyScript);
        r.setDefaultTimeoutMs(30_000);
        r.setEnabled(true);
        return routineRepository.save(r);
    }

    private static String readVar(TurChatFlowState state, String key) {
        if (state.getVariablesJson() == null || state.getVariablesJson().isBlank()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> vars = JSON.readValue(state.getVariablesJson(), Map.class);
        return vars.get(key);
    }

    private static String graphStartScheduleAgentEnd(String routineId, String outputVariable) {
        return graphStartScheduleAgentEnd(routineId, outputVariable, null);
    }

    /** {@code start → scheduleAgent(routineId) → endNode}. */
    private static String graphStartScheduleAgentEnd(String routineId,
            String outputVariable, String payloadTemplate) {
        Map<String, Object> startData = nodeData("START", "start", null);
        Map<String, Object> schedData = new LinkedHashMap<>();
        schedData.put("label", "SCHEDULE");
        schedData.put("type", "scheduleAgent");
        schedData.put("routineId", routineId);
        schedData.put("outputVariable", outputVariable);
        if (payloadTemplate != null) {
            schedData.put("aiInstruction", payloadTemplate);
        }
        Map<String, Object> endData = nodeData("END", "end", null);

        List<Map<String, Object>> nodes = List.of(
                node("startNode", "start", startData),
                node("scheduleNode", "scheduleAgent", schedData),
                node("endNode", "end", endData));
        List<Map<String, Object>> edges = List.of(
                edge("eStart", "startNode", "scheduleNode"),
                edge("eEnd", "scheduleNode", "endNode"));
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
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("type", type);
        n.put("data", data);
        return n;
    }

    private static Map<String, Object> edge(String id, String source, String target) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("source", source);
        e.put("target", target);
        return e;
    }

    private static String serialize(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        return JSON.writeValueAsString(graph);
    }
}
