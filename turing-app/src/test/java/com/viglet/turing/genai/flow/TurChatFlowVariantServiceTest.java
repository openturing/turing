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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurChatFlowVariantRequest;
import com.viglet.turing.persistence.dto.agent.TurChatFlowVariantResponse;
import com.viglet.turing.persistence.mapper.agent.TurChatFlowMapper;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.system.TurLlmSummaryService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the T97 variant generator contract: the LLM rewrites only the
 * whitelisted copy fields, every structural field round-trips verbatim,
 * the candidate is left unpersisted ({@code id == null}) and lands disabled,
 * and the dialog gets a non-empty {@code rewrittenNodes} list to highlight
 * the diff.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatFlowVariantServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SOURCE_DEFINITION = "{"
            + "\"nodes\":["
            + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{\"label\":\"START\"}},"
            + "  {\"id\":\"ask-name\",\"type\":\"aiQuestion\",\"data\":{"
            + "      \"label\":\"NAME\",\"outputVariable\":\"name\","
            + "      \"validationRule\":\"none\","
            + "      \"aiInstruction\":\"Please kindly ask the visitor for their full legal name.\"}},"
            + "  {\"id\":\"decide-vip\",\"type\":\"condition\",\"data\":{"
            + "      \"label\":\"VIP?\","
            + "      \"conditionExpression\":\"the user explicitly stated they are a VIP customer\"}},"
            + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{\"label\":\"END\"}}"
            + "],"
            + "\"edges\":["
            + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"ask-name\"},"
            + "  {\"id\":\"e2\",\"source\":\"ask-name\",\"target\":\"decide-vip\"},"
            + "  {\"id\":\"e3\",\"source\":\"decide-vip\",\"target\":\"end\",\"sourceHandle\":\"yes\"},"
            + "  {\"id\":\"e4\",\"source\":\"decide-vip\",\"target\":\"end\",\"sourceHandle\":\"no\"}"
            + "]}";

    @Mock
    private TurLlmSummaryService llmSummaryService;

    private TurChatFlowMapper mapper;
    private TurChatFlowVariantService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(TurChatFlowMapper.class);
        service = new TurChatFlowVariantService(llmSummaryService, mapper);
        lenient().when(llmSummaryService.isAvailable()).thenReturn(true);
    }

    @Test
    void rewritesWhitelistedCopyAndPreservesStructure() throws JacksonException {
        String llmResponse = "{"
                + "\"summary\":\"Tightened the copy and dropped the formal register.\","
                + "\"nodes\":["
                + "  {\"id\":\"ask-name\",\"label\":\"NAME\",\"aiInstruction\":\"Ask their name.\"},"
                + "  {\"id\":\"decide-vip\",\"conditionExpression\":\"is VIP\"}"
                + "]}";
        when(llmSummaryService.generate(startsWith("chat-flow-variant:"), any(),
                any(), eq(true)))
                .thenReturn(new TurLlmSummaryService.SummaryResult(
                        true, null, llmResponse, true));

        TurChatFlow source = source();
        TurChatFlowVariantResponse result = service.generate(source,
                new TurChatFlowVariantRequest("more casual, shorter", null));

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.summary()).contains("Tightened");
        assertThat(result.rewrittenNodes()).containsExactlyInAnyOrder("ask-name", "decide-vip");

        // Candidate is not persisted and lands disabled.
        assertThat(result.candidate()).isNotNull();
        assertThat(result.candidate().getId()).isNull();
        assertThat(result.candidate().getEnabled()).isZero();
        assertThat(result.candidate().getName()).isEqualTo("Lead Capture (variant)");

        // Rewrite landed on the right fields; everything else round-trips.
        JsonNode root = MAPPER.readTree(result.candidate().getDefinitionJson());
        JsonNode askName = nodeById(root, "ask-name").path("data");
        assertThat(askName.path("label").asString()).isEqualTo("NAME");
        assertThat(askName.path("aiInstruction").asString()).isEqualTo("Ask their name.");
        // Structural fields untouched.
        assertThat(askName.path("outputVariable").asString()).isEqualTo("name");
        assertThat(askName.path("validationRule").asString()).isEqualTo("none");

        JsonNode decideVip = nodeById(root, "decide-vip").path("data");
        assertThat(decideVip.path("conditionExpression").asString()).isEqualTo("is VIP");

        // Edges (including condition sourceHandles) round-trip verbatim.
        JsonNode edges = root.path("edges");
        assertThat(edges.size()).isEqualTo(4);
        assertThat(edges.get(2).path("sourceHandle").asString()).isEqualTo("yes");
        assertThat(edges.get(3).path("sourceHandle").asString()).isEqualTo("no");
    }

    @Test
    void honorsExplicitTargetName() {
        when(llmSummaryService.generate(startsWith("chat-flow-variant:"), any(),
                any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(
                        true, null, "{\"summary\":\"ok\",\"nodes\":[" +
                                "{\"id\":\"ask-name\",\"aiInstruction\":\"Ask name\"}]}",
                        true));

        TurChatFlowVariantResponse result = service.generate(source(),
                new TurChatFlowVariantRequest("translate", "Lead Capture (PT)"));

        assertThat(result.success()).isTrue();
        assertThat(result.candidate().getName()).isEqualTo("Lead Capture (PT)");
    }

    @Test
    void stripsLlmCodeFences() {
        String fenced = """
                ```json
                {"summary":"ok","nodes":[  {"id":"ask-name","aiInstruction":"Ask name"}]}
                ```""";
        when(llmSummaryService.generate(startsWith("chat-flow-variant:"), any(),
                any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, fenced, true));

        TurChatFlowVariantResponse result = service.generate(source(),
                new TurChatFlowVariantRequest("anything", null));

        assertThat(result.success()).isTrue();
        assertThat(result.rewrittenNodes()).contains("ask-name");
    }

    @Test
    void failsWhenNoLlmConfigured() {
        when(llmSummaryService.isAvailable()).thenReturn(false);

        TurChatFlowVariantResponse result = service.generate(source(),
                new TurChatFlowVariantRequest("anything", null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("default LLM");
        verify(llmSummaryService, never()).generate(any(), any(), any(), anyBoolean());
    }

    @Test
    void failsOnBlankInstructions() {
        TurChatFlowVariantResponse result = service.generate(source(),
                new TurChatFlowVariantRequest(" ", null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("instructions");
        verify(llmSummaryService, never()).generate(any(), any(), any(), anyBoolean());
    }

    @Test
    void failsWhenLlmResponseIsNotJson() {
        when(llmSummaryService.generate(startsWith("chat-flow-variant:"), any(),
                any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(
                        true, null, "I refuse to comply.", true));

        TurChatFlowVariantResponse result = service.generate(source(),
                new TurChatFlowVariantRequest("rewrite", null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not valid JSON");
    }

    @Test
    void failsWhenLlmReturnsNoChanges() {
        when(llmSummaryService.generate(startsWith("chat-flow-variant:"), any(),
                any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(
                        true, null, "{\"summary\":\"no-op\",\"nodes\":[]}", true));

        TurChatFlowVariantResponse result = service.generate(source(),
                new TurChatFlowVariantRequest("rewrite", null));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("did not change");
    }

    @Test
    void ignoresUnknownNodeIdsFromLlm() throws JacksonException {
        String llmResponse = "{"
                + "\"summary\":\"partial rewrite\","
                + "\"nodes\":["
                + "  {\"id\":\"ask-name\",\"aiInstruction\":\"Ask name.\"},"
                + "  {\"id\":\"hallucinated-node\",\"aiInstruction\":\"injection\"}"
                + "]}";
        when(llmSummaryService.generate(startsWith("chat-flow-variant:"), any(),
                any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(true, null, llmResponse, true));

        TurChatFlowVariantResponse result = service.generate(source(),
                new TurChatFlowVariantRequest("shorter", null));

        assertThat(result.success()).isTrue();
        assertThat(result.rewrittenNodes()).containsExactly("ask-name");

        // The hallucinated node was not injected into the graph.
        JsonNode root = MAPPER.readTree(result.candidate().getDefinitionJson());
        assertThat(root.path("nodes").findValuesAsString("id"))
                .isNotEmpty()
                .doesNotContain("hallucinated-node");
    }

    @Test
    void wipesRecipeLineageOnCandidate() {
        when(llmSummaryService.generate(startsWith("chat-flow-variant:"), any(),
                any(), anyBoolean()))
                .thenReturn(new TurLlmSummaryService.SummaryResult(
                        true, null, "{\"summary\":\"ok\",\"nodes\":[" +
                                "{\"id\":\"ask-name\",\"aiInstruction\":\"Ask name\"}]}",
                        true));

        TurChatFlow source = source();
        source.setInstalledFromRecipe("lead-capture-b2c");
        source.setInstalledRecipeVersion("1.0.0");

        TurChatFlowVariantResponse result = service.generate(source,
                new TurChatFlowVariantRequest("shorter", null));

        assertThat(result.candidate().getInstalledFromRecipe()).isNull();
        assertThat(result.candidate().getInstalledRecipeVersion()).isNull();
    }

    /* ─────────────────────── helpers ─────────────────────── */

    private static TurChatFlow source() {
        TurChatFlow flow = new TurChatFlow();
        flow.setId("source-flow-id");
        flow.setName("Lead Capture");
        flow.setDescription("Captures basic lead info.");
        flow.setEnabled(1);
        flow.setDefinitionJson(SOURCE_DEFINITION);
        return flow;
    }

    private static JsonNode nodeById(JsonNode root, String id) {
        for (JsonNode node : root.path("nodes")) {
            if (id.equals(node.path("id").asString())) {
                return node;
            }
        }
        throw new AssertionError("Node not found: " + id);
    }
}
