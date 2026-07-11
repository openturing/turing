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
 * call). Serves the {@link TurTranscriptionProviderType#OPENAI} strategy.
 *
 * <p>T687 / §XLII.1 — connection details now come from the resolved
 * {@link TurTranscriptionConfig} (Global Settings / {@code turing.transcription.*}):
 * a dedicated {@code endpoint} / {@code model} / {@code apiKey} decoupled from
 * the default {@code TurLLMInstance}. Any of those left blank <b>falls back</b>
 * to the default LLM instance, so a legacy install with no transcription config
 * behaves exactly as before. The 25 MiB constant is now the configurable
 * per-provider {@link TurTranscriptionConfig#maxUploadBytes()} (superseded by the
 * chunking orchestrator in T689).
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
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSecretCryptoService secretCryptoService;
    private final TurTranscriptionConfigResolver configResolver;

    public TurOpenAiTranscriptionProvider(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSecretCryptoService secretCryptoService,
            TurTranscriptionConfigResolver configResolver) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.secretCryptoService = secretCryptoService;
        this.configResolver = configResolver;
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
        if (audio == null || audio.length == 0) {
            return TurTranscriptionResult.fail("Empty audio.");
        }
        TurTranscriptionConfig config = configResolver.resolve();
        long maxBytes = config.maxUploadBytes();
        if (audio.length > maxBytes) {
            return TurTranscriptionResult.fail(
                    "Audio is %.1f MB, above the %.0f MB per-request limit of the transcription backend. "
                            .formatted(audio.length / BYTES_PER_MB, maxBytes / BYTES_PER_MB)
                            + "Shorten it or re-encode at a lower bitrate / mono (e.g. 64 kbps MP3) and retry.");
        }
        Connection connection = resolveConnection();
        if (connection == null) {
            return TurTranscriptionResult.fail(
                    "No transcription endpoint configured (set turing.transcription.* or a default LLM).");
        }
        try {
            String model = StringUtils.defaultIfBlank(config.model(), DEFAULT_MODEL);

            MultipartBodyBuilder body = new MultipartBodyBuilder();
            String filename = "audio." + extensionFor(mimeType);
            body.part("file", new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(resolveMediaType(mimeType));
            body.part("model", model);
            body.part("response_format", "json");
            if (StringUtils.isNotBlank(languageHint)) {
                body.part("language", languageHint.trim());
            }

            RestClient client = RestClient.builder().baseUrl(connection.baseUrl()).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(TRANSCRIPTIONS_PATH)
                    .header("Authorization", "Bearer " + connection.apiKey())
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

    /**
     * Resolve the effective {@code baseUrl} + {@code apiKey}. A dedicated
     * transcription endpoint/key (from config) is used as-is; any gap is filled
     * from the default {@code TurLLMInstance} (back-compat). Returns {@code null}
     * when neither a full dedicated config nor a usable default LLM is available.
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
        // A dedicated endpoint with its own key needs no default base URL.
        if (StringUtils.isNotBlank(baseUrl) && StringUtils.isBlank(apiKey)) {
            return null;
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

    /** Resolved endpoint + credential for a single transcription call. */
    private record Connection(String baseUrl, String apiKey) {
    }
}
