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
package com.viglet.turing.genai.nativeapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.llm.TurLLMInstanceCapability;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceCapabilityRepository;

/**
 * T130/T132 / §X.2 — read/write façade over the per-LLM-instance native
 * capability matrix.
 *
 * <p>The per-turn lookup ({@link #enabledFor(String, String)}) reads the matrix
 * directly from the repository. The capability matrix is small (a handful of
 * rows per instance) and the read is a single indexed query. (Block AC / T486
 * removed the former repository-level cache — repositories no longer cache
 * entities; reintroduce a read-model cache here if profiling ever warrants it.)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurNativeCapabilityService {

    private final TurLLMInstanceCapabilityRepository repository;

    public TurNativeCapabilityService(TurLLMInstanceCapabilityRepository repository) {
        this.repository = repository;
    }

    /**
     * An enabled capability for a given instance plus its raw config JSON.
     * {@code capability} is the resolved enum; {@code configJson} is the
     * per-capability configuration (vector-store ids, MCP server, …) — may be
     * {@code null}.
     */
    public record EnabledCapability(TurNativeCapability capability, String configJson) {
    }

    /** True when {@code capability} is enabled on the given instance. */
    public boolean hasCapability(String instanceId, TurNativeCapability capability) {
        return repository.findByInstanceId(instanceId).stream()
                .anyMatch(row -> row.isEnabled()
                        && capability.getKey().equalsIgnoreCase(row.getCapabilityKey()));
    }

    /**
     * All enabled, recognised capabilities for the instance whose
     * {@link TurNativeCapability#getPluginType()} matches {@code pluginType}.
     * Unknown / disabled / other-vendor rows are filtered out.
     */
    public List<EnabledCapability> enabledFor(String instanceId, String pluginType) {
        List<EnabledCapability> result = new ArrayList<>();
        for (TurLLMInstanceCapability row : repository.findByInstanceId(instanceId)) {
            if (!row.isEnabled()) {
                continue;
            }
            Optional<TurNativeCapability> capability = TurNativeCapability.fromKey(row.getCapabilityKey());
            if (capability.isPresent() && capability.get().isForPlugin(pluginType)) {
                result.add(new EnabledCapability(capability.get(), row.getConfigJson()));
            }
        }
        return result;
    }

    /** All persisted rows for the instance (enabled or not) — for the admin matrix UI. */
    public List<TurLLMInstanceCapability> list(String instanceId) {
        return repository.findByInstanceId(instanceId);
    }

    /**
     * Upsert one capability row for an instance. Returns the persisted entity.
     */
    public TurLLMInstanceCapability upsert(String instanceId, TurNativeCapability capability,
            boolean enabled, String configJson) {
        TurLLMInstanceCapability row = repository
                .findByInstanceIdAndCapabilityKey(instanceId, capability.getKey())
                .orElseGet(TurLLMInstanceCapability::new);
        row.setInstanceId(instanceId);
        row.setCapabilityKey(capability.getKey());
        row.setEnabled(enabled);
        row.setConfigJson(configJson);
        return repository.save(row);
    }

    /** Remove one capability row; no-op when absent. */
    public void delete(String instanceId, TurNativeCapability capability) {
        repository.findByInstanceIdAndCapabilityKey(instanceId, capability.getKey())
                .ifPresent(repository::delete);
    }
}
