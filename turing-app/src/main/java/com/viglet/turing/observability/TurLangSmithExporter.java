/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.viglet.core.webhook.VigletWebhookDispatcher;
import com.viglet.core.webhook.VigletWebhookRequest;
import com.viglet.core.webhook.VigletWebhookRetryPolicy;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurObservabilityProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * T128 / §IX.9.a — mirrors each completed chat turn to a
 * <a href="https://docs.smith.langchain.com/">LangSmith</a> project via their
 * public run-ingest API ({@code POST {apiUrl}/runs/batch}), so customers who
 * already run LangSmith dashboards see Turing turns side-by-side with their
 * LangChain agents — no need to abandon their tooling to adopt Turing
 * (§IX.9 strategic principle: lock in via capability, never via format).
 *
 * <p><b>Opt-in &amp; fail-open.</b> A disabled exporter or a blank API key is a
 * pure no-op — nothing is built, no thread is spent. When enabled the POST is
 * fired through the shared, retrying {@link VigletWebhookDispatcher} on its own
 * async pool, so a slow or unreachable LangSmith never stalls or breaks a chat
 * turn; delivery failures are logged at {@code debug}/{@code warn} only.
 *
 * <p><b>Privacy.</b> The mirror is <b>metadata-only by design</b>: model,
 * provider, token counts, cost, agent and cost-stage — never the user message
 * or the assistant reply. This keeps potentially-PII conversation content out
 * of a third-party trace store. Message-content mirroring, if ever wanted, must
 * be a separate explicit opt-in.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLangSmithExporter {

    /** Best-effort: a single attempt with a short read timeout (analytics, not delivery-critical). */
    private static final VigletWebhookRetryPolicy RETRY_POLICY =
            VigletWebhookRetryPolicy.once(Duration.ofSeconds(10));

    /** LangSmith run type for a single agent turn. */
    private static final String RUN_TYPE = "llm";
    private static final String RUN_NAME = "turing.chat.turn";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurConfigProperties configProperties;
    private final VigletWebhookDispatcher dispatcher;

    public TurLangSmithExporter(TurConfigProperties configProperties,
            VigletWebhookDispatcher dispatcher) {
        this.configProperties = configProperties;
        this.dispatcher = dispatcher;
    }

    /** Whether the LangSmith mirror is configured and ready to send. */
    public boolean isEnabled() {
        TurObservabilityProperty.LangSmith cfg = config();
        return cfg != null && cfg.isEnabled()
                && cfg.getApiKey() != null && !cfg.getApiKey().isBlank();
    }

    /**
     * Mirrors one completed chat turn as a LangSmith run. Returns immediately
     * (no-op) when the exporter is disabled; otherwise builds a metadata-only
     * run and dispatches it asynchronously.
     *
     * @param vendorId     LLM vendor / provider id (e.g. {@code openai}).
     * @param modelName    model name (e.g. {@code gpt-4o}).
     * @param agentId      originating agent id, or {@code null}.
     * @param stage        cost stage (e.g. {@code chat.live}), or {@code null}.
     * @param username     end-user identifier, or {@code null}.
     * @param inputTokens  prompt tokens for the turn.
     * @param outputTokens completion tokens for the turn.
     * @param costUsd      frozen USD cost computed for the turn.
     */
    public void exportTurn(String vendorId, String modelName, String agentId, String stage,
            String username, long inputTokens, long outputTokens, double costUsd) {
        if (!isEnabled()) {
            return;
        }
        try {
            byte[] payload = buildBatchPayload(vendorId, modelName, agentId, stage, username,
                    inputTokens, outputTokens, costUsd);
            TurObservabilityProperty.LangSmith cfg = config();
            VigletWebhookRequest request = VigletWebhookRequest.builder()
                    .method("POST")
                    .url(runsBatchUrl(cfg.getApiUrl()))
                    .contentType("application/json")
                    .header("x-api-key", cfg.getApiKey())
                    .body(payload)
                    .build();
            dispatcher.dispatchAsync(request, RETRY_POLICY)
                    .thenAccept(result -> {
                        if (!result.delivered()) {
                            log.warn("[LangSmith] run mirror not delivered (status={}): {}",
                                    result.statusCode(), result.error());
                        } else if (log.isDebugEnabled()) {
                            log.debug("[LangSmith] mirrored turn agent={} model={} tokens={}",
                                    agentId, modelName, inputTokens + outputTokens);
                        }
                    });
        } catch (Exception e) {
            // Analytics mirror must never disturb the chat turn that produced it.
            log.warn("[LangSmith] failed to mirror chat turn: {}", e.getMessage());
        }
    }

    /**
     * Builds the {@code /runs/batch} request body ({@code {"post":[run]}}) for
     * one turn. Package-private and pure so it can be unit-tested without HTTP.
     */
    byte[] buildBatchPayload(String vendorId, String modelName, String agentId, String stage,
            String username, long inputTokens, long outputTokens, double costUsd) {
        long totalTokens = inputTokens + outputTokens;
        // No per-turn latency is available at this seam; LangSmith requires a
        // start_time, so stamp start == end (the run shows as instantaneous).
        String now = Instant.now().toString();

        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "provider", vendorId);
        putIfPresent(metadata, "model", modelName);
        putIfPresent(metadata, "agent_id", agentId);
        putIfPresent(metadata, "stage", stage);
        putIfPresent(metadata, "username", username);
        metadata.put("input_tokens", inputTokens);
        metadata.put("output_tokens", outputTokens);
        metadata.put("total_tokens", totalTokens);
        metadata.put("cost_usd", costUsd);
        metadata.put("source", "viglet-turing-es");

        Map<String, Object> inputs = new LinkedHashMap<>();
        putIfPresent(inputs, "agent", agentId);
        putIfPresent(inputs, "model", modelName);

        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("total_tokens", totalTokens);
        outputs.put("cost_usd", costUsd);

        Map<String, Object> run = new LinkedHashMap<>();
        String id = UUID.randomUUID().toString();
        run.put("id", id);
        run.put("trace_id", id);
        run.put("name", RUN_NAME);
        run.put("run_type", RUN_TYPE);
        run.put("start_time", now);
        run.put("end_time", now);
        run.put("session_name", project());
        run.put("inputs", inputs);
        run.put("outputs", outputs);
        run.put("extra", Map.of("metadata", metadata));

        return OBJECT_MAPPER.writeValueAsBytes(Map.of("post", List.of(run)));
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /** Normalises {@code apiUrl} (trims a trailing slash) and appends {@code /runs/batch}. */
    static String runsBatchUrl(String apiUrl) {
        String base = (apiUrl == null || apiUrl.isBlank())
                ? "https://api.smith.langchain.com"
                : apiUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/runs/batch";
    }

    private String project() {
        TurObservabilityProperty.LangSmith cfg = config();
        String project = cfg == null ? null : cfg.getProject();
        return project == null || project.isBlank() ? "turing" : project.trim();
    }

    private TurObservabilityProperty.LangSmith config() {
        if (configProperties == null || configProperties.getObservability() == null) {
            return null;
        }
        return configProperties.getObservability().getLangsmith();
    }
}
