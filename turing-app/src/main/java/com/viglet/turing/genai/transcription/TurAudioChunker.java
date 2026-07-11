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

import java.util.List;

/**
 * T688 / §XLII.2 — provider-agnostic audio chunking seam. Splits a recording
 * that exceeds a backend's per-request limit into decodable segments below that
 * limit, so every {@link TurTranscriptionProvider} inherits large-file support
 * through the T689 orchestrator rather than each backend re-solving it.
 *
 * <p>Byte-slicing a compressed container (mp3/m4a) corrupts it, so a real
 * decode/re-encode is required — the default impl shells out to {@code ffmpeg}.
 * When the audio is already ≤ the limit the chunker is a <b>passthrough</b>
 * (returns the original bytes as a single chunk) and never invokes ffmpeg, so
 * small-clip installs need no ffmpeg at all.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurAudioChunker {

    /** True when {@code audio} exceeds {@code maxBytes} and therefore needs splitting. */
    default boolean isChunkingNeeded(byte[] audio, long maxBytes) {
        return audio != null && maxBytes > 0 && audio.length > maxBytes;
    }

    /**
     * Split {@code audio} into ordered segments each ≤ {@code maxBytes}. When the
     * input already fits, returns a single passthrough chunk with the original
     * bytes (no ffmpeg call). Never throws for a transcodable input; on an
     * unrecoverable ffmpeg failure it falls back to the passthrough single chunk
     * so the caller can still attempt a direct transcription.
     *
     * @param audio    the raw source audio
     * @param mimeType the source MIME type (may be null)
     * @param maxBytes the active backend's per-request upload limit
     * @return one or more ordered chunks (never empty for non-empty input)
     */
    List<TurAudioChunk> chunk(byte[] audio, String mimeType, long maxBytes);

    /** True when the ffmpeg toolchain is present and runnable. */
    boolean isFfmpegAvailable();

    /**
     * Probe the ffmpeg toolchain, mirroring the T80 Docker probe. Never throws —
     * a missing/broken binary comes back as {@code available=false} with an
     * {@code error}.
     */
    TurFfmpegStatus checkFfmpeg();

    /**
     * Result of {@link #checkFfmpeg()}. {@code available} true means ffmpeg
     * answered and {@code version} is populated; otherwise {@code error} explains
     * why chunking large audio is unavailable.
     */
    record TurFfmpegStatus(boolean available, String version, String error) {
    }
}
