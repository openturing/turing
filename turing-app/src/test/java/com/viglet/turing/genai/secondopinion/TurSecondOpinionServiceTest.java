/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.secondopinion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T522 — the second-opinion service's gating + the pure verdict-parse logic.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSecondOpinionServiceTest {

    @Mock private TurGlobalSettingsService globalSettingsService;
    @Mock private TurLLMInstanceRepository llmInstanceRepository;
    @Mock private TurSecretCryptoService secretCryptoService;
    @Mock private TurLlmModelFactory llmModelFactory;
    @Mock private TurGenAiLlmProviderFactory providerFactory;

    private TurSecondOpinionService service() {
        return new TurSecondOpinionService(globalSettingsService, llmInstanceRepository,
                secretCryptoService, llmModelFactory, providerFactory);
    }

    private static TurLLMInstance critic() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setModelName("claude-haiku-cheap");
        return instance;
    }

    @Test
    void isEnabledRequiresFlagAndCriticInstance() {
        when(globalSettingsService.isSecondOpinionEnabled()).thenReturn(true);
        when(globalSettingsService.getSecondOpinionLlmId()).thenReturn("critic-1");
        assertThat(service().isEnabled()).isTrue();
    }

    @Test
    void disabledReturnsEmpty() {
        when(globalSettingsService.isSecondOpinionEnabled()).thenReturn(false);
        lenient().when(globalSettingsService.getSecondOpinionLlmId()).thenReturn("critic-1");
        assertThat(service().evaluate("q", "a", List.of(), null)).isEmpty();
    }

    @Test
    void parseAgreeVerdict() {
        TurSecondOpinion v = TurSecondOpinionService.parse(
                "AGREE\nConfidence: 80%\nThe answer matches the cited policy.", critic(), "anthropic");
        assertThat(v.agree()).isTrue();
        assertThat(v.confidence()).isCloseTo(0.80, within(1e-9));
        assertThat(v.rationale()).isEqualTo("The answer matches the cited policy.");
        assertThat(v.criticModel()).isEqualTo("claude-haiku-cheap");
        assertThat(v.criticVendor()).isEqualTo("anthropic");
    }

    @Test
    void parseDisagreeVerdict() {
        TurSecondOpinion v = TurSecondOpinionService.parse(
                "DISAGREE\nThe answer claims a fact not present in the context.", critic(), "anthropic");
        assertThat(v.agree()).isFalse();
        assertThat(v.confidence()).isNull();
        assertThat(v.rationale()).contains("not present in the context");
    }

    @Test
    void parseUnparseableReplyIsTreatedAsNonAgreement() {
        TurSecondOpinion v = TurSecondOpinionService.parse("hmm, unclear", critic(), "gemini");
        assertThat(v.agree()).isFalse();
    }
}
