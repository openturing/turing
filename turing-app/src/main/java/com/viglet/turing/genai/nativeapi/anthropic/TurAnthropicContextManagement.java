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
package com.viglet.turing.genai.nativeapi.anthropic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaClearToolUses20250919Edit;
import com.anthropic.models.beta.messages.BetaContextManagementConfig;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;

/**
 * F.8 / §X.9 — builds Anthropic <b>context management</b> config from a per-agent
 * Request Option, shared by every native Anthropic path so the knobs behave
 * identically whether a turn runs on the standard Messages builder
 * (web_search/web_fetch/code_execution + coexisting Turing tools) or the beta
 * builder (memory, computer_use).
 *
 * <p>Two independent server-side levers, both off by default:
 * <ul>
 *   <li><b>T164 — context editing</b> ({@code context-editing} option →
 *       {@code clear_tool_uses_20250919}): Claude auto-evicts old tool results
 *       when the window saturates, replacing them with a placeholder.</li>
 *   <li><b>T165 — compaction</b> ({@code compaction} option →
 *       {@code compact_20260112}): Claude summarizes older turns server-side.
 *       Wired separately so T115 (Turing-side workspace compression) can take
 *       precedence and leave this off to avoid double-summarization.</li>
 * </ul>
 *
 * <p>Both ride the {@code context-management-2025-06-27} beta. The typed
 * {@link BetaContextManagementConfig} is used directly on the beta Messages
 * builder; the standard (non-beta) builder has no typed setter, so the same
 * edits are emitted as a raw {@code context_management} body property
 * ({@link #rawConfig}) plus the matching beta header token ({@link #rawBetaTokens}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurAnthropicContextManagement {

    /** T164 — per-agent Request Option key (already in the T435 registry). */
    static final String CONTEXT_EDITING_OPTION = "context-editing";
    /** T165 — per-agent Request Option key. */
    static final String COMPACTION_OPTION = "compaction";

    /** Beta flag that gates {@code context_management} (context editing + compaction). */
    static final String CONTEXT_MGMT_BETA_TOKEN = "context-management-2025-06-27";

    private final TurProviderOptionsParser optionsParser;

    public TurAnthropicContextManagement(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /** The two context-management levers an agent can opt into. */
    public record Options(boolean contextEditing, boolean compaction) {

        public static final Options NONE = new Options(false, false);

        /** True when at least one lever is on (so any config/beta must be attached). */
        public boolean any() {
            return contextEditing || compaction;
        }
    }

    /**
     * Resolve the agent's context-management options from its
     * {@code requestOptionsJson} (values stored as strings by the registry UI).
     * {@code null}/blank → {@link Options#NONE} (unchanged path).
     */
    public Options resolve(String requestOptionsJson) {
        if (!StringUtils.hasText(requestOptionsJson)) {
            return Options.NONE;
        }
        Map<String, Object> opts = optionsParser.parse(requestOptionsJson);
        return new Options(truthy(opts.get(CONTEXT_EDITING_OPTION)),
                truthy(opts.get(COMPACTION_OPTION)));
    }

    /** Typed config for the beta Messages builder ({@code .contextManagement(...)}). */
    public Optional<BetaContextManagementConfig> betaConfig(Options options) {
        if (options == null || !options.any()) {
            return Optional.empty();
        }
        BetaContextManagementConfig.Builder builder = BetaContextManagementConfig.builder();
        if (options.contextEditing()) {
            builder.addEdit(BetaClearToolUses20250919Edit.builder().build());
        }
        // T165 wires the compaction edit here.
        return Optional.of(builder.build());
    }

    /** Beta flags to add on the beta Messages builder ({@code .addBeta(...)}). */
    public Set<AnthropicBeta> betas(Options options) {
        if (options == null || !options.any()) {
            return Set.of();
        }
        return Set.of(AnthropicBeta.CONTEXT_MANAGEMENT_2025_06_27);
    }

    /**
     * Raw {@code context_management} body value for the standard (non-beta)
     * Messages builder, injected via {@code putAdditionalBodyProperty}.
     */
    public Optional<JsonValue> rawConfig(Options options) {
        if (options == null || !options.any()) {
            return Optional.empty();
        }
        List<Map<String, Object>> edits = new ArrayList<>();
        if (options.contextEditing()) {
            edits.add(Map.of("type", "clear_tool_uses_20250919"));
        }
        // T165 wires the compact edit here.
        return Optional.of(JsonValue.from(Map.of("edits", edits)));
    }

    /** Beta header tokens to add on the standard (non-beta) Messages path. */
    public Set<String> rawBetaTokens(Options options) {
        if (options == null || !options.any()) {
            return Set.of();
        }
        return new LinkedHashSet<>(List.of(CONTEXT_MGMT_BETA_TOKEN));
    }

    private static boolean truthy(Object value) {
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }
}
