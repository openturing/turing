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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * T690 / §XLII.4 — the shared multipart POST to an OpenAI-compatible
 * {@code {baseUrl}/audio/transcriptions} endpoint, reused by both the cloud
 * {@link TurOpenAiTranscriptionProvider} (OPENAI) and the self-hosted
 * {@link TurOpenAiCompatibleTranscriptionProvider} (OPENAI_COMPATIBLE) — only
 * the connection source differs, the wire contract is identical. Fail-soft:
 * every error comes back as a {@link TurTranscriptionResult#fail}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurOpenAiTranscriptionClient {

    static final String DEFAULT_MODEL = "whisper-1";
    private static final String TRANSCRIPTIONS_PATH = "/audio/transcriptions";
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    /**
     * POST {@code audio} to {@code baseUrl}{@code /audio/transcriptions}.
     *
     * @param baseUrl      OpenAI-compatible base URL (must be non-blank)
     * @param apiKey       bearer token; when blank no {@code Authorization} header
     *                     is sent (self-hosted servers often require none)
     * @param model        transcription model (blank → {@value #DEFAULT_MODEL})
     * @param maxBytes     per-request upload limit (backstop; the orchestrator
     *                     chunks below this before calling)
     * @param audio        the audio bytes for this request
     * @param mimeType     audio MIME type (may be null)
     * @param languageHint optional ISO-639 hint (may be null)
     */
    public TurTranscriptionResult transcribe(String baseUrl, String apiKey, String model,
            long maxBytes, byte[] audio, String mimeType, String languageHint) {
        if (audio == null || audio.length == 0) {
            return TurTranscriptionResult.fail("Empty audio.");
        }
        if (StringUtils.isBlank(baseUrl)) {
            return TurTranscriptionResult.fail("No transcription endpoint configured.");
        }
        if (maxBytes > 0 && audio.length > maxBytes) {
            return TurTranscriptionResult.fail(
                    "Audio is %.1f MB, above the %.0f MB per-request limit of the transcription backend. "
                            .formatted(audio.length / BYTES_PER_MB, maxBytes / BYTES_PER_MB)
                            + "Shorten it or re-encode at a lower bitrate / mono (e.g. 64 kbps MP3), "
                            + "or install ffmpeg so large audio is chunked automatically.");
        }
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            String filename = "audio." + extensionFor(mimeType);
            body.part("file", new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(resolveMediaType(mimeType));
            body.part("model", StringUtils.defaultIfBlank(model, DEFAULT_MODEL));
            // verbose_json is a strict superset of json (still carries top-level
            // text/language) and adds per-segment avg_logprob, from which T693
            // derives an optional 0–1 confidence for the local→cloud fallback. A
            // backend that ignores the param simply yields no segments → null.
            body.part("response_format", "verbose_json");
            if (StringUtils.isNotBlank(languageHint)) {
                body.part("language", languageHint.trim());
            }

            RestClient client = RestClient.builder().baseUrl(baseUrl).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(TRANSCRIPTIONS_PATH)
                    .headers(headers -> {
                        if (StringUtils.isNotBlank(apiKey)) {
                            headers.add("Authorization", "Bearer " + apiKey);
                        }
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("text") instanceof String text) || text.isBlank()) {
                return TurTranscriptionResult.fail("Transcription returned no text.");
            }
            Object lang = response.get("language");
            return TurTranscriptionResult.ok(text, lang instanceof String s ? s : languageHint,
                    confidenceFromSegments(response.get("segments")));
        } catch (RuntimeException e) {
            log.warn("[Transcription] failed: {}", e.getMessage());
            return TurTranscriptionResult.fail("Transcription failed: " + e.getMessage());
        }
    }

    /**
     * T693 — derive a 0–1 confidence from a {@code verbose_json} response's
     * {@code segments[].avg_logprob}: the mean segment log-probability mapped
     * through {@code exp(...)} (log-prob → probability) and clamped to [0,1].
     * Returns {@code null} when the backend emits no usable segment logprobs, so
     * a confidence-less backend simply disables the confidence fallback rather
     * than reporting a fake score.
     */
    static Double confidenceFromSegments(Object segments) {
        if (!(segments instanceof java.util.List<?> list) || list.isEmpty()) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (Object element : list) {
            if (element instanceof Map<?, ?> seg
                    && seg.get("avg_logprob") instanceof Number logProb) {
                sum += logProb.doubleValue();
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        return Math.clamp(Math.exp(sum / n), 0.0, 1.0);
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
