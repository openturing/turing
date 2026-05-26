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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurChatFlow;

/**
 * Unit tests for the per-turn A/B variant trace formatter
 * ({@link TurChatFlowEngineService#formatVariantTrace}). Pure-function tests
 * — no Spring context, no DB. Pins the wire format described in §VII.8.f
 * (T75): {@code experimentKey:variantLabel:nodeId:userMessage} with a
 * 200-char cap on the message and CR/LF collapsing so one turn fits on one
 * log line.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatFlowVariantTraceTest {

    @Test
    void formatVariantTrace_returnsEmpty_whenFlowIsNull() {
        assertThat(TurChatFlowEngineService.formatVariantTrace(null, "node-1", "oi"))
                .as("Null flow has nothing to trace")
                .isEmpty();
    }

    @Test
    void formatVariantTrace_returnsEmpty_whenExperimentKeyIsBlank() {
        TurChatFlow plain = new TurChatFlow();
        plain.setId("plain-flow");
        // No experimentKey set — flow is not part of an A/B experiment.
        assertThat(TurChatFlowEngineService.formatVariantTrace(plain, "node-1", "oi"))
                .as("Non-experiment flows must not emit trace lines")
                .isEmpty();

        plain.setExperimentKey("   ");
        assertThat(TurChatFlowEngineService.formatVariantTrace(plain, "node-1", "oi"))
                .as("Whitespace-only experimentKey treated as blank")
                .isEmpty();
    }

    @Test
    void formatVariantTrace_emitsCanonicalFourFieldShape() {
        TurChatFlow flow = experiment("programa-match-headline", "lucas-alumni");
        Optional<String> trace = TurChatFlowEngineService.formatVariantTrace(
                flow, "ai-cargo", "Quero saber sobre o MBA");
        assertThat(trace).isPresent();
        // Spec: `experimentKey:variantLabel:nodeId:userMessage`
        assertThat(trace.get())
                .isEqualTo("programa-match-headline:lucas-alumni:ai-cargo:Quero saber sobre o MBA");
    }

    @Test
    void formatVariantTrace_substitutesUnsetLabel_whenVariantLabelMissing() {
        TurChatFlow flow = experiment("exp-x", null);
        String trace = TurChatFlowEngineService.formatVariantTrace(
                flow, "node-1", "msg").orElseThrow();
        assertThat(trace).contains(":(unset):");
    }

    @Test
    void formatVariantTrace_truncatesUserMessage_at200Chars() {
        TurChatFlow flow = experiment("exp-x", "v1");
        // 250-char message: should truncate to 200 + ellipsis.
        String longMsg = "a".repeat(250);
        Optional<String> trace = TurChatFlowEngineService.formatVariantTrace(
                flow, "node-1", longMsg);
        assertThat(trace).isPresent();
        String emitted = trace.get();
        // Strip the prefix to isolate the userMessage segment.
        String userField = emitted.substring("exp-x:v1:node-1:".length());
        assertThat(userField).hasSize(201); // 200 chars + 1 ellipsis char
        assertThat(userField).startsWith("a".repeat(200)).endsWith("…");
    }

    @Test
    void formatVariantTrace_collapsesNewlines_soOneTurnOccupiesOneLine() {
        TurChatFlow flow = experiment("exp-x", "v1");
        Optional<String> trace = TurChatFlowEngineService.formatVariantTrace(
                flow, "node-1", "line1\nline2\r\nline3");
        assertThat(trace).isPresent();
        assertThat(trace.get())
                .doesNotContain("\n")
                .doesNotContain("\r")
                .endsWith("line1 line2  line3");
    }

    @Test
    void formatVariantTrace_handlesNullNodeIdAndNullUserMessage() {
        TurChatFlow flow = experiment("exp-x", "v1");
        String trace = TurChatFlowEngineService.formatVariantTrace(
                flow, null, null).orElseThrow();
        assertThat(trace).isEqualTo("exp-x:v1::");
    }

    private static TurChatFlow experiment(String key, String label) {
        TurChatFlow f = new TurChatFlow();
        f.setId("flow-" + key);
        f.setExperimentKey(key);
        f.setVariantLabel(label);
        return f;
    }
}
