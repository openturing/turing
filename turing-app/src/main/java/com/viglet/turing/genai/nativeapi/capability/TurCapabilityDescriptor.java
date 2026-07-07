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
package com.viglet.turing.genai.nativeapi.capability;

import java.util.List;

/**
 * T432 / §X.18 — one row of the unified capability registry. Both the agent's
 * capability-first picker (T434) and the admin instance × capability heatmap
 * (T186) render from a list of these, so there is a single source of truth for
 * what a capability is, who runs it, and where it belongs in the UI.
 *
 * @param key         unique id within the registry. For provider-native
 *                    capabilities this is the {@code TurNativeCapability} key
 *                    (e.g. {@code "openai-web-search"}); for Turing tool groups
 *                    it is the group id (e.g. {@code "finance"}). Vendor-prefixed
 *                    native keys never collide with Turing group ids.
 * @param label       human-readable title (used as a fallback when the UI has no
 *                    localized label for {@link #key()}).
 * @param description one-line summary.
 * @param kind        TOOL / REQUEST_OPTION / PLATFORM — decides the UI surface.
 * @param function    abstract {@code function} key; capabilities sharing a
 *                    function are alternatives of the same thing and become
 *                    mutually exclusive in the picker. See
 *                    {@link TurCapabilityFunctions}.
 * @param provider    who executes it (TURING / OPENAI / ANTHROPIC / ANY).
 * @param category    coarse grouping for visual layout ("Web", "Code", …).
 * @param ownsTurn    when {@code true} the capability takes the whole turn and
 *                    cannot coexist with other tools (computer_use, voice).
 * @param toolNames   for Turing tool groups, the {@code @Tool} names in the
 *                    group (so a group selection expands to the {@code nativeTools}
 *                    entries an agent persists); empty for provider-native rows.
 * @param valueType   T435 / §X.18.d — for {@code REQUEST_OPTION} rows, the control
 *                    the settings UI renders: {@code "BOOLEAN"} (toggle) or
 *                    {@code "SELECT"} (dropdown over {@link #options()}). {@code null}
 *                    for {@code TOOL} / {@code PLATFORM} rows.
 * @param options     T435 / §X.18.d — for a {@code SELECT} request option, the
 *                    allowed values (first is the conventional default); empty
 *                    otherwise.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCapabilityDescriptor(
        String key,
        String label,
        String description,
        TurCapabilityKind kind,
        String function,
        TurCapabilityProvider provider,
        String category,
        boolean ownsTurn,
        List<String> toolNames,
        String valueType,
        List<String> options) {

    public TurCapabilityDescriptor {
        toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** Convenience constructor for TOOL / PLATFORM rows (no request-option control). */
    public TurCapabilityDescriptor(String key, String label, String description,
            TurCapabilityKind kind, String function, TurCapabilityProvider provider,
            String category, boolean ownsTurn, List<String> toolNames) {
        this(key, label, description, kind, function, provider, category, ownsTurn, toolNames,
                null, List.of());
    }
}
