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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T728 / §XLVI.4 — the per-stage "Big Shuffle" model-lane resolution chain:
 * stage lane → study-wide instance → default LLM, each tier fail-open when its
 * instance is blank / missing / disabled.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurResearchModelLaneResolverTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;

    private TurResearchModelLaneResolver resolver() {
        return new TurResearchModelLaneResolver(globalSettingsService, llmInstanceRepository);
    }

    private void enabled(String id) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId(id);
        instance.setEnabled(1);
        lenient().when(llmInstanceRepository.findById(id)).thenReturn(Optional.of(instance));
    }

    private void disabled(String id) {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId(id);
        instance.setEnabled(0);
        lenient().when(llmInstanceRepository.findById(id)).thenReturn(Optional.of(instance));
    }

    @Test
    void stageLaneWinsWhenSetAndEnabled() {
        enabled("fast-llm");
        TurResearchStudy study = new TurResearchStudy();
        study.setInterviewLlmInstanceId("fast-llm");
        study.setLlmInstanceId("study-llm");

        assertThat(resolver().resolveInstanceId(study, TurResearchStage.INTERVIEW))
                .isEqualTo("fast-llm");
    }

    @Test
    void fallsBackToStudyWideInstanceWhenStageLaneBlank() {
        enabled("study-llm");
        TurResearchStudy study = new TurResearchStudy();
        study.setLlmInstanceId("study-llm");

        assertThat(resolver().resolveInstanceId(study, TurResearchStage.SYNTHESIS))
                .isEqualTo("study-llm");
    }

    @Test
    void fallsThroughDisabledStageLaneToDefault() {
        disabled("gone-llm");
        when(globalSettingsService.getDefaultLlmId()).thenReturn("default-llm");
        TurResearchStudy study = new TurResearchStudy();
        study.setSynthesisLlmInstanceId("gone-llm");

        assertThat(resolver().resolveInstanceId(study, TurResearchStage.SYNTHESIS))
                .isEqualTo("default-llm");
    }

    @Test
    void fallsBackToDefaultWhenNothingSet() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn("default-llm");

        assertThat(resolver().resolveInstanceId(new TurResearchStudy(), TurResearchStage.INTERVIEW))
                .isEqualTo("default-llm");
    }

    @Test
    void stagesResolveIndependently() {
        enabled("interview-llm");
        enabled("synthesis-llm");
        TurResearchStudy study = new TurResearchStudy();
        study.setInterviewLlmInstanceId("interview-llm");
        study.setSynthesisLlmInstanceId("synthesis-llm");

        assertThat(resolver().resolveInstanceId(study, TurResearchStage.INTERVIEW))
                .isEqualTo("interview-llm");
        assertThat(resolver().resolveInstanceId(study, TurResearchStage.SYNTHESIS))
                .isEqualTo("synthesis-llm");
    }

    @Test
    void blankDefaultReturnsEmptyString() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null);

        assertThat(resolver().resolveInstanceId(new TurResearchStudy(), TurResearchStage.INTERVIEW))
                .isEmpty();
    }
}
