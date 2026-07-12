/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.authoring.persona.TurPersonaAudioDeriveService.DraftResult;
import com.viglet.turing.genai.transcription.TurTranscriptionProgressListener;
import com.viglet.turing.genai.transcription.TurTranscriptionResult;
import com.viglet.turing.genai.transcription.TurTranscriptionService;
import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

/**
 * T715 — the async persona-from-audio job orchestration: transcribe (with a
 * per-chunk progress listener) → analyse → terminal, and the fail-soft paths.
 * The transcription + LLM derivation are mocked; the job runs on the real
 * bounded pool, so terminal state is awaited.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaAudioJobServiceTest {

    @Mock
    private TurTranscriptionService transcriptionService;
    @Mock
    private TurPersonaAudioDeriveService deriveService;
    @Mock
    private TurConfigProperties configProperties;

    private TurPersonaAudioJobService service;

    @BeforeEach
    void setUp() {
        when(configProperties.getTranscription()).thenReturn(new TurTranscriptionProperty());
        service = new TurPersonaAudioJobService(transcriptionService, deriveService,
                new TurPersonaAudioJobEventBus(), configProperties);
    }

    @Test
    void runsTranscribeThenAnalyseThenSucceeds() {
        // Transcription reports 2/2 chunks via the listener, then returns text.
        when(transcriptionService.transcribe(any(), any(), any(), any())).thenAnswer(inv -> {
            TurTranscriptionProgressListener listener = inv.getArgument(3);
            listener.onProgress(0, 2);
            listener.onProgress(1, 2);
            listener.onProgress(2, 2);
            return TurTranscriptionResult.ok("I have sold cars for twenty years.", "en");
        });
        TurPersonaDto draft = new TurPersonaDto();
        draft.setName("veteran-seller");
        draft.setSystemInstruction("You are a veteran car salesman.");
        when(deriveService.draftFromTranscript("I have sold cars for twenty years."))
                .thenReturn(new DraftResult(true, null, "I have sold cars for twenty years.", draft));

        String jobId = service.submit(new byte[] { 1, 2, 3 }, "audio/mpeg", "en").jobId();

        await().atMost(Duration.ofSeconds(5)).until(() ->
                service.getStatus(jobId).map(TurPersonaAudioJobStatus::terminal).orElse(false));

        TurPersonaAudioJobStatus done = service.getStatus(jobId).orElseThrow();
        assertThat(done.state()).isEqualTo(TurPersonaAudioJobState.SUCCEEDED);
        assertThat(done.totalChunks()).isEqualTo(2);
        assertThat(done.completedChunks()).isEqualTo(2);
        assertThat(done.draft()).isNotNull();
        assertThat(done.draft().getName()).isEqualTo("veteran-seller");
        assertThat(done.transcript()).contains("sold cars");
        assertThat(done.error()).isNull();
    }

    @Test
    void failsSoftWhenTranscriptionFails() {
        when(transcriptionService.transcribe(any(), any(), any(), any()))
                .thenReturn(TurTranscriptionResult.fail("no STT backend"));

        String jobId = service.submit(new byte[] { 9 }, "audio/wav", null).jobId();

        await().atMost(Duration.ofSeconds(5)).until(() ->
                service.getStatus(jobId).map(TurPersonaAudioJobStatus::terminal).orElse(false));

        TurPersonaAudioJobStatus done = service.getStatus(jobId).orElseThrow();
        assertThat(done.state()).isEqualTo(TurPersonaAudioJobState.FAILED);
        assertThat(done.error()).isEqualTo("no STT backend");
        assertThat(done.draft()).isNull();
    }

    @Test
    void rejectsEmptyAudio() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.submit(new byte[0], "audio/mpeg", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
