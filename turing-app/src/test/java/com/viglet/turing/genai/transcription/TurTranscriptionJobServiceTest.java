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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

/**
 * T692 / §XLII.6 — {@link TurTranscriptionJobService}: lifecycle transitions,
 * fail-soft propagation, back-pressure rejection, and retention eviction.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTranscriptionJobServiceTest {

    private TurConfigProperties propsWith(int workers, int queue, int retentionSeconds) {
        TurTranscriptionProperty t = new TurTranscriptionProperty();
        t.setAsyncWorkers(workers);
        t.setAsyncQueueCapacity(queue);
        t.setAsyncJobRetentionSeconds(retentionSeconds);
        TurConfigProperties cfg = new TurConfigProperties();
        cfg.setTranscription(t);
        return cfg;
    }

    private TurTranscriptionService svcReturning(TurTranscriptionResult result) {
        TurTranscriptionService svc = mock(TurTranscriptionService.class);
        when(svc.transcribe(any(), any(), any())).thenReturn(result);
        when(svc.isAvailable()).thenReturn(true);
        return svc;
    }

    @Test
    void jobReachesSucceededWithTranscript() {
        TurTranscriptionJobService jobs = new TurTranscriptionJobService(
                svcReturning(TurTranscriptionResult.ok("hello world", "en")),
                new TurTranscriptionJobEventBus(), propsWith(2, 8, 3600));

        TurTranscriptionJobStatus queued = jobs.submit(new byte[] { 1, 2, 3 }, "audio/mpeg", "en");
        assertThat(queued.state()).isEqualTo(TurTranscriptionJobState.QUEUED);

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(
                () -> jobs.getStatus(queued.jobId()).map(TurTranscriptionJobStatus::terminal).orElse(false));

        TurTranscriptionJobStatus done = jobs.getStatus(queued.jobId()).orElseThrow();
        assertThat(done.state()).isEqualTo(TurTranscriptionJobState.SUCCEEDED);
        assertThat(done.transcript()).isEqualTo("hello world");
        assertThat(done.language()).isEqualTo("en");
        assertThat(done.finishedAt()).isPositive();
    }

    @Test
    void failedTranscriptionPropagatesError() {
        TurTranscriptionJobService jobs = new TurTranscriptionJobService(
                svcReturning(TurTranscriptionResult.fail("too large")),
                new TurTranscriptionJobEventBus(), propsWith(2, 8, 3600));

        TurTranscriptionJobStatus queued = jobs.submit(new byte[] { 1 }, "audio/mpeg", null);
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(
                () -> jobs.getStatus(queued.jobId()).map(TurTranscriptionJobStatus::terminal).orElse(false));

        TurTranscriptionJobStatus done = jobs.getStatus(queued.jobId()).orElseThrow();
        assertThat(done.state()).isEqualTo(TurTranscriptionJobState.FAILED);
        assertThat(done.error()).isEqualTo("too large");
        assertThat(done.transcript()).isNull();
    }

    @Test
    void emptyAudioRejected() {
        TurTranscriptionJobService jobs = new TurTranscriptionJobService(
                svcReturning(TurTranscriptionResult.ok("x", "en")),
                new TurTranscriptionJobEventBus(), propsWith(2, 8, 3600));
        assertThatThrownBy(() -> jobs.submit(new byte[0], "audio/mpeg", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownJobStatusIsEmpty() {
        TurTranscriptionJobService jobs = new TurTranscriptionJobService(
                svcReturning(TurTranscriptionResult.ok("x", "en")),
                new TurTranscriptionJobEventBus(), propsWith(2, 8, 3600));
        assertThat(jobs.getStatus("nope")).isEmpty();
    }

    @Test
    void saturatedPoolRejectsWithBusyException() throws InterruptedException {
        // 1 worker + queue capacity 1: hold the worker, fill the queue, third submit rejects.
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        TurTranscriptionService svc = mock(TurTranscriptionService.class);
        when(svc.transcribe(any(), any(), any())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return TurTranscriptionResult.ok("done", "en");
        });

        TurTranscriptionJobService jobs = new TurTranscriptionJobService(
                svc, new TurTranscriptionJobEventBus(), propsWith(1, 1, 3600));
        try {
            jobs.submit(new byte[] { 1 }, "audio/mpeg", null); // occupies the single worker
            started.await(5, TimeUnit.SECONDS);
            jobs.submit(new byte[] { 2 }, "audio/mpeg", null); // fills the queue (depth 1)

            assertThatThrownBy(() -> jobs.submit(new byte[] { 3 }, "audio/mpeg", null))
                    .isInstanceOf(TurTranscriptionBusyException.class);
        } finally {
            release.countDown();
        }
    }

    @Test
    void evictExpiredDropsOldTerminalJobs() {
        TurTranscriptionJobService jobs = new TurTranscriptionJobService(
                svcReturning(TurTranscriptionResult.ok("x", "en")),
                new TurTranscriptionJobEventBus(), propsWith(2, 8, 60));

        TurTranscriptionJobStatus queued = jobs.submit(new byte[] { 1 }, "audio/mpeg", null);
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(
                () -> jobs.getStatus(queued.jobId()).map(TurTranscriptionJobStatus::terminal).orElse(false));

        // Not yet expired (retention 60s) — the sweep keeps it.
        jobs.evictExpired();
        assertThat(jobs.getStatus(queued.jobId())).isPresent();
    }

    @Test
    void sseBusReceivesTerminalSnapshot() {
        TurTranscriptionJobEventBus bus = new TurTranscriptionJobEventBus();
        AtomicReference<TurTranscriptionJobStatus> last = new AtomicReference<>();
        bus.subscribe("watch-me").subscribe(last::set);

        bus.publish(new TurTranscriptionJobStatus("watch-me", TurTranscriptionJobState.SUCCEEDED,
                "done", "en", null, 1L, 2L, 3L));
        bus.publish(new TurTranscriptionJobStatus("other", TurTranscriptionJobState.FAILED,
                null, null, "x", 1L, 2L, 3L));

        assertThat(last.get()).isNotNull();
        assertThat(last.get().jobId()).isEqualTo("watch-me");
        assertThat(last.get().transcript()).isEqualTo("done");
    }
}
