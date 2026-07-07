/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.callcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.callcenter.TurCallCenterAssistService.CallAssistResult;
import com.viglet.turing.genai.nativeapi.voice.TurVoiceTranslationService;
import com.viglet.turing.mcp.server.TurMcpRagToolService;
import com.viglet.turing.system.TurLlmSummaryService;

/**
 * T189 / §X.15.c — unit tests for {@link TurCallCenterAssistService}: the
 * translate → cited-answer → translate-back loop, the same-language short-circuit,
 * fail-soft translation, and bad-request handling.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurCallCenterAssistServiceTest {

    @Mock
    private TurVoiceTranslationService translationService;

    @Mock
    private TurMcpRagToolService ragToolService;

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @InjectMocks
    private TurCallCenterAssistService service;

    @Test
    void crossLingualTranslatesQueryAnswersAndTranslatesBack() {
        lenient().when(llmSummaryService.isAvailable()).thenReturn(true);
        // utterance (Spanish) → operator working language (English)
        when(translationService.translate("English", "Spanish", "¿cuánto cuesta el plan Pro?"))
                .thenReturn("How much is the Pro plan?");
        when(ragToolService.ragAnswer(eq("store"), eq("en_US"), eq("How much is the Pro plan?"), eq(5)))
                .thenReturn("The Pro plan is $20/mo. [1]\n\nSources:\n[1] Pricing — /p");
        // answer (English) → customer language (Spanish)
        when(translationService.translate(eq("Spanish"), eq("English"),
                eq("The Pro plan is $20/mo. [1]\n\nSources:\n[1] Pricing — /p")))
                .thenReturn("El plan Pro cuesta $20/mes. [1]");

        CallAssistResult r = service.assist("store", "en_US", "¿cuánto cuesta el plan Pro?",
                "Spanish", "English", 5);

        assertThat(r.success()).isTrue();
        assertThat(r.operatorQuery()).isEqualTo("How much is the Pro plan?");
        assertThat(r.translatedQuery()).isTrue();
        assertThat(r.answer()).contains("The Pro plan is $20/mo.");
        assertThat(r.answerForCustomer()).isEqualTo("El plan Pro cuesta $20/mes. [1]");
        assertThat(r.customerLang()).isEqualTo("Spanish");
        assertThat(r.operatorLang()).isEqualTo("English");
        assertThat(r.ragAvailable()).isTrue();
    }

    @Test
    void sameLanguageSkipsTranslationEntirely() {
        lenient().when(llmSummaryService.isAvailable()).thenReturn(true);
        when(ragToolService.ragAnswer(eq("store"), any(), eq("What are your hours?"), any()))
                .thenReturn("We're open 9-5. [1]");

        // No customerLang → not cross-lingual; the utterance is the query verbatim.
        CallAssistResult r = service.assist("store", null, "What are your hours?",
                null, "English", null);

        assertThat(r.translatedQuery()).isFalse();
        assertThat(r.operatorQuery()).isEqualTo("What are your hours?");
        assertThat(r.answerForCustomer()).isNull();
        assertThat(r.customerLang()).isNull();
        verifyNoInteractions(translationService);
    }

    @Test
    void translationFailureFallsBackToOriginalUtterance() {
        lenient().when(llmSummaryService.isAvailable()).thenReturn(true);
        // The inbound translation fails → the original utterance is used as the query.
        when(translationService.translate("English", "Spanish", "hola"))
                .thenThrow(new RuntimeException("LLM down"));
        when(ragToolService.ragAnswer(eq("store"), any(), eq("hola"), any()))
                .thenReturn("Hello! How can I help? [1]");

        CallAssistResult r = service.assist("store", null, "hola", "Spanish", "English", null);

        assertThat(r.success()).isTrue();
        assertThat(r.translatedQuery()).isFalse();          // fell back
        assertThat(r.operatorQuery()).isEqualTo("hola");
        assertThat(r.answer()).contains("Hello!");
        // Back-translation is still attempted on the answer (unstubbed → null here),
        // so the assist produces no customer-facing translation but never blows up.
        verify(translationService).translate(eq("Spanish"), eq("English"), any());
        assertThat(r.answerForCustomer()).isNull();
    }

    @Test
    void missingSiteOrUtteranceIsABadRequest() {
        CallAssistResult noSite = service.assist("  ", null, "hello", null, "English", null);
        assertThat(noSite.success()).isFalse();
        assertThat(noSite.error()).contains("site");

        CallAssistResult noUtterance = service.assist("store", null, "  ", null, "English", null);
        assertThat(noUtterance.success()).isFalse();
        assertThat(noUtterance.error()).contains("customerUtterance");

        verifyNoInteractions(translationService, ragToolService);
        verify(ragToolService, never()).ragAnswer(any(), any(), any(), any());
    }
}
