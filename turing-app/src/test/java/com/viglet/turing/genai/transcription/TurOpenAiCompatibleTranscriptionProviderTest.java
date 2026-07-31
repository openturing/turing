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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * T690 / §XLII.4 — {@link TurOpenAiCompatibleTranscriptionProvider}: unavailable
 * and fail-soft without a dedicated endpoint, delegates to the shared client
 * with the configured endpoint/model/key when set.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurOpenAiCompatibleTranscriptionProviderTest {

    private TurTranscriptionConfigResolver resolver(String endpoint, String model, String apiKey) {
        return resolver(endpoint, model, apiKey, TurTranscriptionHints.none());
    }

    private TurTranscriptionConfigResolver resolver(String endpoint, String model, String apiKey,
            TurTranscriptionHints hints) {
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);
        when(resolver.resolve()).thenReturn(new TurTranscriptionConfig(
                TurTranscriptionProviderType.OPENAI_COMPATIBLE, endpoint, model, apiKey,
                26_214_400L, hints));
        return resolver;
    }

    @Test
    void typeIsOpenAiCompatible() {
        assertThat(new TurOpenAiCompatibleTranscriptionProvider(
                resolver("", "", ""), mock(TurOpenAiTranscriptionClient.class)).getType())
                .isEqualTo(TurTranscriptionProviderType.OPENAI_COMPATIBLE);
    }

    @Test
    void unavailableWithoutEndpoint() {
        var provider = new TurOpenAiCompatibleTranscriptionProvider(
                resolver("", "", ""), mock(TurOpenAiTranscriptionClient.class));
        assertThat(provider.isAvailable()).isFalse();
    }

    @Test
    void failsSoftWithoutEndpointNeverCallsClient() {
        TurOpenAiTranscriptionClient client = mock(TurOpenAiTranscriptionClient.class);
        var provider = new TurOpenAiCompatibleTranscriptionProvider(resolver("", "", ""), client);

        TurTranscriptionResult result = provider.transcribe(new byte[] { 1 }, "audio/mpeg", "en");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("dedicated endpoint");
        verifyNoInteractions(client);
    }

    @Test
    void delegatesToClientWithConfiguredConnection() {
        TurOpenAiTranscriptionClient client = mock(TurOpenAiTranscriptionClient.class);
        when(client.transcribe(any(), any(), any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("hi", "en"));
        var provider = new TurOpenAiCompatibleTranscriptionProvider(
                resolver("http://whisper:8000/v1", "faster-whisper-small", "local-key"), client);

        TurTranscriptionResult result = provider.transcribe(new byte[] { 1 }, "audio/mpeg", "en");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<TurTranscriptionTarget> target =
                ArgumentCaptor.forClass(TurTranscriptionTarget.class);
        verify(client).transcribe(target.capture(), any(), eq("audio/mpeg"), eq("en"),
                eq(TurTranscriptionHints.none()));
        assertThat(target.getValue().baseUrl()).isEqualTo("http://whisper:8000/v1");
        assertThat(target.getValue().apiKey()).isEqualTo("local-key");
        assertThat(target.getValue().effectiveModel()).isEqualTo("faster-whisper-small");
        assertThat(target.getValue().maxBytes()).isEqualTo(26_214_400L);
        assertThat(provider.isAvailable()).isTrue();
    }

    /**
     * T846 / §LXIII.1 — a self-hosted OpenAI-compatible server serves whisper, so
     * a blank model must NOT inherit the cloud path's {@code gpt-transcribe}.
     */
    @Test
    void blankModelDefaultsToWhisperOnTheSelfHostedPath() {
        TurOpenAiTranscriptionClient client = mock(TurOpenAiTranscriptionClient.class);
        when(client.transcribe(any(), any(), any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("hi", "en"));
        var provider = new TurOpenAiCompatibleTranscriptionProvider(
                resolver("http://whisper:8000/v1", "", ""), client);

        provider.transcribe(new byte[] { 1 }, "audio/mpeg", "en");

        ArgumentCaptor<TurTranscriptionTarget> target =
                ArgumentCaptor.forClass(TurTranscriptionTarget.class);
        verify(client).transcribe(target.capture(), any(), any(), any(), any());
        assertThat(target.getValue().effectiveModel()).isEqualTo("whisper-1");
        assertThat(target.getValue().family()).isEqualTo(TurTranscriptionModelFamily.WHISPER);
    }

    /** T847 / §LXIII.2 — per-request hints layer over the globally configured ones. */
    @Test
    void perRequestHintsOverrideTheGlobalOnes() {
        TurOpenAiTranscriptionClient client = mock(TurOpenAiTranscriptionClient.class);
        when(client.transcribe(any(), any(), any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("hi", "en"));
        var provider = new TurOpenAiCompatibleTranscriptionProvider(
                resolver("http://whisper:8000/v1", "", "",
                        new TurTranscriptionHints("global context", List.of("Turing"))),
                client);

        provider.transcribe(new byte[] { 1 }, "audio/mpeg", "en",
                TurTranscriptionHints.ofKeywords(List.of("Solr")));

        ArgumentCaptor<TurTranscriptionHints> hints =
                ArgumentCaptor.forClass(TurTranscriptionHints.class);
        verify(client).transcribe(any(), any(), any(), any(), hints.capture());
        // Prompt falls through from the global config; the keyword list is replaced.
        assertThat(hints.getValue().prompt()).isEqualTo("global context");
        assertThat(hints.getValue().keywords()).containsExactly("Solr");
    }
}
