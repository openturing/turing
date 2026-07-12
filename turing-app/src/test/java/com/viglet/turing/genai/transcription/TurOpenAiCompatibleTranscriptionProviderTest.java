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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

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
        TurTranscriptionConfigResolver resolver = mock(TurTranscriptionConfigResolver.class);
        when(resolver.resolve()).thenReturn(new TurTranscriptionConfig(
                TurTranscriptionProviderType.OPENAI_COMPATIBLE, endpoint, model, apiKey, 26_214_400L));
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
        when(client.transcribe(any(), any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(TurTranscriptionResult.ok("hi", "en"));
        var provider = new TurOpenAiCompatibleTranscriptionProvider(
                resolver("http://whisper:8000/v1", "faster-whisper-small", "local-key"), client);

        TurTranscriptionResult result = provider.transcribe(new byte[] { 1 }, "audio/mpeg", "en");

        assertThat(result.success()).isTrue();
        verify(client).transcribe(eq("http://whisper:8000/v1"), eq("local-key"),
                eq("faster-whisper-small"), eq(26_214_400L), any(), eq("audio/mpeg"), eq("en"));
        assertThat(provider.isAvailable()).isTrue();
    }
}
