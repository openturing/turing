/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * The {@link TurTranscriptionProviderType#OPENAI} backend: a POST to an
 * OpenAI-compatible {@code /audio/transcriptions} endpoint whose {@code baseUrl}
 * and credential come from the dedicated transcription config, <b>falling back</b>
 * to the default {@code TurLLMInstance} for any unset field (back-compat — a
 * legacy install with no transcription config behaves exactly as before). The
 * multipart wire call itself lives in the shared {@link TurOpenAiTranscriptionClient}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurOpenAiTranscriptionProvider implements TurTranscriptionProvider {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSecretCryptoService secretCryptoService;
    private final TurTranscriptionConfigResolver configResolver;
    private final TurOpenAiTranscriptionClient client;

    public TurOpenAiTranscriptionProvider(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSecretCryptoService secretCryptoService,
            TurTranscriptionConfigResolver configResolver,
            TurOpenAiTranscriptionClient client) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.secretCryptoService = secretCryptoService;
        this.configResolver = configResolver;
        this.client = client;
    }

    @Override
    public TurTranscriptionProviderType getType() {
        return TurTranscriptionProviderType.OPENAI;
    }

    @Override
    public boolean isAvailable() {
        return resolveConnection() != null;
    }

    @Override
    public TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint) {
        Connection connection = resolveConnection();
        if (connection == null) {
            return TurTranscriptionResult.fail(
                    "No transcription endpoint configured (set turing.transcription.* or a default LLM).");
        }
        TurTranscriptionConfig config = configResolver.resolve();
        return client.transcribe(connection.baseUrl(), connection.apiKey(), config.model(),
                config.maxUploadBytes(), audio, mimeType, languageHint);
    }

    /**
     * Resolve the effective {@code baseUrl} + {@code apiKey}: a dedicated
     * transcription endpoint/key (from config) is used as-is; any gap is filled
     * from the default {@code TurLLMInstance}. Returns {@code null} when no usable
     * credential can be resolved.
     */
    private Connection resolveConnection() {
        TurTranscriptionConfig config = configResolver.resolve();
        String baseUrl = config.endpoint();
        String apiKey = config.apiKey();

        if (StringUtils.isBlank(baseUrl) || StringUtils.isBlank(apiKey)) {
            TurLLMInstance instance = resolveInstance();
            if (instance != null) {
                if (StringUtils.isBlank(baseUrl)) {
                    baseUrl = StringUtils.defaultIfBlank(instance.getUrl(), DEFAULT_BASE_URL);
                }
                if (StringUtils.isBlank(apiKey)) {
                    apiKey = secretCryptoService.decrypt(instance.getApiKeyEncrypted());
                }
            }
        }
        if (StringUtils.isBlank(baseUrl)) {
            baseUrl = DEFAULT_BASE_URL;
        }
        return StringUtils.isBlank(apiKey) ? null : new Connection(baseUrl, apiKey);
    }

    private TurLLMInstance resolveInstance() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (StringUtils.isBlank(llmId)) {
            return null;
        }
        TurLLMInstance instance = llmInstanceRepository.findById(llmId).orElse(null);
        return (instance != null && instance.getEnabled() == 1) ? instance : null;
    }

    /** Resolved endpoint + credential for a single transcription call. */
    private record Connection(String baseUrl, String apiKey) {
    }
}
