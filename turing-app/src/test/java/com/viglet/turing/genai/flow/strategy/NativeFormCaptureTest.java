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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowEdge;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.ChatFlowNode.FormField;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;

/**
 * Pure-function tests for the T107 native multi-field {@code formCapture}
 * helpers in {@link ChatFlowOps}: {@code isNativeForm},
 * {@code isNativeFormSatisfied}, and the satisfied-question walker's new
 * form-aware skip branch. No Spring context — only the static graph-navigation
 * logic the engine runs between turns.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class NativeFormCaptureTest {

    private static final String FORM_ID = "lead-form";
    private static final String NEXT_ID = "thanks";

    // ─────────────────────────── isNativeForm ───────────────────────────

    @Test
    @DisplayName("isNativeForm: formCapture with fields → true")
    void isNativeForm_formCaptureWithFields() {
        assertThat(ChatFlowOps.isNativeForm(formNode(
                List.of(field("name", true), field("email", true)))))
                .isTrue();
    }

    @Test
    @DisplayName("isNativeForm: formCapture WITHOUT fields → false (legacy single-field)")
    void isNativeForm_formCaptureWithoutFields() {
        assertThat(ChatFlowOps.isNativeForm(formNode(List.of()))).isFalse();
    }

    @Test
    @DisplayName("isNativeForm: aiQuestion and null → false")
    void isNativeForm_rejectsOthers() {
        assertThat(ChatFlowOps.isNativeForm(nodeOfType("aiQuestion"))).isFalse();
        assertThat(ChatFlowOps.isNativeForm(null)).isFalse();
    }

    // ─────────────────────── isNativeFormSatisfied ───────────────────────

    @Test
    @DisplayName("satisfied when every required field's slot is filled")
    void satisfied_allRequiredFilled() {
        ChatFlowNode form = formNode(List.of(field("name", true), field("email", true)));
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", "Marina");
        vars.put("email", "marina@example.com");
        assertThat(ChatFlowOps.isNativeFormSatisfied(form, vars)).isTrue();
    }

    @Test
    @DisplayName("NOT satisfied when a required field's slot is blank/missing")
    void notSatisfied_requiredMissing() {
        ChatFlowNode form = formNode(List.of(field("name", true), field("email", true)));
        Map<String, String> vars = Map.of("name", "Marina");
        assertThat(ChatFlowOps.isNativeFormSatisfied(form, vars)).isFalse();
        // Present but blank also doesn't satisfy.
        Map<String, String> blank = new LinkedHashMap<>();
        blank.put("name", "Marina");
        blank.put("email", "   ");
        assertThat(ChatFlowOps.isNativeFormSatisfied(form, blank)).isFalse();
    }

    @Test
    @DisplayName("optional (required=false) fields never block satisfaction")
    void satisfied_optionalFieldIgnored() {
        ChatFlowNode form = formNode(List.of(field("name", true), field("phone", false)));
        assertThat(ChatFlowOps.isNativeFormSatisfied(form, Map.of("name", "Marina"))).isTrue();
    }

    @Test
    @DisplayName("required defaults to true when the flag is null")
    void requiredDefaultsTrue() {
        ChatFlowNode form = formNode(List.of(field("name", null)));
        assertThat(ChatFlowOps.isNativeFormSatisfied(form, Map.of())).isFalse();
        assertThat(ChatFlowOps.isNativeFormSatisfied(form, Map.of("name", "x"))).isTrue();
    }

    // ──────────────── walkThroughSatisfiedQuestions (form branch) ────────────────

    @Test
    @DisplayName("satisfied native form is skipped to the next node")
    void walk_skipsSatisfiedForm() {
        ChatFlowNode form = formNode(List.of(field("name", true), field("email", true)));
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(form, endNode(NEXT_ID)),
                List.of(edge("e1", FORM_ID, NEXT_ID)));
        TurChatFlowState state = stateAt(FORM_ID);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", "Marina");
        vars.put("email", "marina@example.com");
        ChatFlowOps.writeVariables(state, vars);

        ChatFlowOps.walkThroughSatisfiedQuestions(state, graph);

        assertThat(state.getCurrentNodeId()).isEqualTo(NEXT_ID);
    }

    @Test
    @DisplayName("unsatisfied native form parks the cursor (engine asks the form)")
    void walk_parksOnUnsatisfiedForm() {
        ChatFlowNode form = formNode(List.of(field("name", true), field("email", true)));
        ChatFlowGraph graph = new ChatFlowGraph(
                List.of(form, endNode(NEXT_ID)),
                List.of(edge("e1", FORM_ID, NEXT_ID)));
        TurChatFlowState state = stateAt(FORM_ID);
        ChatFlowOps.writeVariables(state, Map.of("name", "Marina")); // email still missing

        ChatFlowOps.walkThroughSatisfiedQuestions(state, graph);

        assertThat(state.getCurrentNodeId()).isEqualTo(FORM_ID);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static FormField field(String name, Boolean required) {
        return new FormField(name, name, "text", required, null, null, List.of());
    }

    private static ChatFlowNode formNode(List<FormField> fields) {
        return new ChatFlowNode(FORM_ID, "formCapture", new NodeData(
                "Form", "formCapture", "Fill in your details", null, null, null, null, null, null,
                null, null, null, null, List.of(), List.of(), null, null, null, null, null, null,
                List.of(), null, null, null, null, null, fields));
    }

    private static ChatFlowNode nodeOfType(String type) {
        return new ChatFlowNode("n", type, new NodeData(
                "label", type, null, null, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), null, null, null, null, null, null, List.of(),
                null, null));
    }

    private static ChatFlowNode endNode(String id) {
        return new ChatFlowNode(id, "end", new NodeData(
                "End", "end", null, null, null, null, null, null, null, null, null,
                null, null, List.of(), List.of(), null, null, null, null, null, null, List.of(),
                null, null));
    }

    private static ChatFlowEdge edge(String id, String source, String target) {
        return new ChatFlowEdge(id, source, target, null, null, null);
    }

    private static TurChatFlowState stateAt(String nodeId) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-form");
        state.setCurrentNodeId(nodeId);
        return state;
    }
}
