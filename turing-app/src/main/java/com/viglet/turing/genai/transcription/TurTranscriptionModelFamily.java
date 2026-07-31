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

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * T846 / §LXIII.1 — the wire dialect an OpenAI-compatible
 * {@code /audio/transcriptions} request must speak, resolved from the
 * <b>model id</b> rather than a hardcoded constant. The endpoint is shared, the
 * request shape is not:
 *
 * <ul>
 *   <li>{@link #WHISPER} — {@code whisper-1} and every self-hosted whisper server
 *       ({@code faster-whisper-server}, {@code whisper.cpp}, vLLM-whisper).
 *       Accepts {@code verbose_json}, which carries {@code segments[].avg_logprob}
 *       — the only family from which T693 can derive a confidence.</li>
 *   <li>{@link #GPT_4O_TRANSCRIBE} — the superseded {@code gpt-4o-transcribe} /
 *       {@code gpt-4o-mini-transcribe} ids. {@code json} only (no segments, so no
 *       confidence) but still the <em>singular</em> {@code language} parameter.</li>
 *   <li>{@link #GPT_TRANSCRIBE} — the current {@code gpt-transcribe} /
 *       {@code gpt-live-transcribe} ids. {@code json} only, the <em>plural</em>
 *       {@code languages} parameter, and the only family that accepts
 *       {@code keywords} (T847).</li>
 * </ul>
 *
 * <p>Unknown ids fall back to {@link #WHISPER}: it is the most permissive dialect
 * (a backend that ignores {@code verbose_json} simply returns no segments → a
 * {@code null} confidence) and it is what every self-hosted OpenAI-compatible
 * server actually serves, so a custom model name can never be made worse by the
 * dialect choice.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurTranscriptionModelFamily {

    /** {@code whisper-1} and self-hosted whisper servers. */
    WHISPER("verbose_json", "language", true, false),

    /** Superseded {@code gpt-4o[-mini]-transcribe} — {@code json}, singular {@code language}. */
    GPT_4O_TRANSCRIBE("json", "language", false, false),

    /** Current {@code gpt-transcribe} / {@code gpt-live-transcribe} — {@code json} + {@code languages}. */
    GPT_TRANSCRIBE("json", "languages", false, true);

    private final String responseFormat;
    private final String languageParameter;
    private final boolean segmentConfidence;
    private final boolean keywords;

    TurTranscriptionModelFamily(String responseFormat, String languageParameter,
            boolean segmentConfidence, boolean keywords) {
        this.responseFormat = responseFormat;
        this.languageParameter = languageParameter;
        this.segmentConfidence = segmentConfidence;
        this.keywords = keywords;
    }

    /** The {@code response_format} value this family accepts. */
    public String responseFormat() {
        return responseFormat;
    }

    /** The multipart field name carrying the language hint ({@code language} vs {@code languages}). */
    public String languageParameter() {
        return languageParameter;
    }

    /**
     * True when the family returns {@code segments[].avg_logprob}, i.e. when a
     * 0–1 confidence can honestly be derived (T693). When false the result
     * carries {@code confidence = null}, which the confidence fallback already
     * reads as "no signal" — it degrades instead of inventing a score.
     */
    public boolean supportsSegmentConfidence() {
        return segmentConfidence;
    }

    /** True when the family accepts the T847 {@code keywords} context hint. */
    public boolean supportsKeywords() {
        return keywords;
    }

    /**
     * T849 / §LXIII.4 — true when the family accepts {@code stream=true} on
     * {@code /audio/transcriptions} and emits transcript events while it works.
     *
     * <p>Derived rather than declared: the split is exactly whisper vs. the
     * {@code gpt-*transcribe} models, and a self-hosted whisper server that
     * ignored the flag would return one blocking JSON body anyway. Streaming is
     * always attempted optimistically and falls back to the blocking call, so a
     * wrong answer here costs a retry, never a transcript.
     */
    public boolean supportsStreaming() {
        return this != WHISPER;
    }

    /**
     * T851 / §LXIII.6 — true when the family accepts
     * {@code response_format=diarized_json} and returns per-speaker segments.
     *
     * <p>Same split as streaming, and for the same reason: whisper's
     * {@code verbose_json} segments are timing boundaries with no speaker
     * attribution at all, so asking it to diarize would either 400 or return
     * segments that silently mean something else.
     */
    public boolean supportsDiarization() {
        return this != WHISPER;
    }

    /**
     * Resolve the dialect from a model id. Blank/unknown → {@link #WHISPER}.
     *
     * <p>Matching is on the id shape rather than an allow-list so dated snapshots
     * ({@code gpt-transcribe-2026-07-01}) and vendor-prefixed ids
     * ({@code openai/whisper-large-v3}) resolve to the right dialect.
     */
    public static TurTranscriptionModelFamily of(String model) {
        if (StringUtils.isBlank(model)) {
            return WHISPER;
        }
        String id = model.trim().toLowerCase(Locale.ROOT);
        if (id.contains("whisper")) {
            return WHISPER;
        }
        if (id.contains("gpt-4o") && id.contains("transcribe")) {
            return GPT_4O_TRANSCRIBE;
        }
        if (id.contains("transcribe")) {
            return GPT_TRANSCRIBE;
        }
        return WHISPER;
    }
}
