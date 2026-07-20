/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.eval.TurEvalDatasetImportService;
import com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurGatewayProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T746 / §XLIX — "the gateway that learns": captures inbound gateway traffic as
 * rows of a reusable {@code TurEvalDataset}, so real production prompts/answers
 * can be replayed through the Block AJ eval graders and feed the distillation
 * bridge. It reuses {@link TurEvalDatasetImportService} — the captured rows are
 * byte-identical in shape to uploaded, mined-failure and research rows, so they
 * run through the same eval + export machinery.
 *
 * <p>Each turn becomes one canonical row: the user turns are the replay
 * {@code turns}, the assistant answer is kept as {@code referenceAnswer}
 * <b>material</b> (not an assertion — {@code expectedOutcome=ANY}, exactly like a
 * mined transcript), tagged {@code gateway-traffic} with model/instance/key
 * provenance in {@code metadata}. Opt-in ({@code turing.gateway.capture-traffic},
 * default off) and fully fail-open — capture never breaks a chat turn.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGatewayTrafficCaptureService {

    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_SYSTEM = "system";

    private final TurGatewayProperty gatewayProperty;
    private final TurEvalDatasetImportService importService;
    private final ObjectMapper objectMapper;

    /** Id of the growing capture dataset, created lazily on first capture. */
    private final AtomicReference<String> datasetId = new AtomicReference<>();

    public TurGatewayTrafficCaptureService(TurGatewayProperty gatewayProperty,
            TurEvalDatasetImportService importService,
            ObjectMapper objectMapper) {
        this.gatewayProperty = gatewayProperty;
        this.importService = importService;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return gatewayProperty.isCaptureTraffic();
    }

    /** The capture dataset id, or {@code null} if nothing has been captured yet. */
    public String currentDatasetId() {
        return datasetId.get();
    }

    /**
     * Captures one completed gateway turn as an eval-dataset row. No-op when
     * capture is disabled or the turn has no user content / answer. Fail-open.
     */
    public void capture(String model, List<WireMessage> messages, String answer,
            TurLLMInstance instance, String keyId) {
        if (!isEnabled() || answer == null || answer.isBlank()) {
            return;
        }
        try {
            ObjectNode row = buildRow(model, messages, answer, instance, keyId);
            if (row == null) {
                return;
            }
            appendOrCreate(row);
        } catch (RuntimeException e) {
            log.warn("[Gateway] traffic capture failed (ignored): {}", e.getMessage());
        }
    }

    private synchronized void appendOrCreate(ObjectNode row) {
        String existing = datasetId.get();
        if (existing != null) {
            importService.appendCanonicalRows(existing, List.of(row));
            return;
        }
        TurEvalDataset created = importService.importCanonicalRows(
                gatewayProperty.getCaptureDatasetName(), List.of(row));
        if (created != null && created.getId() != null) {
            datasetId.set(created.getId());
        }
    }

    private ObjectNode buildRow(String model, List<WireMessage> messages, String answer,
            TurLLMInstance instance, String keyId) {
        ArrayNode turns = objectMapper.createArrayNode();
        if (messages != null) {
            for (WireMessage message : messages) {
                if (message.content() != null && !message.content().isBlank()
                        && !ROLE_ASSISTANT.equals(message.role())
                        && !ROLE_SYSTEM.equals(message.role())) {
                    turns.add(message.content().trim());
                }
            }
        }
        if (turns.isEmpty()) {
            return null;
        }

        ObjectNode row = objectMapper.createObjectNode();
        row.put("name", instance != null ? instance.getId() : model);
        row.set("turns", turns);
        // Candidate golden material, not an assertion — a human curates before it grades.
        row.put("referenceAnswer", answer.trim());
        row.put("expectedOutcome", "ANY");

        ArrayNode tags = objectMapper.createArrayNode();
        tags.add("gateway-traffic");
        row.set("tags", tags);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", "gateway");
        metadata.put("model", model);
        if (instance != null) {
            metadata.put("instanceId", instance.getId());
        }
        if (keyId != null) {
            metadata.put("keyId", keyId);
        }
        row.set("metadata", metadata);
        return row;
    }
}
