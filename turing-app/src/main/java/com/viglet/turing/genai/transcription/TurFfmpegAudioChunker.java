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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * T688 / §XLII.2 — the default {@link TurAudioChunker}: decode/re-encode/split
 * with {@code ffmpeg}. Each produced chunk is re-encoded to a compact 16 kHz
 * mono MP3 (the form Whisper likes), so the segment byte size is predictable and
 * always below the backend limit. Cut points snap to detected silence when
 * possible (fixed-duration + overlap fallback). Passthrough (no ffmpeg) when the
 * input already fits.
 *
 * <p>The segment <em>planning</em> ({@link #planSegments}) is a pure function,
 * unit-tested without ffmpeg; the ffmpeg invocation itself is exercised only by
 * a guarded integration test.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurFfmpegAudioChunker implements TurAudioChunker {

    /** 16 kHz mono MP3 at 32 kbps ≈ 4000 bytes/s — the re-encoded output rate. */
    static final long OUTPUT_BYTES_PER_SEC = 4000L;
    private static final String OUTPUT_BITRATE = "32k";
    private static final String OUTPUT_MIME = "audio/mpeg";
    /** Keep each re-encoded segment comfortably under the hard limit. */
    static final double SIZE_SAFETY = 0.85;
    /** Never plan a segment shorter than this (guards against pathological limits). */
    static final double MIN_SEGMENT_SECONDS = 5.0;
    /** silencedetect threshold + minimum silence duration. */
    private static final String SILENCE_FILTER = "silencedetect=noise=-30dB:d=0.5";

    private static final Pattern SILENCE_START = Pattern.compile("silence_start:\\s*([0-9.]+)");
    private static final Pattern SILENCE_END = Pattern.compile("silence_end:\\s*([0-9.]+)");

    private final TurTranscriptionProperty props;

    public TurFfmpegAudioChunker(TurConfigProperties configProperties) {
        this.props = configProperties.getTranscription() != null
                ? configProperties.getTranscription()
                : new TurTranscriptionProperty();
    }

    @Override
    public List<TurAudioChunk> chunk(byte[] audio, String mimeType, long maxBytes) {
        if (audio == null || audio.length == 0) {
            return List.of();
        }
        if (!isChunkingNeeded(audio, maxBytes)) {
            return List.of(passthrough(audio, mimeType));
        }
        try {
            return splitWithFfmpeg(audio, mimeType, maxBytes);
        } catch (IOException | RuntimeException e) {
            log.warn("[Transcription] audio chunking failed ({}); falling back to passthrough",
                    e.getMessage());
            return List.of(passthrough(audio, mimeType));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Transcription] audio chunking interrupted; falling back to passthrough");
            return List.of(passthrough(audio, mimeType));
        }
    }

    private TurAudioChunk passthrough(byte[] audio, String mimeType) {
        return new TurAudioChunk(audio, mimeType, 0, 0.0, -1.0, 0.0);
    }

    private List<TurAudioChunk> splitWithFfmpeg(byte[] audio, String mimeType, long maxBytes)
            throws IOException, InterruptedException {
        Path source = Files.createTempFile("tur-audio-src-", "." + extensionFor(mimeType));
        try {
            Files.write(source, audio);
            double duration = probeDuration(source);
            if (duration <= 0) {
                log.warn("[Transcription] could not probe audio duration; passthrough");
                return List.of(passthrough(audio, mimeType));
            }
            double overlap = Math.max(0.0, props.getChunkOverlapSeconds());
            List<Double> silences = detectSilenceMidpoints(source);
            List<Segment> plan = planSegments(duration, maxBytes, overlap, silences,
                    props.getMaxChunkSeconds());

            List<TurAudioChunk> chunks = new ArrayList<>(plan.size());
            for (int i = 0; i < plan.size(); i++) {
                Segment seg = plan.get(i);
                byte[] encoded = reencodeSegment(source, seg.start(), seg.end() - seg.start());
                chunks.add(new TurAudioChunk(encoded, OUTPUT_MIME, i,
                        seg.start(), seg.end(), seg.overlap()));
            }
            log.info("[Transcription] split {} bytes into {} chunks (duration {}s, limit {} bytes)",
                    audio.length, chunks.size(), Math.round(duration), maxBytes);
            return chunks;
        } finally {
            deleteQuietly(source);
        }
    }

    /**
     * Pure segment planner. Produces ordered {@link Segment}s whose re-encoded
     * output stays under {@code maxBytes}; interior cut points snap to the nearest
     * silence midpoint within an overlap-sized window (fixed-duration fallback
     * when no usable silence is found). Each segment after the first starts
     * {@code overlapSeconds} early.
     */
    static List<Segment> planSegments(double durationSec, long maxBytes, double overlapSec,
            List<Double> silenceMidpoints, double maxSegmentSeconds) {
        double targetSeg = Math.max(MIN_SEGMENT_SECONDS,
                Math.floor((maxBytes * SIZE_SAFETY) / OUTPUT_BYTES_PER_SEC));
        // T715 — a positive duration cap forces more (shorter) chunks so long
        // recordings both parallelise and report per-chunk progress.
        if (maxSegmentSeconds > 0) {
            targetSeg = Math.max(MIN_SEGMENT_SECONDS, Math.min(targetSeg, maxSegmentSeconds));
        }
        if (durationSec <= targetSeg) {
            return List.of(new Segment(0.0, durationSec, 0.0));
        }
        int count = (int) Math.ceil(durationSec / targetSeg);
        double window = Math.max(overlapSec, 1.0);

        // Boundaries: b[0]=0, interior cuts, b[count]=duration.
        double[] boundaries = new double[count + 1];
        boundaries[0] = 0.0;
        boundaries[count] = durationSec;
        for (int k = 1; k < count; k++) {
            double target = k * targetSeg;
            boundaries[k] = snapToSilence(target, window, silenceMidpoints, boundaries[k - 1]);
        }

        List<Segment> segments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double overlap = i == 0 ? 0.0 : Math.min(overlapSec, boundaries[i]);
            double start = boundaries[i] - overlap;
            segments.add(new Segment(start, boundaries[i + 1], overlap));
        }
        return segments;
    }

    /**
     * Snap {@code target} to the nearest silence midpoint within
     * {@code [target-window, target+window]} that still leaves a positive-length
     * segment after {@code prevBoundary}; falls back to {@code target}.
     */
    private static double snapToSilence(double target, double window, List<Double> silences,
            double prevBoundary) {
        if (silences == null || silences.isEmpty()) {
            return target;
        }
        double best = target;
        double bestDist = Double.MAX_VALUE;
        for (double s : silences) {
            if (s <= prevBoundary) {
                continue;
            }
            double dist = Math.abs(s - target);
            if (dist <= window && dist < bestDist) {
                best = s;
                bestDist = dist;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // ffmpeg / ffprobe invocations
    // ------------------------------------------------------------------

    private double probeDuration(Path source) throws IOException, InterruptedException {
        List<String> cmd = List.of(props.getFfprobePath(), "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", source.toString());
        String out = runProcess(cmd).trim();
        try {
            return out.isBlank() ? -1 : Double.parseDouble(out.lines().findFirst().orElse("-1").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<Double> detectSilenceMidpoints(Path source) {
        try {
            List<String> cmd = List.of(props.getFfmpegPath(), "-i", source.toString(),
                    "-af", SILENCE_FILTER, "-f", "null", "-");
            String out = runProcess(cmd);
            List<Double> midpoints = new ArrayList<>();
            Double pendingStart = null;
            for (String line : out.lines().toList()) {
                Matcher ms = SILENCE_START.matcher(line);
                Matcher me = SILENCE_END.matcher(line);
                if (ms.find()) {
                    pendingStart = safeParse(ms.group(1));
                } else if (me.find()) {
                    Double end = safeParse(me.group(1));
                    if (pendingStart != null && end != null) {
                        midpoints.add((pendingStart + end) / 2.0);
                    }
                    pendingStart = null;
                }
            }
            return midpoints;
        } catch (IOException | RuntimeException e) {
            log.debug("[Transcription] silence detection failed ({}); using fixed cuts", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private byte[] reencodeSegment(Path source, double start, double duration)
            throws IOException, InterruptedException {
        Path out = Files.createTempFile("tur-audio-seg-", ".mp3");
        try {
            List<String> cmd = List.of(props.getFfmpegPath(), "-y",
                    "-ss", formatSeconds(start), "-t", formatSeconds(duration),
                    "-i", source.toString(),
                    "-ac", "1", "-ar", "16000", "-c:a", "libmp3lame", "-b:a", OUTPUT_BITRATE,
                    "-f", "mp3", out.toString());
            runProcess(cmd);
            return Files.readAllBytes(out);
        } finally {
            deleteQuietly(out);
        }
    }

    /** Run a process, capture merged stdout+stderr, enforce the configured timeout. */
    private String runProcess(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // stream closed on process exit
            }
        });
        int timeout = props.getFfmpegTimeoutSeconds() > 0 ? props.getFfmpegTimeoutSeconds() : 120;
        boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            reader.join(2000);
            throw new IOException("ffmpeg timed out after " + timeout + "s");
        }
        reader.join(2000);
        if (p.exitValue() != 0) {
            throw new IOException("ffmpeg exited " + p.exitValue() + ": "
                    + StringUtils.abbreviate(out.toString(), 400));
        }
        return out.toString();
    }

    @Override
    public boolean isFfmpegAvailable() {
        return checkFfmpeg().available();
    }

    @Override
    public TurFfmpegStatus checkFfmpeg() {
        try {
            String out = runProcess(List.of(props.getFfmpegPath(), "-version"));
            String version = out.lines().findFirst().orElse("").trim();
            return version.isBlank()
                    ? new TurFfmpegStatus(false, null, "ffmpeg produced no version output")
                    : new TurFfmpegStatus(true, version, null);
        } catch (IOException e) {
            return new TurFfmpegStatus(false, null,
                    "ffmpeg not found or not runnable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TurFfmpegStatus(false, null, "ffmpeg probe interrupted");
        }
    }

    private static Double safeParse(String s) {
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatSeconds(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0.0, seconds));
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("[Transcription] could not delete temp file {}: {}", path, e.getMessage());
        }
    }

    private String extensionFor(String mimeType) {
        if (mimeType == null) {
            return "bin";
        }
        return switch (mimeType.toLowerCase(java.util.Locale.ROOT)) {
            case "audio/mpeg", "audio/mp3" -> "mp3";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a";
            case "audio/webm" -> "webm";
            case "audio/ogg", "application/ogg" -> "ogg";
            case "audio/flac" -> "flac";
            default -> "bin";
        };
    }

    /** A planned segment: source offsets plus the leading overlap it carries. */
    record Segment(double start, double end, double overlap) {
    }
}
