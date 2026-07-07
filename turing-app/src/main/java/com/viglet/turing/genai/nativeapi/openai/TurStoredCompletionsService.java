/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * F.9 / §X.10.a — T167. Resolves the per-agent {@code stored-completions}
 * Request Option (T435 registry UI) into a directive the OpenAI Responses path
 * applies: {@code store: true} plus a structured {@code metadata} map
 * ({@code agentId}, {@code conversationId}, {@code flowId}, {@code nodeId},
 * {@code experimentKey}, {@code variantLabel}).
 *
 * <p>When the agent opts in, every Responses turn is persisted on OpenAI's side,
 * tagged with the metadata so the T168 Evals run and the T169 distillation
 * export can later slice the stored traffic by agent / conversation /
 * experiment. Strictly opt-in: {@code null}/absent/false → {@link StoredCompletionsDirective#DISABLED},
 * the Responses builder leaves {@code store}/{@code metadata} unset and the turn
 * is byte-for-byte unchanged.
 *
 * <p>OpenAI metadata limits (≤ 16 keys, key ≤ 64 chars, value ≤ 512 chars) are
 * well within reach here — only six short keys are ever written, and blank
 * values are omitted rather than sent empty.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurStoredCompletionsService {

    /** Per-agent Request Option key (matches the capability registry, T435). */
    static final String AGENT_OPTION_KEY = "stored-completions";

    static final String META_AGENT_ID = "agentId";
    static final String META_CONVERSATION_ID = "conversationId";
    static final String META_FLOW_ID = "flowId";
    static final String META_NODE_ID = "nodeId";
    static final String META_EXPERIMENT_KEY = "experimentKey";
    static final String META_VARIANT_LABEL = "variantLabel";

    /** OpenAI hard limit on a metadata value's length. */
    static final int MAX_VALUE_LENGTH = 512;

    private final TurProviderOptionsParser optionsParser;

    public TurStoredCompletionsService(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /**
     * The directive the Responses builder consumes: whether to persist the
     * completion and, if so, the structured metadata to tag it with. Blank
     * metadata values are never present in the map.
     */
    public record StoredCompletionsDirective(boolean store, Map<String, String> metadata) {
        public static final StoredCompletionsDirective DISABLED =
                new StoredCompletionsDirective(false, Map.of());
    }

    /**
     * Resolve the directive for a live chat turn. The native OpenAI path has no
     * chat-flow context (the REST controller routes only plain turns natively),
     * so {@code flowId}/{@code nodeId}/{@code experimentKey}/{@code variantLabel}
     * are typically absent — use {@link #resolve} to supply them when a future
     * caller has them.
     */
    public StoredCompletionsDirective resolveForChat(TurAIAgent agent, String conversationId) {
        return resolve(agent, conversationId, null, null, null, null);
    }

    /** Canonical resolution carrying all six metadata dimensions (any may be null). */
    public StoredCompletionsDirective resolve(TurAIAgent agent, String conversationId,
            String flowId, String nodeId, String experimentKey, String variantLabel) {
        if (!isEnabled(agent)) {
            return StoredCompletionsDirective.DISABLED;
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, META_AGENT_ID, agent == null ? null : agent.getId());
        putIfPresent(metadata, META_CONVERSATION_ID, conversationId);
        putIfPresent(metadata, META_FLOW_ID, flowId);
        putIfPresent(metadata, META_NODE_ID, nodeId);
        putIfPresent(metadata, META_EXPERIMENT_KEY, experimentKey);
        putIfPresent(metadata, META_VARIANT_LABEL, variantLabel);
        return new StoredCompletionsDirective(true, metadata);
    }

    /**
     * Read the {@code stored-completions} boolean from the agent's
     * {@code requestOptionsJson}. Values are stored as strings by the
     * registry-driven UI, so both {@code "true"} and a JSON boolean {@code true}
     * are accepted; any other value (or no options) → disabled.
     */
    public boolean isEnabled(TurAIAgent agent) {
        if (agent == null) {
            return false;
        }
        String json = agent.getRequestOptionsJson();
        if (!StringUtils.hasText(json)) {
            return false;
        }
        Object value = optionsParser.parse(json).get(AGENT_OPTION_KEY);
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    private static void putIfPresent(Map<String, String> metadata, String key, String value) {
        if (StringUtils.hasText(value)) {
            metadata.put(key, value.length() > MAX_VALUE_LENGTH
                    ? value.substring(0, MAX_VALUE_LENGTH) : value);
        }
    }
}
