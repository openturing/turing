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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.transcription.TurFfmpegAudioChunker.Segment;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

/**
 * T688 / §XLII.2 — pure-logic tests for {@link TurFfmpegAudioChunker}: the
 * passthrough path, the probe negative on a bogus binary, and the deterministic
 * {@link TurFfmpegAudioChunker#planSegments} planner (segment count under the
 * byte limit, overlap wiring, silence snapping). The ffmpeg subprocess itself is
 * out of scope here (integration-only).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurFfmpegAudioChunkerTest {

    private TurFfmpegAudioChunker chunker(TurTranscriptionProperty props) {
        TurConfigProperties config = new TurConfigProperties();
        config.setTranscription(props);
        return new TurFfmpegAudioChunker(config);
    }

    @Test
    void passthroughWhenUnderLimitNeverTouchesFfmpeg() {
        // ffmpeg path is bogus — if the chunker invoked it, this would fail.
        TurTranscriptionProperty props = new TurTranscriptionProperty();
        props.setFfmpegPath("/nonexistent/ffmpeg");
        byte[] audio = new byte[1024];

        List<TurAudioChunk> chunks = chunker(props).chunk(audio, "audio/mpeg", 26_214_400L);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).audio()).isSameAs(audio);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).overlapSeconds()).isZero();
    }

    @Test
    void emptyInputYieldsNoChunks() {
        assertThat(chunker(new TurTranscriptionProperty()).chunk(new byte[0], "audio/mpeg", 100L))
                .isEmpty();
        assertThat(chunker(new TurTranscriptionProperty()).chunk(null, "audio/mpeg", 100L))
                .isEmpty();
    }

    @Test
    void chunkingNeededOnlyWhenOverLimit() {
        TurFfmpegAudioChunker c = chunker(new TurTranscriptionProperty());
        assertThat(c.isChunkingNeeded(new byte[100], 100L)).isFalse();
        assertThat(c.isChunkingNeeded(new byte[101], 100L)).isTrue();
        assertThat(c.isChunkingNeeded(new byte[101], 0L)).isFalse();
    }

    @Test
    void probeNegativeOnBogusBinary() {
        TurTranscriptionProperty props = new TurTranscriptionProperty();
        props.setFfmpegPath("/nonexistent/ffmpeg");
        props.setFfmpegTimeoutSeconds(5);

        TurAudioChunker.TurFfmpegStatus status = chunker(props).checkFfmpeg();

        assertThat(status.available()).isFalse();
        assertThat(status.error()).isNotBlank();
    }

    @Test
    void planSingleSegmentWhenDurationFits() {
        // 10 MiB limit → target ~ (10485760*0.85)/4000 ≈ 2228s; a 60s clip fits.
        List<Segment> plan = TurFfmpegAudioChunker.planSegments(60.0, 10_485_760L, 2.0, List.of(), 0);

        assertThat(plan).hasSize(1);
        assertThat(plan.get(0).start()).isZero();
        assertThat(plan.get(0).end()).isEqualTo(60.0);
        assertThat(plan.get(0).overlap()).isZero();
    }

    @Test
    void planMultipleSegmentsEachUnderByteLimit() {
        long maxBytes = 4_000_000L; // ~850s target segment
        double duration = 3600.0;   // 1 hour → several segments
        List<Segment> plan = TurFfmpegAudioChunker.planSegments(duration, maxBytes, 3.0, List.of(), 0);

        assertThat(plan).hasSizeGreaterThan(1);
        double targetSeg = Math.floor((maxBytes * TurFfmpegAudioChunker.SIZE_SAFETY)
                / TurFfmpegAudioChunker.OUTPUT_BYTES_PER_SEC);
        // First chunk has no overlap and starts at 0.
        assertThat(plan.get(0).start()).isZero();
        assertThat(plan.get(0).overlap()).isZero();
        // Every chunk's encoded length (duration * output rate) stays under the limit.
        for (Segment seg : plan) {
            double lengthSec = seg.end() - seg.start();
            assertThat(lengthSec).isLessThanOrEqualTo(targetSeg + 3.0 + 0.001);
            assertThat(lengthSec * TurFfmpegAudioChunker.OUTPUT_BYTES_PER_SEC)
                    .isLessThanOrEqualTo(maxBytes);
        }
        // Segments are contiguous at the cut points (end[i] == start[i+1] + overlap).
        for (int i = 1; i < plan.size(); i++) {
            assertThat(plan.get(i).overlap()).isEqualTo(3.0);
            assertThat(plan.get(i).start()).isEqualTo(plan.get(i - 1).end() - 3.0, org.assertj.core.data.Offset.offset(0.001));
        }
        assertThat(plan.get(plan.size() - 1).end()).isEqualTo(duration);
    }

    @Test
    void planSnapsInteriorCutToNearbySilence() {
        long maxBytes = 4_000_000L;
        double targetSeg = Math.floor((maxBytes * TurFfmpegAudioChunker.SIZE_SAFETY)
                / TurFfmpegAudioChunker.OUTPUT_BYTES_PER_SEC); // 850
        double duration = targetSeg * 2 + 100; // forces a single interior cut near targetSeg
        // A silence 5s after the target cut, within the 10s window.
        double silence = targetSeg + 5;
        List<Segment> plan = TurFfmpegAudioChunker.planSegments(duration, maxBytes, 10.0,
                List.of(silence), 0);

        // The interior boundary (end of chunk 0) snaps to the silence midpoint.
        assertThat(plan.get(0).end()).isEqualTo(silence);
    }

    @Test
    void capsSegmentDurationWhenMaxChunkSecondsSet() {
        // T715 — a 600s clip fits one byte-limited chunk, but a 120s duration cap
        // forces several shorter chunks (so a long recording parallelises + shows
        // per-chunk progress). Legacy (cap 0) keeps it a single chunk.
        long maxBytes = 10_485_760L; // byte target ≈ 2228s
        assertThat(TurFfmpegAudioChunker.planSegments(600.0, maxBytes, 2.0, List.of(), 0))
                .hasSize(1);

        List<Segment> capped =
                TurFfmpegAudioChunker.planSegments(600.0, maxBytes, 2.0, List.of(), 120.0);
        assertThat(capped).hasSizeGreaterThan(1);
        for (Segment seg : capped) {
            assertThat(seg.end() - seg.start()).isLessThanOrEqualTo(120.0 + 2.0 + 0.001);
        }
        assertThat(capped.get(capped.size() - 1).end()).isEqualTo(600.0);
    }
}
