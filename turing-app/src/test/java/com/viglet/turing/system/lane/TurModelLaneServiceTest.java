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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T517 — stage → lane → bound-instance resolution, with the fail-safe fallback
 * to the default LLM when a lane is unbound or points at a missing/disabled
 * instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurModelLaneServiceTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;

    private TurModelLaneService service;

    @BeforeEach
    void setUp() {
        service = new TurModelLaneService(globalSettingsService, llmInstanceRepository);
        lenient().when(globalSettingsService.getDefaultLlmId()).thenReturn("default-llm");
    }

    private static TurLLMInstance enabled(boolean on) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setEnabled(on ? 1 : 0);
        return instance;
    }

    @Test
    void usesLaneInstanceWhenBoundAndEnabled() {
        when(globalSettingsService.getModelLaneInstanceId(TurModelLane.REASONING))
                .thenReturn("reasoning-llm");
        when(llmInstanceRepository.findById("reasoning-llm"))
                .thenReturn(Optional.of(enabled(true)));

        assertThat(service.resolveInstanceId(TurModelStage.RERANK)).isEqualTo("reasoning-llm");
    }

    @Test
    void fallsBackToDefaultWhenLaneUnbound() {
        when(globalSettingsService.getModelLaneInstanceId(TurModelLane.FAST)).thenReturn("");

        assertThat(service.resolveInstanceId(TurModelStage.AUTOCOMPLETE)).isEqualTo("default-llm");
    }

    @Test
    void fallsBackToDefaultWhenLaneInstanceDisabled() {
        when(globalSettingsService.getModelLaneInstanceId(TurModelLane.CHEAP))
                .thenReturn("cheap-llm");
        when(llmInstanceRepository.findById("cheap-llm"))
                .thenReturn(Optional.of(enabled(false)));

        assertThat(service.resolveInstanceId(TurModelStage.BACKGROUND_SUMMARIZATION))
                .isEqualTo("default-llm");
    }

    @Test
    void fallsBackToDefaultWhenLaneInstanceMissing() {
        when(globalSettingsService.getModelLaneInstanceId(TurModelLane.REASONING))
                .thenReturn("ghost-llm");
        when(llmInstanceRepository.findById("ghost-llm")).thenReturn(Optional.empty());

        assertThat(service.resolveInstanceId(TurModelStage.JUDGE)).isEqualTo("default-llm");
    }

    @Test
    void nullStageResolvesToDefault() {
        assertThat(service.resolveInstanceId(null)).isEqualTo("default-llm");
    }
}
