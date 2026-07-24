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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * T689 / §XLII.3 — chunk-aware transcription orchestration. Sits <b>above</b>
 * {@link TurTranscriptionProvider}: it chunks audio over the active backend's
 * per-request limit ({@link TurAudioChunker}), transcribes the chunks
 * bounded-parallel and in order through the config-selected provider
 * ({@link TurTranscriptionProviderFactory}), then stitches the transcripts
 * (deduping the overlap window) and aggregates the detected language. Because it
 * wraps the seam rather than a specific impl, <b>every</b> backend — OpenAI,
 * local server, in-process ONNX — inherits large-file support for free.
 *
 * <p>This is the entry point callers use instead of a raw provider. When the
 * audio already fits (single passthrough chunk) it is a thin delegate to the
 * provider, byte-identical to the pre-chunking path. When chunking is impossible
 * (no ffmpeg) the chunker falls open to a single full-size chunk and the
 * provider's own limit guard produces the clear "too large" failure — the
 * chunk-or-fail decision.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurTranscriptionService {

    /** Cap on how many trailing/leading words to test when deduping an overlap. */
    static final int MAX_OVERLAP_WORDS = 40;

    /** Cloud OpenAI's fixed 25 MiB per-request cap — the fallback chunks below this. */
    static final long OPENAI_MAX_UPLOAD_BYTES = 26_214_400L;

    private final TurTranscriptionProviderFactory providerFactory;
    private final TurAudioChunker audioChunker;
    private final TurTranscriptionConfigResolver configResolver;
    private final TurTranscriptionMetricsService metrics;
    private final TurTranscriptionProperty props;

    public TurTranscriptionService(TurTranscriptionProviderFactory providerFactory,
            TurAudioChunker audioChunker,
            TurTranscriptionConfigResolver configResolver,
            TurTranscriptionMetricsService metrics,
            TurConfigProperties configProperties) {
        this.providerFactory = providerFactory;
        this.audioChunker = audioChunker;
        this.configResolver = configResolver;
        this.metrics = metrics;
        this.props = configProperties.getTranscription() != null
                ? configProperties.getTranscription()
                : new TurTranscriptionProperty();
    }

    /** True when a transcription backend is configured and usable. */
    public boolean isAvailable() {
        return providerFactory.resolveActive().isAvailable();
    }

    /**
     * Transcribe audio of any size, chunking transparently when it exceeds the
     * active backend's per-request limit. Fail-soft — never throws.
     *
     * <p>T693 — records a per-backend metric for every attempt and, when the
     * confidence fallback is enabled, escalates a failed/low-confidence result
     * from the active (typically local) backend to the configured cloud backend.
     */
    public TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint) {
        return transcribe(audio, mimeType, languageHint, TurTranscriptionProgressListener.NOOP);
    }

    /**
     * T715 — chunk-aware transcription that reports per-chunk progress to
     * {@code listener} (used by the async persona-from-audio job for a live
     * progress bar). Behaviour is otherwise identical to
     * {@link #transcribe(byte[], String, String)}; a {@code null} listener is
     * treated as {@link TurTranscriptionProgressListener#NOOP}.
     */
    public TurTranscriptionResult transcribe(byte[] audio, String mimeType, String languageHint,
            TurTranscriptionProgressListener listener) {
        TurTranscriptionProgressListener progress =
                listener == null ? TurTranscriptionProgressListener.NOOP : listener;
        if (audio == null || audio.length == 0) {
            return TurTranscriptionResult.fail("Empty audio.");
        }
        TurTranscriptionConfig config = configResolver.resolve();
        TurTranscriptionProviderType activeType = config.type();
        TurTranscriptionProvider provider = providerFactory.resolveActive();

        TurTranscriptionResult primary = timedTranscribe(
                activeType, provider, config.maxUploadBytes(), audio, mimeType, languageHint, progress);

        if (!shouldFallback(primary, activeType)) {
            return primary;
        }
        TurTranscriptionProviderType fbType = props.getConfidenceFallbackType();
        TurTranscriptionProvider fallback = providerFactory.resolve(fbType);
        if (fallback == null || !fallback.isAvailable() || fbType == activeType) {
            return primary;
        }
        log.info("[Transcription] {} result {} — escalating to fallback {}", activeType,
                primary.success() ? "confidence " + primary.confidence() : "failed", fbType);
        TurTranscriptionResult fbResult = timedTranscribe(fbType, fallback,
                fallbackMaxBytes(fbType, config.maxUploadBytes()), audio, mimeType, languageHint, progress);
        return fbResult.success() ? fbResult : primary;
    }

    /** True when the confidence fallback is enabled and this result warrants escalation. */
    private boolean shouldFallback(TurTranscriptionResult result, TurTranscriptionProviderType activeType) {
        if (!props.isConfidenceFallbackEnabled()) {
            return false;
        }
        if (props.getConfidenceFallbackType() == null
                || props.getConfidenceFallbackType() == activeType) {
            return false;
        }
        if (!result.success()) {
            return true;
        }
        Double confidence = result.confidence();
        return confidence != null && confidence < Math.clamp(props.getConfidenceThreshold(), 0.0, 1.0);
    }

    /** Cloud OpenAI needs its 25 MiB cap; a local fallback keeps the active limit. */
    private long fallbackMaxBytes(TurTranscriptionProviderType fbType, long activeMax) {
        if (fbType == TurTranscriptionProviderType.OPENAI) {
            return activeMax > 0 ? Math.min(activeMax, OPENAI_MAX_UPLOAD_BYTES) : OPENAI_MAX_UPLOAD_BYTES;
        }
        return activeMax;
    }

    /** Transcribe (chunk + stitch) on one provider, recording a per-backend metric. */
    private TurTranscriptionResult timedTranscribe(TurTranscriptionProviderType backend,
            TurTranscriptionProvider provider, long maxBytes,
            byte[] audio, String mimeType, String languageHint,
            TurTranscriptionProgressListener progress) {
        long start = System.currentTimeMillis();
        TurTranscriptionResult result =
                transcribeWith(provider, maxBytes, audio, mimeType, languageHint, progress);
        metrics.record(backend, System.currentTimeMillis() - start, result.success(), audio.length);
        return result;
    }

    /** The chunk-or-passthrough + stitch pipeline for a single provider. */
    private TurTranscriptionResult transcribeWith(TurTranscriptionProvider provider, long maxBytes,
            byte[] audio, String mimeType, String languageHint,
            TurTranscriptionProgressListener progress) {
        List<TurAudioChunk> chunks = audioChunker.chunk(audio, mimeType, maxBytes);
        if (chunks.isEmpty()) {
            return TurTranscriptionResult.fail("Empty audio.");
        }
        if (chunks.size() == 1) {
            progress.onProgress(0, 1);
            TurAudioChunk only = chunks.get(0);
            TurTranscriptionResult single = provider.transcribe(only.audio(), only.mimeType(), languageHint);
            progress.onProgress(1, 1);
            return single;
        }

        List<TurTranscriptionResult> results = transcribeChunks(provider, chunks, languageHint, progress);
        for (int i = 0; i < results.size(); i++) {
            TurTranscriptionResult r = results.get(i);
            if (r == null || !r.success() || StringUtils.isBlank(r.text())) {
                return TurTranscriptionResult.fail("Chunk " + (i + 1) + "/" + results.size()
                        + " failed: " + (r == null ? "no result" : Objects.toString(r.error(),
                                "empty transcript")));
            }
        }

        String stitched = stitchTranscripts(results.stream().map(TurTranscriptionResult::text).toList());
        String language = aggregateLanguage(
                results.stream().map(TurTranscriptionResult::language).toList(), languageHint);
        Double confidence = aggregateConfidence(results);
        log.info("[Transcription] stitched {} chunk transcripts into {} chars (lang {})",
                results.size(), stitched.length(), language);
        return TurTranscriptionResult.ok(stitched, language, confidence);
    }

    /** Mean of the non-null per-chunk confidences, or {@code null} when none reported one. */
    static Double aggregateConfidence(List<TurTranscriptionResult> results) {
        double sum = 0;
        int n = 0;
        for (TurTranscriptionResult r : results) {
            if (r != null && r.confidence() != null) {
                sum += r.confidence();
                n++;
            }
        }
        return n == 0 ? null : sum / n;
    }

    /** Transcribe chunks bounded-parallel, preserving order, reporting progress. */
    private List<TurTranscriptionResult> transcribeChunks(TurTranscriptionProvider provider,
            List<TurAudioChunk> chunks, String languageHint,
            TurTranscriptionProgressListener progress) {
        int total = chunks.size();
        progress.onProgress(0, total);
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        int concurrency = Math.clamp(props.getChunkConcurrency(), 1, total);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<TurTranscriptionResult>> futures = new ArrayList<>(chunks.size());
            for (TurAudioChunk chunk : chunks) {
                Callable<TurTranscriptionResult> task = () -> {
                    try {
                        return provider.transcribe(chunk.audio(), chunk.mimeType(), languageHint);
                    } finally {
                        progress.onProgress(done.incrementAndGet(), total);
                    }
                };
                futures.add(pool.submit(task));
            }
            List<TurTranscriptionResult> results = new ArrayList<>(chunks.size());
            for (Future<TurTranscriptionResult> f : futures) {
                try {
                    results.add(f.get());
                } catch (ExecutionException e) {
                    results.add(TurTranscriptionResult.fail(
                            "transcription error: " + e.getCause().getMessage()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(TurTranscriptionResult.fail("transcription interrupted"));
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Stitch ordered chunk transcripts into one, deduping the repeated overlap
     * window: for each join, drop the longest leading word-run of the next chunk
     * that already appears as the trailing word-run of the accumulated text
     * (case/punctuation-insensitive comparison, original text preserved).
     *
     * <p>Pure function — the core testable logic of the orchestrator.
     */
    static String stitchTranscripts(List<String> transcripts) {
        List<String> nonBlank = transcripts.stream().filter(StringUtils::isNotBlank).toList();
        if (nonBlank.isEmpty()) {
            return "";
        }
        List<String> acc = new ArrayList<>(splitWords(nonBlank.get(0)));
        for (int i = 1; i < nonBlank.size(); i++) {
            List<String> next = splitWords(nonBlank.get(i));
            int overlap = longestOverlap(acc, next);
            acc.addAll(next.subList(overlap, next.size()));
        }
        return String.join(" ", acc);
    }

    /**
     * The largest {@code k} (≤ {@link #MAX_OVERLAP_WORDS}) such that the last
     * {@code k} words of {@code prev} equal (normalized) the first {@code k}
     * words of {@code next}.
     */
    private static int longestOverlap(List<String> prev, List<String> next) {
        int max = Math.min(Math.min(prev.size(), next.size()), MAX_OVERLAP_WORDS);
        for (int k = max; k > 0; k--) {
            boolean match = true;
            for (int j = 0; j < k; j++) {
                if (!normalizeWord(prev.get(prev.size() - k + j)).equals(normalizeWord(next.get(j)))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return k;
            }
        }
        return 0;
    }

    /** Most frequent non-blank detected language; ties resolved by first-seen; else the hint. */
    static String aggregateLanguage(List<String> languages, String fallback) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String lang : languages) {
            if (StringUtils.isNotBlank(lang)) {
                counts.merge(lang.trim(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(StringUtils.trimToNull(fallback));
    }

    private static List<String> splitWords(String text) {
        List<String> words = new ArrayList<>();
        for (String w : text.trim().split("\\s+")) {
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        return words;
    }

    private static String normalizeWord(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
