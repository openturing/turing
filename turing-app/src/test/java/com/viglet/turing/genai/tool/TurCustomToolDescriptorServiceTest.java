/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.tool.TurCustomToolDescriptorService.HelperDescriptor;
import com.viglet.turing.genai.tool.TurCustomToolDescriptorService.MethodDescriptor;
import com.viglet.turing.genai.tool.TurCustomToolDescriptorService.ToolEditorDescriptor;

/**
 * Pin tests for the editor descriptor exposed via {@code GET /api/custom-tool/descriptor}.
 * The Custom Tool admin page consumes this payload to drive auto-complete; if a binding
 * is dropped or renamed in {@code TurCustomToolCallbackService.executeGroovy} but not
 * mirrored in {@link TurCustomToolDescriptorService}, the editor would silently stop
 * suggesting it — these tests fail loud when that happens.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurCustomToolDescriptorServiceTest {

    private final TurCustomToolDescriptorService service = new TurCustomToolDescriptorService();

    @Test
    void descriptor_listsAllHelpersByBindingName() {
        ToolEditorDescriptor descriptor = service.get();

        assertThat(descriptor.helpers())
                .extracting(HelperDescriptor::name)
                .as("binding names must match TurCustomToolCallbackService.executeGroovy")
                .containsExactlyInAnyOrder("http", "slots", "turingSearch", "code", "agent");
    }

    @Test
    void descriptor_exposesArgsGlobal() {
        ToolEditorDescriptor descriptor = service.get();

        assertThat(descriptor.globals())
                .extracting("name")
                .as("`args` is always bound for the LLM-supplied parameters")
                .contains("args");
    }

    @Test
    void httpHelper_hasGetJsonPostJsonBearerBasic() {
        HelperDescriptor http = helper("http");

        assertThat(http.methods())
                .extracting(MethodDescriptor::name)
                .containsExactlyInAnyOrder("getJson", "postJson", "bearer", "basic");
        assertThat(http.description())
                .as("the helper-level blurb should mention the reserved __-slot prefix so the "
                        + "editor surfaces it as a hint")
                .contains("__bearer_token");
    }

    @Test
    void slotsHelper_hasSetGetAll() {
        HelperDescriptor slots = helper("slots");

        assertThat(slots.methods())
                .extracting(MethodDescriptor::name)
                .containsExactlyInAnyOrder("set", "get", "all");
    }

    @Test
    void turingSearchHelper_hasAnnAndSn() {
        HelperDescriptor turingSearch = helper("turingSearch");

        assertThat(turingSearch.methods())
                .extracting(MethodDescriptor::name)
                .containsExactlyInAnyOrder("ann", "sn");
    }

    @Test
    void agentHelper_hasInvokeOverloads() {
        HelperDescriptor agent = helper("agent");

        // Both overloads share the name `invoke`; signatures must differ
        // (2-arg form vs 3-arg with opts map).
        List<MethodDescriptor> invokes = agent.methods().stream()
                .filter(m -> m.name().equals("invoke"))
                .toList();
        assertThat(invokes).hasSize(2);
        Set<String> signatures = invokes.stream()
                .map(MethodDescriptor::signature)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(signatures).hasSize(2);
        assertThat(agent.description())
                .as("the helper-level blurb should mention the recursion cap so authors "
                        + "discover the depth limit without hunting through Javadoc")
                .containsIgnoringCase("recursion");
    }

    @Test
    void codeHelper_hasExecutePythonOverloads() {
        HelperDescriptor code = helper("code");

        // Both overloads share the name `executePython`; signatures must differ.
        List<MethodDescriptor> executePython = code.methods().stream()
                .filter(m -> m.name().equals("executePython"))
                .toList();
        assertThat(executePython).hasSize(2);
        Set<String> signatures = executePython.stream()
                .map(MethodDescriptor::signature)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(signatures)
                .as("string-arg and named-arg overloads must have distinct signatures")
                .hasSize(2);
    }

    @Test
    void everyMethodHasNonBlankSignatureAndDescription() {
        ToolEditorDescriptor descriptor = service.get();

        assertThat(descriptor.helpers()).isNotEmpty().allSatisfy(helper -> {
            assertThat(helper.name()).isNotBlank();
            assertThat(helper.className()).isNotBlank();
            assertThat(helper.description()).isNotBlank();
            assertThat(helper.methods()).isNotEmpty();
            assertThat(helper.methods()).allSatisfy(method -> {
                assertThat(method.name()).isNotBlank();
                assertThat(method.signature()).isNotBlank();
                assertThat(method.description()).isNotBlank();
            });
        });
    }

    @Test
    void httpBearerSignatureReturnsHelperType_chainableMarker() {
        // The chainable nature of bearer()/basic() is what distinguishes them
        // from getJson/postJson — the editor's hover should make that explicit
        // so authors discover `http.bearer(t).postJson(...)`.
        HelperDescriptor http = helper("http");
        MethodDescriptor bearer = http.methods().stream()
                .filter(m -> m.name().equals("bearer"))
                .findFirst()
                .orElseThrow();

        assertThat(bearer.signature()).contains("TurCustomToolHttpHelper");
    }

    private HelperDescriptor helper(String name) {
        return service.get().helpers().stream()
                .filter(h -> h.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Helper '" + name + "' missing from descriptor"));
    }
}
