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

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link TurTranscriptionProvider}: a direct multipart POST to an
 * OpenAI-compatible {@code {baseUrl}/audio/transcriptions} endpoint (Spring AI
 * 2.0.0-M2 ships no transcription model, so this is a provider-neutral REST
 * call). Credentials and base URL come from the configured default
 * {@code TurLLMInstance}; air-gapped installs override this bean with a local
 * STT ({@code @Primary}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOpenAiTranscriptionProvider implements TurTranscriptionProvider {

    private static final String DEFAULT_MODEL = "whisper-1";
    private static final String TRANSCRIPTIONS_PATH = "/audio/transcriptions";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /**
     * Hard upload limit of the OpenAI transcription endpoint: 25 MiB
     * (26,214,400 bytes). Sending more returns a raw {@code 413 Payload Too
     * Large}, so we fail fast with an actionable message instead of shipping the
     * whole blob over the wire. A local/air-gapped STT bean overriding this
     * provider ({@code @Primary}) has its own limits and is unaffected.
     */
    private static final long MAX_AUDIO_BYTES = 26_214_400L;
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSecretCryptoService secretCryptoService;

    public TurOpenAiTranscriptionProvider(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSecretCryptoService secretCryptoService) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.secretCryptoService = secretCryptoService;
    }

    @Override
    public boolean isAvailable() {
        return resolveInstance() != null;
    }

    @Override
    public TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint) {
        if (audio == null || audio.length == 0) {
            return TurTranscriptionResult.fail("Empty audio.");
        }
        if (audio.length > MAX_AUDIO_BYTES) {
            return TurTranscriptionResult.fail(
                    "Audio is %.1f MB, above the %.0f MB limit of the OpenAI transcription API. "
                            .formatted(audio.length / BYTES_PER_MB, MAX_AUDIO_BYTES / BYTES_PER_MB)
                            + "Shorten it or re-encode at a lower bitrate / mono (e.g. 64 kbps MP3) and retry.");
        }
        TurLLMInstance instance = resolveInstance();
        if (instance == null) {
            return TurTranscriptionResult.fail("No default LLM configured for transcription.");
        }
        try {
            String apiKey = secretCryptoService.decrypt(instance.getApiKeyEncrypted());
            String baseUrl = StringUtils.defaultIfBlank(instance.getUrl(), DEFAULT_BASE_URL);

            MultipartBodyBuilder body = new MultipartBodyBuilder();
            String filename = "audio." + extensionFor(mimeType);
            body.part("file", new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(resolveMediaType(mimeType));
            body.part("model", DEFAULT_MODEL);
            body.part("response_format", "json");
            if (StringUtils.isNotBlank(languageHint)) {
                body.part("language", languageHint.trim());
            }

            RestClient client = RestClient.builder().baseUrl(baseUrl).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(TRANSCRIPTIONS_PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("text") instanceof String text)
                    || text.isBlank()) {
                return TurTranscriptionResult.fail("Transcription returned no text.");
            }
            Object lang = response.get("language");
            return TurTranscriptionResult.ok(text, lang instanceof String s ? s : languageHint);
        } catch (RuntimeException e) {
            log.warn("[Transcription] failed: {}", e.getMessage());
            return TurTranscriptionResult.fail("Transcription failed: " + e.getMessage());
        }
    }

    private TurLLMInstance resolveInstance() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (StringUtils.isBlank(llmId)) {
            return null;
        }
        TurLLMInstance instance = llmInstanceRepository.findById(llmId).orElse(null);
        return (instance != null && instance.getEnabled() == 1) ? instance : null;
    }

    private MediaType resolveMediaType(String mimeType) {
        try {
            return StringUtils.isBlank(mimeType)
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mimeType);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String extensionFor(String mimeType) {
        if (mimeType == null) {
            return "mp3";
        }
        return switch (mimeType.toLowerCase()) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a";
            case "audio/webm" -> "webm";
            case "audio/ogg", "application/ogg" -> "ogg";
            case "audio/flac" -> "flac";
            default -> "mp3";
        };
    }
}
