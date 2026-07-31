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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T690 / §XLII.4 — the shared multipart POST to an OpenAI-compatible
 * {@code {baseUrl}/audio/transcriptions} endpoint, reused by both the cloud
 * {@link TurOpenAiTranscriptionProvider} (OPENAI) and the self-hosted
 * {@link TurOpenAiCompatibleTranscriptionProvider} (OPENAI_COMPATIBLE) — only
 * the connection source differs, the wire contract is identical. Fail-soft:
 * every error comes back as a {@link TurTranscriptionResult#fail}.
 *
 * <p>T846 / §LXIII.1 — it stays <b>one</b> client, but the request is no longer
 * whisper-shaped by construction: the {@link TurTranscriptionTarget} carries the
 * caller's own default model and the {@link TurTranscriptionModelFamily} derived
 * from the effective model picks {@code response_format}
 * ({@code verbose_json} vs {@code json}), the language field ({@code language} vs
 * {@code languages}) and whether a confidence can honestly be derived at all.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurOpenAiTranscriptionClient {

    private static final String TRANSCRIPTIONS_PATH = "/audio/transcriptions";
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    /** T849 — the two stream events that carry transcript text. */
    private static final String STREAM_DELTA_EVENT = "transcript.text.delta";
    private static final String STREAM_DONE_EVENT = "transcript.text.done";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    /**
     * POST {@code audio} to {@code target.baseUrl()}{@code /audio/transcriptions}.
     *
     * @param target       endpoint, credential, model + per-request upload limit
     * @param audio        the audio bytes for this request
     * @param mimeType     audio MIME type (may be null)
     * @param languageHint optional ISO-639 hint (may be null)
     * @param hints        T847 context hints; {@code null} is treated as
     *                     {@link TurTranscriptionHints#none()} and any hint the
     *                     model family does not accept is dropped silently
     */
    public TurTranscriptionResult transcribe(TurTranscriptionTarget target, byte[] audio,
            String mimeType, String languageHint, TurTranscriptionHints hints) {
        return transcribe(target, audio, mimeType, languageHint, hints,
                TurTranscriptionPartialListener.NOOP);
    }

    /**
     * T849 / §LXIII.4 — as above, but when {@code partial} is a real listener and
     * the model family can stream ({@code gpt-*transcribe}), the request is sent
     * with {@code stream=true} and transcript deltas are relayed as they arrive.
     *
     * <p><b>Fail-open by construction:</b> any streaming failure — an unsupported
     * flag, a proxy that buffers the body, a malformed event — falls back to the
     * ordinary blocking call, which produces the same final transcript. Streaming
     * can therefore only ever cost a retry, never a result.
     */
    public TurTranscriptionResult transcribe(TurTranscriptionTarget target, byte[] audio,
            String mimeType, String languageHint, TurTranscriptionHints hints,
            TurTranscriptionPartialListener partial) {
        TurTranscriptionResult rejected = reject(target, audio);
        if (rejected != null) {
            return rejected;
        }
        TurTranscriptionModelFamily family = target.family();
        TurTranscriptionHints context = hints == null ? TurTranscriptionHints.none() : hints;

        // T849 — stream only when the family can and someone is actually rendering
        // the text. A null return means "streaming didn't work" → blocking call.
        if (streamingWanted(partial, family)) {
            TurTranscriptionResult streamed = transcribeStreaming(
                    target, family, audio, mimeType, languageHint, context, partial);
            if (streamed != null) {
                return streamed;
            }
        }
        return transcribeBlocking(target, family, audio, mimeType, languageHint, context);
    }

    /** The pre-flight guards, as one nullable rejection. */
    private TurTranscriptionResult reject(TurTranscriptionTarget target, byte[] audio) {
        if (audio == null || audio.length == 0) {
            return TurTranscriptionResult.fail("Empty audio.");
        }
        if (target == null || StringUtils.isBlank(target.baseUrl())) {
            return TurTranscriptionResult.fail("No transcription endpoint configured.");
        }
        long maxBytes = target.maxBytes();
        if (maxBytes > 0 && audio.length > maxBytes) {
            return TurTranscriptionResult.fail(
                    "Audio is %.1f MB, above the %.0f MB per-request limit of the transcription backend. "
                            .formatted(audio.length / BYTES_PER_MB, maxBytes / BYTES_PER_MB)
                            + "Shorten it or re-encode at a lower bitrate / mono (e.g. 64 kbps MP3), "
                            + "or install ffmpeg so large audio is chunked automatically.");
        }
        return null;
    }

    /** T849 — there is both something to stream to and a family that can stream. */
    static boolean streamingWanted(TurTranscriptionPartialListener partial,
            TurTranscriptionModelFamily family) {
        return partial != null
                && partial != TurTranscriptionPartialListener.NOOP
                && family.supportsStreaming();
    }

    /** The original one-shot POST — unchanged behaviour for every non-streaming call. */
    private TurTranscriptionResult transcribeBlocking(TurTranscriptionTarget target,
            TurTranscriptionModelFamily family, byte[] audio, String mimeType,
            String languageHint, TurTranscriptionHints hints) {
        try {
            MultipartBodyBuilder body =
                    buildBody(target, family, audio, mimeType, languageHint, hints);

            RestClient client = RestClient.builder().baseUrl(target.baseUrl()).build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(TRANSCRIPTIONS_PATH)
                    .headers(headers -> authorize(headers, target))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(Map.class);

            if (response == null || !(response.get("text") instanceof String text) || text.isBlank()) {
                return TurTranscriptionResult.fail("Transcription returned no text.");
            }
            Object lang = response.get("language");
            return TurTranscriptionResult.ok(text, lang instanceof String s ? s : languageHint,
                    confidenceFor(family, response.get("segments")));
        } catch (RuntimeException e) {
            log.warn("[Transcription] failed on model '{}' ({}): {}",
                    target.effectiveModel(), family, e.getMessage());
            return TurTranscriptionResult.fail("Transcription failed: " + e.getMessage());
        }
    }

    /**
     * T849 — the same multipart POST with {@code stream=true}, reading the SSE
     * body and relaying each {@code transcript.text.delta} to {@code partial}.
     *
     * @return the completed result, or {@code null} when streaming could not be
     *         used at all — the caller then retries the blocking path. Returning
     *         {@code null} rather than a failure is the whole fail-open contract:
     *         a buffering proxy or an older endpoint must cost a retry, not the
     *         transcript.
     */
    private TurTranscriptionResult transcribeStreaming(TurTranscriptionTarget target,
            TurTranscriptionModelFamily family, byte[] audio, String mimeType,
            String languageHint, TurTranscriptionHints hints,
            TurTranscriptionPartialListener partial) {
        MultipartBodyBuilder body = buildBody(target, family, audio, mimeType, languageHint, hints);
        body.part("stream", "true");
        try {
            RestClient client = RestClient.builder().baseUrl(target.baseUrl()).build();
            return client.post()
                    .uri(TRANSCRIPTIONS_PATH)
                    .headers(headers -> {
                        authorize(headers, target);
                        headers.add("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.debug("[Transcription] stream rejected with HTTP {} — falling back",
                                    response.getStatusCode());
                            return null;
                        }
                        return readStream(response.getBody(), languageHint, partial);
                    }, false);
        } catch (RuntimeException e) {
            log.debug("[Transcription] streaming unavailable on model '{}' ({}) — falling back: {}",
                    target.effectiveModel(), family, e.getMessage());
            return null;
        }
    }

    /**
     * Consume the SSE body, accumulating {@code transcript.text.delta} events and
     * finishing on {@code transcript.text.done}. Returns {@code null} when the
     * stream yielded no text at all, so the caller falls back rather than
     * reporting an empty transcript.
     */
    private TurTranscriptionResult readStream(InputStream bodyStream, String languageHint,
            TurTranscriptionPartialListener partial) throws IOException {
        StreamAccumulator accumulator = new StreamAccumulator(languageHint);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode event = parseFrame(line);
                if (event != null) {
                    accumulator.accept(event, text -> notifyPartial(partial, text));
                }
            }
        }
        return accumulator.result();
    }

    /**
     * One SSE line → its parsed event, or {@code null} for a comment, a blank, the
     * terminator, or an unparseable payload. A malformed frame is skipped rather
     * than fatal — the next one may still be good.
     */
    private JsonNode parseFrame(String line) {
        String payload = ssePayload(line);
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (RuntimeException e) {
            log.debug("[Transcription] skipping unparseable stream frame: {}", e.getMessage());
            return null;
        }
    }

    /** {@code data: {...}} → the JSON payload; anything else (comments, blanks) → null. */
    static String ssePayload(String line) {
        if (line == null || !line.startsWith("data:")) {
            return null;
        }
        String payload = line.substring("data:".length()).trim();
        return payload.isEmpty() || "[DONE]".equals(payload) ? null : payload;
    }

    /** A listener must never be able to abort a transcription that is working. */
    private void notifyPartial(TurTranscriptionPartialListener partial, String text) {
        try {
            partial.onPartialText(text);
        } catch (RuntimeException e) {
            log.debug("[Transcription] partial-text listener threw: {}", e.getMessage());
        }
    }

    private static void authorize(HttpHeaders headers, TurTranscriptionTarget target) {
        if (StringUtils.isNotBlank(target.apiKey())) {
            headers.add("Authorization", "Bearer " + target.apiKey());
        }
    }

    /**
     * T849 — folds the transcript stream's events into one result. Package-private
     * so the event mapping can be tested without an HTTP round-trip.
     */
    static final class StreamAccumulator {

        private final StringBuilder accumulated = new StringBuilder();
        private String finalText;
        private String language;

        StreamAccumulator(String languageHint) {
            this.language = languageHint;
        }

        /** Apply one event, notifying {@code onText} with the text so far on a delta. */
        void accept(JsonNode event, java.util.function.Consumer<String> onText) {
            String type = event.path("type").asString("");
            if (STREAM_DELTA_EVENT.equals(type)) {
                String delta = event.path("delta").asString("");
                if (!delta.isEmpty()) {
                    accumulated.append(delta);
                    onText.accept(accumulated.toString());
                }
            } else if (STREAM_DONE_EVENT.equals(type)) {
                finalText = event.path("text").asString(accumulated.toString());
                String eventLanguage = event.path("language").asString("");
                if (!eventLanguage.isBlank()) {
                    language = eventLanguage;
                }
            }
        }

        /** The completed result, or {@code null} when the stream carried no text. */
        TurTranscriptionResult result() {
            String text = StringUtils.defaultIfBlank(finalText, accumulated.toString());
            if (StringUtils.isBlank(text)) {
                return null;
            }
            // gpt-*transcribe returns no segment logprobs, streamed or not (T846).
            return TurTranscriptionResult.ok(text, language, null);
        }
    }

    /** The multipart body in the dialect {@code family} speaks. */
    private MultipartBodyBuilder buildBody(TurTranscriptionTarget target,
            TurTranscriptionModelFamily family, byte[] audio, String mimeType,
            String languageHint, TurTranscriptionHints hints) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        String filename = "audio." + extensionFor(mimeType);
        body.part("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(resolveMediaType(mimeType));
        body.part("model", target.effectiveModel());
        // T846 — verbose_json is a strict superset of json (still carries top-level
        // text/language) and adds per-segment avg_logprob, from which T693 derives an
        // optional 0–1 confidence. Only the whisper family serves it; the gpt-*transcribe
        // families reject it, so they get plain json and a null confidence instead.
        body.part("response_format", family.responseFormat());
        if (StringUtils.isNotBlank(languageHint)) {
            body.part(family.languageParameter(), languageHint.trim());
        }
        // T847 — context hints, each dropped silently when the family can't take it.
        if (hints.prompt() != null) {
            body.part("prompt", hints.prompt());
        }
        if (family.supportsKeywords() && !hints.keywords().isEmpty()) {
            hints.keywords().forEach(keyword -> body.part("keywords", keyword));
        }
        return body;
    }

    /**
     * T846 — a family that returns no segment logprobs yields {@code null} rather
     * than a fabricated score; T693 already reads {@code null} as "no confidence
     * signal", so the local→cloud fallback degrades instead of lying.
     */
    static Double confidenceFor(TurTranscriptionModelFamily family, Object segments) {
        return family.supportsSegmentConfidence() ? confidenceFromSegments(segments) : null;
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
