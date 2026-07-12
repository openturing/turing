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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

/**
 * T689 / §XLII.3 — {@link TurTranscriptionService}: pure stitch/dedupe +
 * language aggregation, single-chunk passthrough delegate, multi-chunk
 * bounded-parallel ordered orchestration, and chunk-or-fail.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionServiceTest {

    private TurTranscriptionService service(TurTranscriptionProviderFactory factory,
            TurAudioChunker chunker, TurTranscriptionConfigResolver resolver) {
        return new TurTranscriptionService(factory, chunker, resolver,
                new TurTranscriptionMetricsService(), new TurConfigProperties());
    }

    private TurTranscriptionService service(TurTranscriptionProviderFactory factory,
            TurAudioChunker chunker, TurTranscriptionConfigResolver resolver,
            TurTranscriptionMetricsService metrics, TurConfigProperties config) {
        return new TurTranscriptionService(factory, chunker, resolver, metrics, config);
    }

    private TurTranscriptionConfigResolver resolverWithLimit(long maxBytes) {
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);
        when(resolver.resolve()).thenReturn(new TurTranscriptionConfig(
                TurTranscriptionProviderType.OPENAI, "", "", "", maxBytes));
        return resolver;
    }

    // ---- pure stitching -------------------------------------------------

    @Test
    void stitchDropsRepeatedOverlapWindow() {
        String a = "the quick brown fox jumps over";
        String b = "jumps over the lazy dog";
        String stitched = TurTranscriptionService.stitchTranscripts(List.of(a, b));
        assertThat(stitched).isEqualTo("the quick brown fox jumps over the lazy dog");
    }

    @Test
    void stitchIsCaseAndPunctuationInsensitiveOnOverlap() {
        String a = "hello world, this is";
        String b = "This is a test.";
        String stitched = TurTranscriptionService.stitchTranscripts(List.of(a, b));
        assertThat(stitched).isEqualTo("hello world, this is a test.");
    }

    @Test
    void stitchWithNoOverlapConcatenates() {
        String stitched = TurTranscriptionService.stitchTranscripts(List.of("alpha beta", "gamma delta"));
        assertThat(stitched).isEqualTo("alpha beta gamma delta");
    }

    @Test
    void stitchSkipsBlankChunks() {
        String stitched = TurTranscriptionService.stitchTranscripts(List.of("one two", "  ", "three"));
        assertThat(stitched).isEqualTo("one two three");
    }

    // ---- language aggregation ------------------------------------------

    @Test
    void aggregateLanguagePicksMajority() {
        assertThat(TurTranscriptionService.aggregateLanguage(List.of("pt", "pt", "en"), "es"))
                .isEqualTo("pt");
    }

    @Test
    void aggregateLanguageFallsBackWhenAllBlank() {
        assertThat(TurTranscriptionService.aggregateLanguage(List.of("", "  "), "en"))
                .isEqualTo("en");
    }

    // ---- orchestration --------------------------------------------------

    @Test
    void singleChunkDelegatesToProvider() {
        TurTranscriptionProvider provider = mock(TurTranscriptionProvider.class);
        when(provider.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("hello", "en"));
        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(provider);

        TurAudioChunker chunker = mock(TurAudioChunker.class);
        byte[] audio = new byte[10];
        when(chunker.chunk(any(), any(), anyLong()))
                .thenReturn(List.of(new TurAudioChunk(audio, "audio/mpeg", 0, 0, -1, 0)));

        TurTranscriptionResult result = service(factory, chunker, resolverWithLimit(100L))
                .transcribe(audio, "audio/mpeg", "en");

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("hello");
    }

    @Test
    void multiChunkStitchesInOrder() {
        TurTranscriptionProvider provider = mock(TurTranscriptionProvider.class);
        // Chunk bytes distinguish the two segments; return ordered transcripts.
        byte[] c0 = new byte[] { 0 };
        byte[] c1 = new byte[] { 1 };
        when(provider.transcribe(any(), any(), any())).thenAnswer(inv -> {
            byte[] b = inv.getArgument(0);
            return b.length > 0 && b[0] == 0
                    ? TurTranscriptionResult.ok("the quick brown fox", "en")
                    : TurTranscriptionResult.ok("brown fox jumps high", "en");
        });
        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(provider);

        TurAudioChunker chunker = mock(TurAudioChunker.class);
        when(chunker.chunk(any(), any(), anyLong())).thenReturn(List.of(
                new TurAudioChunk(c0, "audio/mpeg", 0, 0, 10, 0),
                new TurAudioChunk(c1, "audio/mpeg", 1, 8, 20, 2)));

        TurTranscriptionResult result = service(factory, chunker, resolverWithLimit(1L))
                .transcribe(new byte[5], "audio/mpeg", "en");

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("the quick brown fox jumps high");
        assertThat(result.language()).isEqualTo("en");
    }

    @Test
    void failsWhenAnyChunkFails() {
        TurTranscriptionProvider provider = mock(TurTranscriptionProvider.class);
        when(provider.transcribe(any(), any(), any())).thenAnswer(inv -> {
            byte[] b = inv.getArgument(0);
            return b[0] == 0
                    ? TurTranscriptionResult.ok("first", "en")
                    : TurTranscriptionResult.fail("backend 500");
        });
        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(provider);

        TurAudioChunker chunker = mock(TurAudioChunker.class);
        when(chunker.chunk(any(), any(), anyLong())).thenReturn(List.of(
                new TurAudioChunk(new byte[] { 0 }, "audio/mpeg", 0, 0, 10, 0),
                new TurAudioChunk(new byte[] { 1 }, "audio/mpeg", 1, 8, 20, 2)));

        TurTranscriptionResult result = service(factory, chunker, resolverWithLimit(1L))
                .transcribe(new byte[5], "audio/mpeg", "en");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("backend 500");
    }

    // ---- confidence aggregation ----------------------------------------

    @Test
    void aggregateConfidenceMeansNonNullValues() {
        assertThat(TurTranscriptionService.aggregateConfidence(List.of(
                TurTranscriptionResult.ok("a", "en", 0.4),
                TurTranscriptionResult.ok("b", "en", 0.6),
                TurTranscriptionResult.ok("c", "en", null))))
                .isEqualTo(0.5);
    }

    @Test
    void aggregateConfidenceNullWhenNoneReported() {
        assertThat(TurTranscriptionService.aggregateConfidence(List.of(
                TurTranscriptionResult.ok("a", "en"),
                TurTranscriptionResult.ok("b", "en"))))
                .isNull();
    }

    // ---- confidence fallback (T693) ------------------------------------

    private TurTranscriptionConfigResolver resolver(TurTranscriptionProviderType type, long maxBytes) {
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);
        when(resolver.resolve()).thenReturn(
                new TurTranscriptionConfig(type, "", "", "", maxBytes));
        return resolver;
    }

    private TurAudioChunker singleChunk() {
        TurAudioChunker chunker = mock(TurAudioChunker.class);
        when(chunker.chunk(any(), any(), anyLong()))
                .thenReturn(List.of(new TurAudioChunk(new byte[10], "audio/mpeg", 0, 0, -1, 0)));
        return chunker;
    }

    private TurConfigProperties fallbackConfig(boolean enabled, double threshold) {
        TurTranscriptionProperty t = new TurTranscriptionProperty();
        t.setConfidenceFallbackEnabled(enabled);
        t.setConfidenceFallbackType(TurTranscriptionProviderType.OPENAI);
        t.setConfidenceThreshold(threshold);
        TurConfigProperties cfg = new TurConfigProperties();
        cfg.setTranscription(t);
        return cfg;
    }

    @Test
    void confidenceFallbackEscalatesOnPrimaryFailure() {
        TurTranscriptionProvider local = mock(TurTranscriptionProvider.class);
        when(local.transcribe(any(), any(), any())).thenReturn(TurTranscriptionResult.fail("local down"));
        TurTranscriptionProvider cloud = mock(TurTranscriptionProvider.class);
        when(cloud.isAvailable()).thenReturn(true);
        when(cloud.transcribe(any(), any(), any())).thenReturn(TurTranscriptionResult.ok("cloud text", "en"));

        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(local);
        when(factory.resolve(TurTranscriptionProviderType.OPENAI)).thenReturn(cloud);

        TurTranscriptionResult result = service(factory, singleChunk(),
                resolver(TurTranscriptionProviderType.OPENAI_COMPATIBLE, 100L),
                new TurTranscriptionMetricsService(), fallbackConfig(true, 0.5))
                .transcribe(new byte[10], "audio/mpeg", "en");

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("cloud text");
    }

    @Test
    void confidenceFallbackEscalatesOnLowConfidence() {
        TurTranscriptionProvider local = mock(TurTranscriptionProvider.class);
        when(local.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("weak", "en", 0.2));
        TurTranscriptionProvider cloud = mock(TurTranscriptionProvider.class);
        when(cloud.isAvailable()).thenReturn(true);
        when(cloud.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("strong", "en", 0.9));

        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(local);
        when(factory.resolve(TurTranscriptionProviderType.OPENAI)).thenReturn(cloud);

        TurTranscriptionResult result = service(factory, singleChunk(),
                resolver(TurTranscriptionProviderType.OPENAI_COMPATIBLE, 100L),
                new TurTranscriptionMetricsService(), fallbackConfig(true, 0.5))
                .transcribe(new byte[10], "audio/mpeg", "en");

        assertThat(result.text()).isEqualTo("strong");
    }

    @Test
    void confidenceFallbackKeepsPrimaryWhenDisabled() {
        TurTranscriptionProvider local = mock(TurTranscriptionProvider.class);
        when(local.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("weak", "en", 0.2));
        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(local);

        TurTranscriptionResult result = service(factory, singleChunk(),
                resolver(TurTranscriptionProviderType.OPENAI_COMPATIBLE, 100L),
                new TurTranscriptionMetricsService(), fallbackConfig(false, 0.5))
                .transcribe(new byte[10], "audio/mpeg", "en");

        assertThat(result.text()).isEqualTo("weak");
    }

    @Test
    void confidenceFallbackKeepsPrimaryWhenFallbackAlsoFails() {
        TurTranscriptionProvider local = mock(TurTranscriptionProvider.class);
        when(local.transcribe(any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("weak", "en", 0.2));
        TurTranscriptionProvider cloud = mock(TurTranscriptionProvider.class);
        when(cloud.isAvailable()).thenReturn(true);
        when(cloud.transcribe(any(), any(), any())).thenReturn(TurTranscriptionResult.fail("cloud down"));

        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(local);
        when(factory.resolve(TurTranscriptionProviderType.OPENAI)).thenReturn(cloud);

        TurTranscriptionResult result = service(factory, singleChunk(),
                resolver(TurTranscriptionProviderType.OPENAI_COMPATIBLE, 100L),
                new TurTranscriptionMetricsService(), fallbackConfig(true, 0.5))
                .transcribe(new byte[10], "audio/mpeg", "en");

        assertThat(result.text()).isEqualTo("weak");
    }

    @Test
    void recordsPerBackendMetric() {
        TurTranscriptionProvider provider = mock(TurTranscriptionProvider.class);
        when(provider.transcribe(any(), any(), any())).thenReturn(TurTranscriptionResult.ok("x", "en"));
        TurTranscriptionProviderFactory factory = mock(TurTranscriptionProviderFactory.class);
        when(factory.resolveActive()).thenReturn(provider);

        TurTranscriptionMetricsService metrics = new TurTranscriptionMetricsService();
        service(factory, singleChunk(), resolver(TurTranscriptionProviderType.OPENAI, 100L),
                metrics, new TurConfigProperties())
                .transcribe(new byte[10], "audio/mpeg", "en");

        assertThat(metrics.rows()).anySatisfy(row ->
                assertThat(row).containsEntry("backend", "OPENAI").containsEntry("count", 1));
    }

}
