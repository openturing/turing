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

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.nativeapi.capability.TurCapabilityDescriptor;
import com.viglet.turing.genai.nativeapi.capability.TurCapabilityRegistry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@RestController
@RequestMapping("/api/capability")
@Tag(name = "Capability Registry", description = "Unified native + Turing capability taxonomy")
public class TurCapabilityRegistryAPI {

    private final TurCapabilityRegistry capabilityRegistry;

    public TurCapabilityRegistryAPI(TurCapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = capabilityRegistry;
    }

    @Operation(summary = "List the full capability registry (native + Turing), tagged with the §X.18 taxonomy")
    @GetMapping("/registry")
    public List<TurCapabilityDescriptor> registry() {
        return capabilityRegistry.all();
    }
}
