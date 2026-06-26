/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

@ExtendWith(MockitoExtension.class)
class TurVoiceTranslationServiceTest {

    @Mock private TurGlobalSettingsService globalSettingsService;
    @Mock private TurLLMInstanceRepository llmInstanceRepository;
    @Mock private TurLlmModelFactory llmModelFactory;
    @Mock private TurSecretCryptoService secretCryptoService;

    private TurVoiceTranslationService service() {
        return new TurVoiceTranslationService(globalSettingsService, llmInstanceRepository,
                llmModelFactory, secretCryptoService);
    }

    private TurLLMInstance enabledDefaultLlm() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setEnabled(1);
        instance.setApiKeyEncrypted("enc");
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        return instance;
    }

    @Test
    void translatesViaDefaultLlmAndTrims() {
        TurLLMInstance instance = enabledDefaultLlm();
        when(secretCryptoService.decrypt("enc")).thenReturn("sk-key");
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("  Hello  ")))));
        when(llmModelFactory.createChatModel(eq(instance), eq("sk-key"))).thenReturn(model);

        String out = service().translate("English", "pt-BR", "Olá");
        assertThat(out).isEqualTo("Hello");
    }

    @Test
    void throwsWhenNoDefaultLlm() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null);
        assertThatThrownBy(() -> service().translate("English", null, "Olá"))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("default LLM");
    }

    @Test
    void throwsOnBlankText() {
        assertThatThrownBy(() -> service().translate("English", null, "  "))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("Nothing to translate");
    }

    @Test
    void throwsOnBlankTargetLang() {
        assertThatThrownBy(() -> service().translate("  ", null, "Olá"))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("target language");
    }

    @Test
    void throwsWhenDefaultLlmDisabled() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setEnabled(0);
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        assertThatThrownBy(() -> service().translate("English", null, "Olá"))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void wrapsModelFailureAsVoiceException() {
        TurLLMInstance instance = enabledDefaultLlm();
        when(secretCryptoService.decrypt("enc")).thenReturn("sk-key");
        ChatModel model = mock(ChatModel.class);
        lenient().when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));
        when(llmModelFactory.createChatModel(eq(instance), eq("sk-key"))).thenReturn(model);
        assertThatThrownBy(() -> service().translate("English", null, "Olá"))
                .isInstanceOf(TurRealtimeVoiceException.class)
                .hasMessageContaining("Translation failed");
    }
}
