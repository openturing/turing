/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T728 / §XLVI.4 — resolves which {@code TurLLMInstance} id a study
 * {@link TurResearchStage} should use ("Big Shuffle" per-stage model lanes). The
 * precedence, in the same fail-open spirit as the T517 global model-lane seam:
 *
 * <pre>stage lane (if set + enabled) → study-wide llmInstanceId (if set + enabled) → default LLM</pre>
 *
 * <p>Fully fail-safe: a blank binding, or one pointing at a missing/disabled
 * instance, degrades to the next tier — so misconfiguring a lane can never take a
 * stage offline, it just reverts that stage to the coarser behaviour. Leaving both
 * per-stage lanes blank reproduces the pre-T728 single-model study exactly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchModelLaneResolver {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;

    public TurResearchModelLaneResolver(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
    }

    /**
     * The resolved LLM instance id for {@code stage} on {@code study}, or a blank
     * string when not even a default LLM is configured. Never throws.
     */
    public String resolveInstanceId(TurResearchStudy study, TurResearchStage stage) {
        String staged = switch (stage) {
            case INTERVIEW -> study.getInterviewLlmInstanceId();
            case SYNTHESIS -> study.getSynthesisLlmInstanceId();
        };
        if (isUsable(staged)) {
            return staged.trim();
        }
        String overall = study.getLlmInstanceId();
        if (isUsable(overall)) {
            return overall.trim();
        }
        return StringUtils.defaultString(globalSettingsService.getDefaultLlmId());
    }

    private boolean isUsable(String instanceId) {
        if (StringUtils.isBlank(instanceId)) {
            return false;
        }
        try {
            return llmInstanceRepository.findById(instanceId.trim())
                    .filter(instance -> instance.getEnabled() == 1)
                    .isPresent();
        } catch (RuntimeException e) {
            log.debug("[Research] lane instance {} lookup failed; falling back: {}",
                    instanceId, e.getMessage());
            return false;
        }
    }
}
