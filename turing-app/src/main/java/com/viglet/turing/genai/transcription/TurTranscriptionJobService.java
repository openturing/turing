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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * T692 / §XLII.6 — async transcription jobs. Takes long audio off the HTTP
 * request thread: {@link #submit(byte[], String, String)} accepts the bytes,
 * assigns a job id, hands the work to a <b>bounded</b> worker pool, and returns
 * immediately; callers then poll {@link #getStatus(String)} or subscribe to the
 * SSE stream ({@link TurTranscriptionJobEventBus}) for the transcript.
 *
 * <p><b>Back-pressure.</b> The pool has a fixed number of workers
 * ({@code turing.transcription.async-workers}) and a bounded queue
 * ({@code async-queue-capacity}). When both are saturated a submission is
 * rejected with {@link TurTranscriptionBusyException} (HTTP 429) rather than
 * letting the queue — and memory, since each job holds its audio bytes until a
 * worker consumes them — grow without limit. This is the scale lever once
 * chunking (T688/T689) makes multi-hour recordings tractable: the chunk fan-out
 * bounds per-job load, and this pool bounds cross-job load.
 *
 * <p>The actual transcription (chunk, transcribe, stitch) is delegated to
 * {@link TurTranscriptionService}, so async jobs inherit large-file support and
 * every config-selected backend for free. Terminal results are retained in
 * memory for {@code async-job-retention-seconds} so a client that reconnects can
 * still read them, then swept. Single-node only — the registry and pool live
 * in-process (same caveat as the SSE buses).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurTranscriptionJobService {

    private final TurTranscriptionService transcriptionService;
    private final TurTranscriptionJobEventBus eventBus;
    private final TurTranscriptionProperty props;

    private final ThreadPoolExecutor pool;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public TurTranscriptionJobService(TurTranscriptionService transcriptionService,
            TurTranscriptionJobEventBus eventBus,
            TurConfigProperties configProperties) {
        this.transcriptionService = transcriptionService;
        this.eventBus = eventBus;
        this.props = configProperties.getTranscription() != null
                ? configProperties.getTranscription()
                : new TurTranscriptionProperty();

        int workers = Math.max(1, props.getAsyncWorkers());
        int queue = Math.max(1, props.getAsyncQueueCapacity());
        this.pool = new ThreadPoolExecutor(workers, workers, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), runnable -> {
                    Thread t = new Thread(runnable, "tur-transcription-job");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
        log.info("[TranscriptionJob] async pool ready (workers={}, queue={})", workers, queue);
    }

    /** True when a transcription backend is configured — async jobs require one. */
    public boolean isAvailable() {
        return transcriptionService.isAvailable();
    }

    /**
     * Accept audio for background transcription and return the initial (QUEUED)
     * status. The audio is held only until a worker consumes it.
     *
     * @throws TurTranscriptionBusyException when the pool + queue are saturated
     * @throws IllegalArgumentException       when the audio is null/empty
     */
    public TurTranscriptionJobStatus submit(byte[] audio, String mimeType, String languageHint) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Audio file is required");
        }
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId);
        jobs.put(jobId, job);
        eventBus.publish(job.snapshot());
        try {
            pool.execute(() -> run(job, audio, mimeType, languageHint));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            jobs.remove(jobId);
            throw new TurTranscriptionBusyException(
                    "Transcription workers are all busy and the queue is full; retry shortly.");
        }
        return job.snapshot();
    }

    /** Current status of a job, or empty when unknown/evicted. */
    public Optional<TurTranscriptionJobStatus> getStatus(String jobId) {
        Job job = jobId == null ? null : jobs.get(jobId);
        return Optional.ofNullable(job).map(Job::snapshot);
    }

    private void run(Job job, byte[] audio, String mimeType, String languageHint) {
        job.markRunning();
        eventBus.publish(job.snapshot());
        try {
            TurTranscriptionResult result =
                    transcriptionService.transcribe(audio, mimeType, languageHint);
            if (result != null && result.success()) {
                job.markSucceeded(result.text(), result.language());
            } else {
                job.markFailed(result == null ? "No transcription result." : result.error());
            }
        } catch (RuntimeException e) {
            log.warn("[TranscriptionJob] job {} failed: {}", job.jobId, e.getMessage());
            job.markFailed("Transcription error: " + e.getMessage());
        }
        eventBus.publish(job.snapshot());
    }

    /**
     * Evict terminal jobs older than the retention window so completed
     * transcripts don't accumulate. Runs every 10 minutes; jobs still queued or
     * running are never touched.
     */
    @Scheduled(fixedDelayString = "PT10M")
    public void evictExpired() {
        long retentionMs = Math.max(60, props.getAsyncJobRetentionSeconds()) * 1000L;
        long cutoff = System.currentTimeMillis() - retentionMs;
        int before = jobs.size();
        jobs.values().removeIf(job -> {
            TurTranscriptionJobStatus s = job.snapshot();
            return s.terminal() && s.finishedAt() > 0 && s.finishedAt() < cutoff;
        });
        int removed = before - jobs.size();
        if (removed > 0) {
            log.debug("[TranscriptionJob] evicted {} expired job(s)", removed);
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    /**
     * Mutable per-job holder. All transitions are guarded on the instance monitor
     * so {@link #snapshot()} always reads a consistent state; callers only ever
     * see the immutable {@link TurTranscriptionJobStatus}.
     */
    private static final class Job {
        private final String jobId;
        private final long submittedAt = System.currentTimeMillis();
        private TurTranscriptionJobState state = TurTranscriptionJobState.QUEUED;
        private String transcript;
        private String language;
        private String error;
        private long startedAt;
        private long finishedAt;

        Job(String jobId) {
            this.jobId = jobId;
        }

        synchronized void markRunning() {
            this.state = TurTranscriptionJobState.RUNNING;
            this.startedAt = System.currentTimeMillis();
        }

        synchronized void markSucceeded(String transcript, String language) {
            this.state = TurTranscriptionJobState.SUCCEEDED;
            this.transcript = transcript;
            this.language = language;
            this.finishedAt = System.currentTimeMillis();
        }

        synchronized void markFailed(String error) {
            this.state = TurTranscriptionJobState.FAILED;
            this.error = error;
            this.finishedAt = System.currentTimeMillis();
        }

        synchronized TurTranscriptionJobStatus snapshot() {
            return new TurTranscriptionJobStatus(jobId, state, transcript, language, error,
                    submittedAt, startedAt, finishedAt);
        }
    }
}
