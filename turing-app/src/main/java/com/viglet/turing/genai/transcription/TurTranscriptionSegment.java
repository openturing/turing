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

/**
 * T851 / §LXIII.6 — one attributable turn of a diarized transcript: who spoke,
 * what they said, and when.
 *
 * <p><b>The shape decision this block was blocked on.</b> A
 * {@link TurTranscriptionResult} stays a flat {@code text} plus an <em>optional,
 * additive</em> list of these. Segments never replace the text — they annotate
 * it. That keeps every existing caller (persona-from-audio, AUDIO slots, the
 * confidence fallback) reading exactly the field it reads today, and makes
 * "diarization was not available" indistinguishable from "this backend never had
 * it": both are simply an empty list.
 *
 * <p>{@link #speaker()} is the label the vendor assigned within <b>one request</b>
 * ({@code A}, {@code B}, …). It is deliberately not normalised to a person: the
 * API offers no voice identity across requests, so a label means "a distinct
 * voice in this recording" and nothing more.
 *
 * @param speaker      vendor speaker label for this turn, or {@code null} when the
 *                     backend segmented without attributing
 * @param text         what was said in this turn (never blank — blank turns are dropped)
 * @param startSeconds offset from the start of the audio, or {@code null} if absent
 * @param endSeconds   end offset from the start of the audio, or {@code null} if absent
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurTranscriptionSegment(
        String speaker,
        String text,
        Double startSeconds,
        Double endSeconds) {

    public TurTranscriptionSegment {
        speaker = StringUtils.trimToNull(speaker);
        text = StringUtils.trimToEmpty(text);
    }

    /** True when this turn carries actual words — the only ones worth keeping. */
    public boolean hasText() {
        return !text.isEmpty();
    }
}
