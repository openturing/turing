/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm;

import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.service.llm.lifecycle.TurLLMCatalogChangeService;
import com.viglet.turing.service.llm.lifecycle.TurLLMCatalogChangeService.ChangeNotification;
import com.viglet.turing.service.llm.lifecycle.TurLLMModelLifecycleService;
import com.viglet.turing.service.llm.lifecycle.TurLLMModelLifecycleService.DeprecatedModelUsage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T782 / §LIII.2 (Block BE) — surfaces LLM instances whose configured model the
 * public catalog marks deprecated/retired, with a suggested GA replacement, so
 * the admin UI can show a lifecycle banner. Computed on demand (the catalog is a
 * live signal), read-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/llm/model-lifecycle")
@Tag(name = "LLM Model Lifecycle", description = "Block BE — deprecated-model detection")
public class TurLLMModelLifecycleAPI {

    private final TurLLMModelLifecycleService lifecycleService;
    private final TurLLMCatalogChangeService changeService;

    public TurLLMModelLifecycleAPI(TurLLMModelLifecycleService lifecycleService,
            TurLLMCatalogChangeService changeService) {
        this.lifecycleService = lifecycleService;
        this.changeService = changeService;
    }

    @Secured({"ROLE_ADMIN", "LLM_VIEW", "LLM_CREATE", "LLM_EDIT"})
    @Operation(summary = "List LLM instances configured with a deprecated/retired model")
    @GetMapping("/deprecations")
    public List<DeprecatedModelUsage> deprecations() {
        return lifecycleService.findDeprecatedModelsInUse();
    }

    @Secured({"ROLE_ADMIN", "LLM_VIEW", "LLM_CREATE", "LLM_EDIT"})
    @Operation(summary = "Catalog change-feed notifications for configured (in-use) models")
    @GetMapping("/change-feed")
    public List<ChangeNotification> changeFeed() {
        return changeService.notificationsForUsedModels();
    }
}
