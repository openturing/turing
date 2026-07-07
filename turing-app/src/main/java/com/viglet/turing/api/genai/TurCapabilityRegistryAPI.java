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
package com.viglet.turing.api.genai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService;
import com.viglet.turing.genai.nativeapi.capability.TurCapabilityDescriptor;
import com.viglet.turing.genai.nativeapi.capability.TurCapabilityRegistry;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMInstanceCapability;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;

/**
 * T432 / §X.18 — read-only REST surface over the unified capability registry.
 * Returns every capability (provider-native + Turing native tools) tagged with
 * its {@code kind} / {@code function} / {@code provider} / {@code category} /
 * {@code ownsTurn}, so the agent picker (T434) and the admin heatmap (T186)
 * render from one source. Static metadata only — no secrets, no per-instance
 * state — mirroring the sibling {@link TurNativeToolAPI} catalogue endpoint.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/capability")
@Tag(name = "Capability Registry", description = "Unified native + Turing capability taxonomy")
public class TurCapabilityRegistryAPI {

    private final TurCapabilityRegistry capabilityRegistry;
    private final TurNativeCapabilityService capabilityService;
    private final TurLLMInstanceRepository instanceRepository;
    private final TurGenAiLlmProviderFactory providerFactory;

    public TurCapabilityRegistryAPI(TurCapabilityRegistry capabilityRegistry,
            TurNativeCapabilityService capabilityService,
            TurLLMInstanceRepository instanceRepository,
            TurGenAiLlmProviderFactory providerFactory) {
        this.capabilityRegistry = capabilityRegistry;
        this.capabilityService = capabilityService;
        this.instanceRepository = instanceRepository;
        this.providerFactory = providerFactory;
    }

    @Operation(summary = "List the full capability registry (native + Turing), tagged with the §X.18 taxonomy")
    @GetMapping("/registry")
    public List<TurCapabilityDescriptor> registry() {
        return capabilityRegistry.all();
    }

    /**
     * T186 / §X.14.f — one row per LLM instance for the admin (instance ×
     * capability) heatmap: the instance's vendor plugin type plus the set of
     * native capability keys currently <em>enabled</em> on it. The frontend
     * cross-references this against {@link #registry()} (filtered to provider
     * matching {@code pluginType}) to colour each cell wired / capable-not-wired
     * / not-applicable. Read-only static + per-instance enabled state — no
     * secrets.
     */
    public record InstanceCapabilityRow(String instanceId, String title, String pluginType,
            boolean enabled, List<String> enabledCapabilityKeys) {
    }

    @Operation(summary = "Per-instance enabled native capabilities for the admin heatmap (T186)")
    @GetMapping("/matrix")
    public List<InstanceCapabilityRow> matrix() {
        List<InstanceCapabilityRow> rows = new ArrayList<>();
        for (TurLLMInstance instance : instanceRepository.findAll()) {
            List<String> enabledKeys = capabilityService.list(instance.getId()).stream()
                    .filter(TurLLMInstanceCapability::isEnabled)
                    .map(row -> row.getCapabilityKey().toLowerCase(Locale.ROOT))
                    .toList();
            rows.add(new InstanceCapabilityRow(instance.getId(), instance.getTitle(),
                    resolvePluginType(instance), instance.getEnabled() == 1, enabledKeys));
        }
        return rows;
    }

    private String resolvePluginType(TurLLMInstance instance) {
        try {
            return providerFactory.getProvider(instance).getPluginType().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            log.debug("[Capability] could not resolve provider for instance '{}': {}",
                    instance.getId(), e.getMessage());
            return "unknown";
        }
    }
}
