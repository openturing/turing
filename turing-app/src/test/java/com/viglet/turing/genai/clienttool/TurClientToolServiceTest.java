/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.clienttool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

class TurClientToolServiceTest {

    private final TurClientToolService service = new TurClientToolService();

    private static TurAIAgent agent(boolean enabled, String json) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("a1");
        agent.setClientToolsEnabled(enabled);
        agent.setClientToolsJson(json);
        return agent;
    }

    @Test
    void disabledAgentHasNoClientTools() {
        TurAIAgent a = agent(false, "[{\"name\":\"x\"}]");
        assertThat(service.isEnabled(a)).isFalse();
        assertThat(service.declarations(a)).isEmpty();
        assertThat(service.namesFor(a)).isEmpty();
        assertThat(service.buildToolCallbacks(a)).isEmpty();
    }

    @Test
    void parsesDeclarationsWithNestedSchema() {
        String json = """
                [
                  {"name":"get_user_location","description":"Where is the user",
                   "schema":{"type":"object","properties":{"hint":{"type":"string"}}}},
                  {"name":"open_modal","description":"Open a modal"}
                ]""";
        TurAIAgent a = agent(true, json);

        List<TurClientTool> tools = service.declarations(a);
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).name()).isEqualTo("get_user_location");
        assertThat(tools.get(0).effectiveSchema()).contains("\"hint\"");
        // missing schema → empty-object default
        assertThat(tools.get(1).effectiveSchema()).isEqualTo(TurClientTool.EMPTY_OBJECT_SCHEMA);

        assertThat(service.isEnabled(a)).isTrue();
        assertThat(service.namesFor(a)).containsExactly("get_user_location", "open_modal");
        assertThat(service.buildToolCallbacks(a)).hasSize(2);
        assertThat(service.buildToolCallbacks(a)[0].getToolDefinition().name())
                .isEqualTo("get_user_location");
    }

    @Test
    void skipsNamelessAndDuplicateEntries() {
        String json = """
                [{"name":"a"},{"description":"no name"},{"name":"a"},{"name":"b"}]""";
        TurAIAgent a = agent(true, json);
        assertThat(service.namesFor(a)).containsExactly("a", "b");
    }

    @Test
    void malformedJsonYieldsNoTools() {
        TurAIAgent a = agent(true, "{ not an array");
        assertThat(service.declarations(a)).isEmpty();
        assertThat(service.isEnabled(a)).isFalse();
    }

    @Test
    void nonArrayJsonYieldsNoTools() {
        TurAIAgent a = agent(true, "{\"name\":\"x\"}");
        assertThat(service.declarations(a)).isEmpty();
    }
}
