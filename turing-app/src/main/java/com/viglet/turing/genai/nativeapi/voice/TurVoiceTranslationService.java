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
package com.viglet.turing.genai.nativeapi.voice;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T150 / §X.6.d — simultaneous-interpretation helper. While a real-time voice
 * session (T147/T148) converses in the visitor's language, an operator watching
 * in spectator mode (T120) wants a live translation of every utterance. This
 * service turns one short transcript segment into the operator's language using
 * the default LLM — the same provider-agnostic path {@code TurLlmSummaryService}
 * uses, so it works with any configured vendor today.
 *
 * <p>Per-utterance and ephemeral: unlike the summary service there is no cache
 * (each spoken segment is unique) and the prompt is deliberately tiny (translate
 * only, no commentary) to keep latency low. Failures surface as
 * {@link TurRealtimeVoiceException} so the spectator panel can show a hint
 * rather than a half-translation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurVoiceTranslationService {

    private static final int MAX_SEGMENT_CHARS = 4000;

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;

    public TurVoiceTranslationService(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
    }

    /**
     * Translate {@code text} into {@code targetLang}. {@code sourceLang} is an
     * optional hint ({@code null}/blank → let the model auto-detect).
     *
     * @throws TurRealtimeVoiceException when no default LLM is configured, the
     *         segment is blank/too long, or the call fails.
     */
    public String translate(String targetLang, String sourceLang, String text) {
        if (!StringUtils.hasText(text)) {
            throw new TurRealtimeVoiceException("Nothing to translate");
        }
        if (text.length() > MAX_SEGMENT_CHARS) {
            throw new TurRealtimeVoiceException("Segment too long to translate");
        }
        if (!StringUtils.hasText(targetLang)) {
            throw new TurRealtimeVoiceException("No target language given");
        }

        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            throw new TurRealtimeVoiceException("No default LLM configured in Global Settings");
        }
        TurLLMInstance llmInstance = llmInstanceRepository.findById(llmId).orElse(null);
        if (llmInstance == null || llmInstance.getEnabled() != 1) {
            throw new TurRealtimeVoiceException("Default LLM not found or disabled");
        }

        try {
            String decryptedApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, decryptedApiKey);
            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt(targetLang, sourceLang)),
                    new UserMessage(text));
            var response = chatModel.call(new Prompt(messages));
            String content = response.getResult().getOutput().getText();
            return content == null ? "" : content.trim();
        } catch (TurRealtimeVoiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Voice] translation failed: {}", e.getMessage());
            throw new TurRealtimeVoiceException("Translation failed: " + e.getMessage(), e);
        }
    }

    private String systemPrompt(String targetLang, String sourceLang) {
        String from = StringUtils.hasText(sourceLang) ? (" from " + sourceLang) : "";
        return "You are a simultaneous interpreter. Translate the user's message"
                + from + " into " + targetLang + ". Output ONLY the translation — no quotes, "
                + "no notes, no preface. Preserve the speaker's tone and keep it concise.";
    }
}
