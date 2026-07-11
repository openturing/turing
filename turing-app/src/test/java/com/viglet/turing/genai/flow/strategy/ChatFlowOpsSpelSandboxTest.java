/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T644 / §XXXVII.6 — verifies the chat-flow condition SpEL evaluator runs in a
 * {@code SimpleEvaluationContext} sandbox: legitimate property-access
 * comparisons still evaluate, but type references ({@code T(...)}) and method
 * invocation — the RCE sink reachable via flow-bundle import / LLM authoring —
 * no longer evaluate (they fail closed to {@link Optional#empty()}, which routes
 * to the LLM judge fallback rather than executing).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class ChatFlowOpsSpelSandboxTest {

    @Test
    void propertyAccessComparisonStillEvaluates() {
        assertThat(ChatFlowOps.trySpel("country == 'BR'", Map.of("country", "BR")))
                .contains(Boolean.TRUE);
        assertThat(ChatFlowOps.trySpel("country == 'BR'", Map.of("country", "US")))
                .contains(Boolean.FALSE);
    }

    @Test
    void booleanCombinationsStillEvaluate() {
        assertThat(ChatFlowOps.trySpel("a == '1' and b == '2'", Map.of("a", "1", "b", "2")))
                .contains(Boolean.TRUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Type reference — would be RCE under StandardEvaluationContext.
            "T(java.lang.Runtime).getRuntime() != null",
            "T(java.lang.Boolean).TRUE",
            "T(java.lang.System).getProperty('user.name') == 'x'",
            // Method invocation on a literal / property — blocked.
            "''.getClass().name == 'java.lang.String'",
            "'a'.getClass().forName('java.lang.Runtime') != null"
    })
    void typeReferencesAndMethodInvocationAreBlocked(String maliciousExpression) {
        // Blocked → the parser/evaluator refuses → empty (no execution, LLM fallback).
        assertThat(ChatFlowOps.trySpel(maliciousExpression, Map.of())).isEmpty();
    }
}
