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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;

import lombok.extern.slf4j.Slf4j;

/**
 * T433 / §X.18.b — resolves the per-turn native-capability picture for the
 * "capacidade é mutex, resto coexiste" execution model.
 *
 * <p>Given the agent's explicit selection (the {@code nativeCapabilities} CSV)
 * and what the agent's LLM instance is technically capable of (the per-instance
 * matrix, T132), this collaborator produces:
 * <ul>
 *   <li>the provider-native capabilities to wire this turn —
 *       {@code agentSelected ∩ instanceCapable};</li>
 *   <li>the set of abstract {@code function}s those selected natives claim
 *       (so a same-function Turing tool is dropped — a code-interpreter
 *       function can be served by Turing <em>or</em> the provider, never both
 *       in one turn);</li>
 *   <li>the agent's Turing/MCP/custom {@link ToolCallback}s that <em>coexist</em>
 *       — every callback whose function isn't claimed by a selected native pick.
 *       MCP and custom tools are never in a registry group, so they have no
 *       function and always coexist.</li>
 * </ul>
 *
 * <p><b>Legacy passthrough.</b> When the agent has made no explicit selection
 * ({@code nativeCapabilities == null}) the native path keeps its pre-T433
 * winner-takes-all behaviour: every instance-enabled native capability is used
 * and no Turing tools are attached. {@link #isLegacy(String)} reports this so
 * the executor can branch; every existing agent is therefore unchanged until an
 * admin makes a selection on the T434 picker.
 *
 * <p>All methods are pure (no I/O) so the resolution is unit-testable in
 * isolation; the only collaborator is {@link TurCapabilityRegistry}, consulted
 * once to build the tool-name → function map.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurNativeCapabilityResolver {

    private final TurCapabilityRegistry registry;

    public TurNativeCapabilityResolver(TurCapabilityRegistry registry) {
        this.registry = registry;
    }

    /**
     * The resolved native-capability picture for a turn.
     *
     * @param nativeCapabilities the provider-native capabilities to wire
     *        ({@code agentSelected ∩ instanceCapable}).
     * @param claimedFunctions   the abstract functions those natives claim.
     * @param ownsTurnCapability the first selected capability that takes the
     *        whole turn (computer_use), or {@code null} when none — when set the
     *        executor must drive that capability's dedicated loop and ignore the
     *        coexisting tools.
     */
    public record NativeSelection(List<EnabledCapability> nativeCapabilities,
            Set<String> claimedFunctions, TurNativeCapability ownsTurnCapability) {

        public boolean isEmpty() {
            return nativeCapabilities.isEmpty();
        }

        public boolean hasOwnsTurn() {
            return ownsTurnCapability != null;
        }
    }

    /** True when the agent made no explicit selection — keep pre-T433 behaviour. */
    public boolean isLegacy(String agentNativeCapabilitiesCsv) {
        return agentNativeCapabilitiesCsv == null;
    }

    /** Parse the agent CSV into a set of normalized capability keys (never null). */
    public Set<String> parseSelection(String csv) {
        Set<String> result = new HashSet<>();
        if (csv == null) {
            return result;
        }
        for (String token : csv.split(",")) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Resolve {@code agentSelected ∩ instanceCapable} into a {@link NativeSelection}.
     * Caller must have already excluded the legacy case ({@link #isLegacy}).
     */
    public NativeSelection resolve(String agentNativeCapabilitiesCsv,
            List<EnabledCapability> instanceCapable) {
        Set<String> selectedKeys = parseSelection(agentNativeCapabilitiesCsv);
        List<EnabledCapability> wired = new ArrayList<>();
        Set<String> claimed = new HashSet<>();
        TurNativeCapability ownsTurn = null;
        if (instanceCapable != null) {
            for (EnabledCapability candidate : instanceCapable) {
                if (!selectedKeys.contains(candidate.capability().getKey())) {
                    continue;
                }
                wired.add(candidate);
                claimed.add(candidate.capability().getFunction());
                if (ownsTurn == null && candidate.capability().isOwnsTurn()) {
                    ownsTurn = candidate.capability();
                }
            }
        }
        return new NativeSelection(List.copyOf(wired), Set.copyOf(claimed), ownsTurn);
    }

    /**
     * The agent's Turing/MCP/custom {@link ToolCallback}s that coexist with the
     * selected provider-native picks — every callback whose abstract function
     * isn't in {@code claimedFunctions}. A callback whose tool name maps to no
     * registry group (MCP / custom tools) has no function and always coexists.
     */
    public ToolCallback[] coexistingTools(ToolCallback[] agentTools, Set<String> claimedFunctions) {
        if (agentTools == null || agentTools.length == 0) {
            return new ToolCallback[0];
        }
        if (claimedFunctions == null || claimedFunctions.isEmpty()) {
            return agentTools;
        }
        Map<String, String> toolFunction = toolNameToFunction();
        List<ToolCallback> kept = new ArrayList<>(agentTools.length);
        for (ToolCallback callback : agentTools) {
            String name = safeName(callback);
            String function = name == null ? null : toolFunction.get(name);
            if (function != null && claimedFunctions.contains(function)) {
                log.debug("[Native] dropping Turing tool '{}' — function '{}' is claimed by a "
                        + "selected provider-native capability", name, function);
                continue;
            }
            kept.add(callback);
        }
        return kept.toArray(ToolCallback[]::new);
    }

    /** Build the {@code @Tool name → function} map from the registry's Turing groups. */
    private Map<String, String> toolNameToFunction() {
        Map<String, String> map = new HashMap<>();
        for (TurCapabilityDescriptor descriptor : registry.turingDescriptors()) {
            for (String toolName : descriptor.toolNames()) {
                map.put(toolName, descriptor.function());
            }
        }
        return map;
    }

    private static String safeName(ToolCallback callback) {
        try {
            return callback.getToolDefinition().name();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
