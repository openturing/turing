/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.core.webhook.VigletWebhookDeliveryResult;
import com.viglet.core.webhook.VigletWebhookDispatcher;
import com.viglet.core.webhook.VigletWebhookRequest;
import com.viglet.core.webhook.VigletWebhookRetryPolicy;
import com.viglet.turing.properties.TurConfigProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T128 / §IX.9.a — tests for the opt-in, fail-open LangSmith run mirror.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLangSmithExporterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TurConfigProperties enabledConfig(String apiUrl) {
        TurConfigProperties props = new TurConfigProperties();
        props.getObservability().getLangsmith().setEnabled(true);
        props.getObservability().getLangsmith().setApiKey("ls-secret");
        props.getObservability().getLangsmith().setProject("my-project");
        if (apiUrl != null) {
            props.getObservability().getLangsmith().setApiUrl(apiUrl);
        }
        return props;
    }

    @Test
    void disabledByDefault_isNoOp() {
        VigletWebhookDispatcher dispatcher = org.mockito.Mockito.mock(VigletWebhookDispatcher.class);
        TurLangSmithExporter exporter = new TurLangSmithExporter(new TurConfigProperties(), dispatcher);

        assertThat(exporter.isEnabled()).isFalse();
        exporter.exportTurn("openai", "gpt-4o", "agent-1", "chat.live", "alex", 100, 50, 0.01);

        verify(dispatcher, never()).dispatchAsync(any(), any());
    }

    @Test
    void enabledWithoutApiKey_isNoOp() {
        VigletWebhookDispatcher dispatcher = org.mockito.Mockito.mock(VigletWebhookDispatcher.class);
        TurConfigProperties props = new TurConfigProperties();
        props.getObservability().getLangsmith().setEnabled(true); // no apiKey
        TurLangSmithExporter exporter = new TurLangSmithExporter(props, dispatcher);

        assertThat(exporter.isEnabled()).isFalse();
        exporter.exportTurn("openai", "gpt-4o", "agent-1", "chat.live", "alex", 100, 50, 0.01);

        verify(dispatcher, never()).dispatchAsync(any(), any());
    }

    @Test
    void enabled_dispatchesRunsBatchWithApiKeyHeader() {
        VigletWebhookDispatcher dispatcher = org.mockito.Mockito.mock(VigletWebhookDispatcher.class);
        when(dispatcher.dispatchAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(VigletWebhookDeliveryResult.ok(202, 1)));
        TurLangSmithExporter exporter = new TurLangSmithExporter(enabledConfig(null), dispatcher);

        assertThat(exporter.isEnabled()).isTrue();
        exporter.exportTurn("openai", "gpt-4o", "agent-1", "chat.live", "alex", 100, 50, 0.0123);

        ArgumentCaptor<VigletWebhookRequest> captor = ArgumentCaptor.forClass(VigletWebhookRequest.class);
        verify(dispatcher).dispatchAsync(captor.capture(), any(VigletWebhookRetryPolicy.class));
        VigletWebhookRequest request = captor.getValue();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url()).isEqualTo("https://api.smith.langchain.com/runs/batch");
        assertThat(request.headers()).containsEntry("x-api-key", "ls-secret");
    }

    @Test
    void buildBatchPayload_isMetadataOnly_withNoMessageContent() {
        TurLangSmithExporter exporter = new TurLangSmithExporter(enabledConfig(null),
                org.mockito.Mockito.mock(VigletWebhookDispatcher.class));

        byte[] payload = exporter.buildBatchPayload("openai", "gpt-4o", "agent-1", "chat.live",
                "alex", 100, 50, 0.0123);
        JsonNode root = MAPPER.readTree(payload);

        JsonNode run = root.get("post").get(0);
        assertThat(run.get("name").asString()).isEqualTo("turing.chat.turn");
        assertThat(run.get("run_type").asString()).isEqualTo("llm");
        assertThat(run.get("session_name").asString()).isEqualTo("my-project");
        assertThat(run.get("start_time").asString()).isNotBlank();
        assertThat(run.get("end_time").asString()).isNotBlank();
        // trace_id mirrors the root run id.
        assertThat(run.get("trace_id").asString()).isEqualTo(run.get("id").asString());

        JsonNode metadata = run.get("extra").get("metadata");
        assertThat(metadata.get("provider").asString()).isEqualTo("openai");
        assertThat(metadata.get("model").asString()).isEqualTo("gpt-4o");
        assertThat(metadata.get("agent_id").asString()).isEqualTo("agent-1");
        assertThat(metadata.get("stage").asString()).isEqualTo("chat.live");
        assertThat(metadata.get("input_tokens").asLong()).isEqualTo(100L);
        assertThat(metadata.get("output_tokens").asLong()).isEqualTo(50L);
        assertThat(metadata.get("total_tokens").asLong()).isEqualTo(150L);
        assertThat(metadata.get("cost_usd").asDouble()).isEqualTo(0.0123);
        assertThat(metadata.get("source").asString()).isEqualTo("viglet-turing-es");

        // Privacy: no message / transcript / prompt / completion fields anywhere.
        String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("message").doesNotContain("transcript")
                .doesNotContain("prompt").doesNotContain("completion");
    }

    @Test
    void buildBatchPayload_omitsBlankOptionalFields() {
        TurLangSmithExporter exporter = new TurLangSmithExporter(enabledConfig(null),
                org.mockito.Mockito.mock(VigletWebhookDispatcher.class));

        byte[] payload = exporter.buildBatchPayload("openai", "gpt-4o", null, null, null, 10, 5, 0.0);
        JsonNode metadata = MAPPER.readTree(payload).get("post").get(0).get("extra").get("metadata");

        assertThat(metadata.has("agent_id")).isFalse();
        assertThat(metadata.has("stage")).isFalse();
        assertThat(metadata.has("username")).isFalse();
    }

    @Test
    void runsBatchUrl_normalisesTrailingSlashAndBlank() {
        assertThat(TurLangSmithExporter.runsBatchUrl("https://eu.smith.langchain.com/"))
                .isEqualTo("https://eu.smith.langchain.com/runs/batch");
        assertThat(TurLangSmithExporter.runsBatchUrl("  "))
                .isEqualTo("https://api.smith.langchain.com/runs/batch");
    }
}
