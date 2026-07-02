/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system.lane;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T517 / §XXVIII.13 — resolves which {@link TurLLMInstance} id a pipeline
 * {@link TurModelStage} should use, by looking up the binding on the stage's
 * {@link TurModelLane}. The single rule the whole feature rides on:
 *
 * <pre>stage → lane → bound instance (if set, present and enabled) → else default LLM</pre>
 *
 * <p>Fully fail-safe: a blank binding, a binding pointing at a missing or
 * disabled instance, or any lookup error degrades to the platform default LLM —
 * so misconfiguring a lane can never take a stage offline, it just reverts that
 * stage to the legacy single-model behaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurModelLaneService {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;

    public TurModelLaneService(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
    }

    /**
     * Resolves the LLM instance id for {@code stage}: the stage's lane binding
     * when it is set and points at an enabled instance, otherwise the default
     * LLM id (possibly blank when no default is configured).
     */
    public String resolveInstanceId(TurModelStage stage) {
        if (stage == null) {
            return globalSettingsService.getDefaultLlmId();
        }
        String laneId = globalSettingsService.getModelLaneInstanceId(stage.lane());
        if (StringUtils.hasText(laneId) && isUsable(laneId)) {
            return laneId;
        }
        return globalSettingsService.getDefaultLlmId();
    }

    private boolean isUsable(String instanceId) {
        try {
            return llmInstanceRepository.findById(instanceId)
                    .filter(instance -> instance.getEnabled() == 1)
                    .isPresent();
        } catch (RuntimeException e) {
            log.debug("[Lane] instance {} lookup failed; falling back to default LLM: {}",
                    instanceId, e.getMessage());
            return false;
        }
    }
}
