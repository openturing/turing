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

/**
 * T688 / §XLII.2 — one segment of a larger recording produced by
 * {@link TurAudioChunker}. Chunks are ordered by {@link #index()} and each
 * (after the first) begins {@link #overlapSeconds()} before the previous chunk
 * ended, so the T689 orchestrator can stitch transcripts and dedupe the overlap
 * window without dropping words at a cut point.
 *
 * <p>For a passthrough (audio already ≤ the limit) the chunker returns a single
 * chunk with {@code index=0}, {@code overlapSeconds=0}, and the original bytes.
 *
 * @param audio          the (possibly re-encoded) audio bytes for this segment
 * @param mimeType       the MIME type of {@link #audio()}
 * @param index          0-based position in the ordered chunk list
 * @param startSeconds   segment start offset in the source recording
 * @param endSeconds     segment end offset in the source recording
 * @param overlapSeconds seconds of leading overlap with the previous chunk (0 for the first)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAudioChunk(
        byte[] audio,
        String mimeType,
        int index,
        double startSeconds,
        double endSeconds,
        double overlapSeconds) {
}
