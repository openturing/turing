/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.system.TurLlmSummaryService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T188 / §X.15.b — unit tests for {@link TurMcpBackboneToolService}: the
 * {@code describe_backbone} discovery manifest reflects each live capability
 * (RAG availability, enabled-agent count, write toggle) read from its real
 * source, and conditionally lists the agent / write tool groups.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurMcpBackboneToolServiceTest {

    @Mock
    private TurAIAgentRepository turAIAgentRepository;

    @Mock
    private TurLlmSummaryService llmSummaryService;

    private final JsonMapper json = JsonMapper.builder().build();

    private TurMcpBackboneToolService service(boolean writeEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getMcpServer().setWriteEnabled(writeEnabled);
        return new TurMcpBackboneToolService(turAIAgentRepository, llmSummaryService, props);
    }

    private static TurAIAgent agent(int enabled) {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("Agent");
        a.setEnabled(enabled);
        return a;
    }

    @Test
    void manifestReflectsLiveCapabilitiesWhenEverythingOn() throws Exception {
        when(llmSummaryService.isAvailable()).thenReturn(true);
        when(turAIAgentRepository.findAll()).thenReturn(List.of(agent(1), agent(1), agent(0)));

        JsonNode m = json.readTree(service(true).describeBackbone());

        assertThat(m.get("product").asString()).isEqualTo("Viglet Turing ES");
        assertThat(m.get("role").asString()).contains("Headless enterprise-search backbone");
        JsonNode caps = m.get("capabilities");
        assertThat(caps.get("search").asBoolean()).isTrue();
        assertThat(caps.get("ragAnswer").asBoolean()).isTrue();
        assertThat(caps.get("agents").asLong()).isEqualTo(2L); // only the enabled ones
        assertThat(caps.get("write").asBoolean()).isTrue();

        JsonNode tools = m.get("tools");
        // discovery + search + answer + agents + analytics + write all present
        assertThat(tools.has("discovery")).isTrue();
        assertThat(tools.has("agents")).isTrue();
        assertThat(tools.has("write")).isTrue();
        assertThat(tools.get("discovery").get(0).get("name").asString()).isEqualTo("describe_backbone");
    }

    @Test
    void manifestHidesAgentAndWriteGroupsWhenUnavailable() throws Exception {
        when(llmSummaryService.isAvailable()).thenReturn(false);
        when(turAIAgentRepository.findAll()).thenReturn(List.of(agent(0)));

        JsonNode m = json.readTree(service(false).describeBackbone());

        assertThat(m.get("capabilities").get("ragAnswer").asBoolean()).isFalse();
        assertThat(m.get("capabilities").get("agents").asLong()).isZero();
        assertThat(m.get("capabilities").get("write").asBoolean()).isFalse();

        JsonNode tools = m.get("tools");
        assertThat(tools.has("agents")).isFalse();
        assertThat(tools.has("write")).isFalse();
        // The answer tool is still listed, but its purpose notes the degraded mode.
        assertThat(tools.get("answer").get(0).get("purpose").asString())
                .contains("no default LLM configured");
    }

    @Test
    void manifestSurvivesRepositoryFailure() throws Exception {
        when(llmSummaryService.isAvailable()).thenReturn(true);
        when(turAIAgentRepository.findAll()).thenThrow(new RuntimeException("db down"));

        JsonNode m = json.readTree(service(false).describeBackbone());

        // Agent count degrades to 0 (fail-soft) rather than throwing.
        assertThat(m.get("capabilities").get("agents").asLong()).isZero();
        assertThat(m.get("tools").has("agents")).isFalse();
    }
}
