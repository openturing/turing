/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.openai.TurStoredCompletionsService.StoredCompletionsDirective;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/** F.9 / §X.10.a — T167 stored-completions directive resolution. */
class TurStoredCompletionsServiceTest {

    private final TurStoredCompletionsService service =
            new TurStoredCompletionsService(new TurProviderOptionsParser());

    private static TurAIAgent agent(String id, String requestOptionsJson) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId(id);
        agent.setRequestOptionsJson(requestOptionsJson);
        return agent;
    }

    @Test
    void disabledWhenNoRequestOptions() {
        assertThat(service.isEnabled(agent("a1", null))).isFalse();
        assertThat(service.isEnabled(agent("a1", ""))).isFalse();
        assertThat(service.isEnabled(null)).isFalse();
        assertThat(service.resolveForChat(agent("a1", null), "conv-1"))
                .isEqualTo(StoredCompletionsDirective.DISABLED);
    }

    @Test
    void disabledWhenOptionFalseOrAbsent() {
        assertThat(service.isEnabled(agent("a1", "{\"stored-completions\":\"false\"}"))).isFalse();
        assertThat(service.isEnabled(agent("a1", "{\"citations\":\"true\"}"))).isFalse();
    }

    @Test
    void enabledAcceptsStringAndBooleanTrue() {
        assertThat(service.isEnabled(agent("a1", "{\"stored-completions\":\"true\"}"))).isTrue();
        assertThat(service.isEnabled(agent("a1", "{\"stored-completions\":true}"))).isTrue();
        assertThat(service.isEnabled(agent("a1", "{\"stored-completions\":\" TRUE \"}"))).isTrue();
    }

    @Test
    void resolveForChatTagsAgentAndConversationOnly() {
        StoredCompletionsDirective directive = service.resolveForChat(
                agent("agent-7", "{\"stored-completions\":\"true\"}"), "conv-9");

        assertThat(directive.store()).isTrue();
        assertThat(directive.metadata())
                .containsEntry("agentId", "agent-7")
                .containsEntry("conversationId", "conv-9")
                // no chat-flow / experiment context on the native chat path
                .doesNotContainKeys("flowId", "nodeId", "experimentKey", "variantLabel");
    }

    @Test
    void resolveCarriesAllSixDimensionsAndOmitsBlanks() {
        StoredCompletionsDirective directive = service.resolve(
                agent("agent-7", "{\"stored-completions\":\"true\"}"),
                "conv-9", "flow-3", null, "exp-A", "  ");

        assertThat(directive.metadata())
                .containsEntry("agentId", "agent-7")
                .containsEntry("conversationId", "conv-9")
                .containsEntry("flowId", "flow-3")
                .containsEntry("experimentKey", "exp-A")
                // null nodeId and blank variantLabel are omitted, not sent empty
                .doesNotContainKeys("nodeId", "variantLabel");
    }

    @Test
    void metadataValueTruncatedToOpenAiLimit() {
        String longConversationId = "c".repeat(600);
        StoredCompletionsDirective directive = service.resolveForChat(
                agent("a1", "{\"stored-completions\":\"true\"}"), longConversationId);

        assertThat(directive.metadata().get("conversationId"))
                .hasSize(TurStoredCompletionsService.MAX_VALUE_LENGTH);
    }
}
