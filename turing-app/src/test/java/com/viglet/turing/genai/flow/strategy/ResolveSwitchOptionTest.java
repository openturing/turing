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
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.ChatFlowNode.NodeData;
import com.viglet.turing.genai.flow.ChatFlowNode.SwitchOption;

/**
 * Pin tests for {@link ChatFlowOps#resolveSwitchOption(org.springframework.ai.chat.model.ChatModel,
 * ChatFlowNode, java.util.Map)} — the public extraction that powers both the existing
 * {@code switch} routing (via {@code resolveSwitchHandle}, now a thin wrapper) and the
 * new T47 {@code subFlowSwitch} dispatch, where the matched option's
 * {@link SwitchOption#subFlowId()} drives sub-flow descent.
 *
 * <p>Exercises the cheap-match tiers (exact label → substring; LLM tier requires an aux
 * model that's out of scope here — covered by the engine IT for {@code switch}).
 * Crucially, asserts that the option's {@code subFlowId} is preserved on the returned
 * record, which is what makes T47's branching work.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class ResolveSwitchOptionTest {

    @Test
    @DisplayName("exact label match returns the option carrying its subFlowId")
    void exactMatch_preservesSubFlowId() {
        ChatFlowNode node = subFlowSwitchNode("audience",
                new SwitchOption("opt-b2b", "B2B", "flow-b2b"),
                new SwitchOption("opt-b2c", "B2C", "flow-b2c"));

        Optional<SwitchOption> matched = ChatFlowOps.resolveSwitchOption(null, node,
                Map.of("audience", "B2C"));

        assertThat(matched).isPresent();
        assertThat(matched.get().id()).isEqualTo("opt-b2c");
        assertThat(matched.get().subFlowId())
                .as("subFlowId must round-trip so subFlowSwitch can descend")
                .isEqualTo("flow-b2c");
    }

    @Test
    @DisplayName("substring match returns the longest-fitting option (Beach Getaway > Beach)")
    void substringMatch_longestWins() {
        ChatFlowNode node = subFlowSwitchNode("interest",
                new SwitchOption("opt-beach", "Beach", "flow-beach"),
                new SwitchOption("opt-beach-getaway", "Beach Getaway", "flow-getaway"));

        Optional<SwitchOption> matched = ChatFlowOps.resolveSwitchOption(null, node,
                Map.of("interest", "Beach Getaway"));

        assertThat(matched).isPresent();
        assertThat(matched.get().id()).isEqualTo("opt-beach-getaway");
        assertThat(matched.get().subFlowId()).isEqualTo("flow-getaway");
    }

    @Test
    @DisplayName("case-insensitive exact match still wins over substring")
    void exactMatch_caseInsensitive() {
        ChatFlowNode node = subFlowSwitchNode("plan",
                new SwitchOption("opt-pro", "PRO", "flow-pro"),
                new SwitchOption("opt-pro-plus", "PRO Plus", "flow-pro-plus"));

        Optional<SwitchOption> matched = ChatFlowOps.resolveSwitchOption(null, node,
                Map.of("plan", "pro"));

        assertThat(matched).isPresent();
        assertThat(matched.get().id()).isEqualTo("opt-pro");
    }

    @Test
    @DisplayName("no switchVariable / blank variable value → empty (engine falls back to advance)")
    void noValue_emptyOptional() {
        ChatFlowNode node = subFlowSwitchNode("audience",
                new SwitchOption("opt-b2b", "B2B", "flow-b2b"));

        assertThat(ChatFlowOps.resolveSwitchOption(null, node, Map.of())).isEmpty();
        assertThat(ChatFlowOps.resolveSwitchOption(null, node, Map.of("audience", ""))).isEmpty();
        assertThat(ChatFlowOps.resolveSwitchOption(null, node, Map.of("audience", "  "))).isEmpty();
    }

    @Test
    @DisplayName("empty options list → empty (no NPE, no exception)")
    void emptyOptions_emptyOptional() {
        ChatFlowNode node = subFlowSwitchNode("audience" /* no options */);

        Optional<SwitchOption> matched = ChatFlowOps.resolveSwitchOption(null, node,
                Map.of("audience", "B2C"));

        assertThat(matched).isEmpty();
    }

    @Test
    @DisplayName("no match + no aux model → empty (no exception)")
    void noMatchNoModel_emptyOptional() {
        ChatFlowNode node = subFlowSwitchNode("audience",
                new SwitchOption("opt-b2b", "B2B", "flow-b2b"),
                new SwitchOption("opt-b2c", "B2C", "flow-b2c"));

        Optional<SwitchOption> matched = ChatFlowOps.resolveSwitchOption(null, node,
                Map.of("audience", "totally unrelated input"));

        assertThat(matched).isEmpty();
    }

    @Test
    @DisplayName("legacy SwitchOption(id, label) two-arg constructor still works — subFlowId is null")
    void backCompatTwoArgConstructor() {
        SwitchOption legacy = new SwitchOption("opt-a", "A");

        assertThat(legacy.id()).isEqualTo("opt-a");
        assertThat(legacy.label()).isEqualTo("A");
        assertThat(legacy.subFlowId()).isNull();
    }

    // ─────────────────────────── helpers ───────────────────────────

    /**
     * Builds a node with {@code type=subFlowSwitch} and the given options. Reuses the
     * existing switch matching (which is type-agnostic) — the dispatch contract in the
     * engine differentiates by node type, but {@code resolveSwitchOption} doesn't care.
     */
    private static ChatFlowNode subFlowSwitchNode(String switchVariable, SwitchOption... options) {
        return new ChatFlowNode("sfs-1", "subFlowSwitch", new NodeData(
                "Sub-flow switch", "subFlowSwitch",
                null,                       // aiInstruction
                null,                       // outputVariable
                null,                       // validationRule
                null,                       // functionName
                null,                       // conditionExpression
                null,                       // toolSource
                null,                       // mcpServerId
                null,                       // subFlowId
                null,                       // subFlowName
                null,                       // personaId
                switchVariable,             // switchVariable
                List.of(options),           // switchOptions
                List.of(),                  // inlineOptions
                null,                       // overrideExistingValue
                null,                       // slotName
                null,                       // slotOperation
                null,                       // slotValue
                null,                       // onJudgeReject
                null,                       // toolsEnabled
                List.of(),                  // requiredTools
                null,                       // routineId
                null));                     // routineTimeoutMs
    }
}
