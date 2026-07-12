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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.authoring.persona.TurPersonaAudioDeriveService.DraftResult;
import com.viglet.turing.genai.transcription.TurTranscriptionBusyException;
import com.viglet.turing.genai.transcription.TurTranscriptionResult;
import com.viglet.turing.genai.transcription.TurTranscriptionService;
import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTranscriptionProperty;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * T715 / §XLII.7 — async persona-from-audio jobs. Takes the (potentially long)
 * transcribe→analyse chain off the HTTP request thread so the browser can show a
 * live progress bar instead of blocking on one multi-minute POST.
 * {@link #submit(byte[], String, String)} accepts the bytes, assigns a job id,
 * hands the work to a <b>bounded</b> worker pool, and returns immediately; the
 * caller then subscribes to the SSE stream ({@link TurPersonaAudioJobEventBus})
 * or polls {@link #getStatus(String)}.
 *
 * <p>The worker runs the two phases explicitly so each can report progress:
 * {@link TurTranscriptionService#transcribe} with a per-chunk
 * {@link com.viglet.turing.genai.transcription.TurTranscriptionProgressListener}
 * ({@link TurPersonaAudioJobState#TRANSCRIBING}), then
 * {@link TurPersonaAudioDeriveService#draftFromTranscript}
 * ({@link TurPersonaAudioJobState#ANALYZING}). Reuses the transcription async
 * pool sizing / retention config ({@code turing.transcription.async-*}) since the
 * bound is the same class of work. Single-node only — the registry and pool live
 * in-process (same caveat as the SSE buses). The synchronous
 * {@link TurPersonaAudioDeriveService#derive} path is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaAudioJobService {

    private final TurTranscriptionService transcriptionService;
    private final TurPersonaAudioDeriveService deriveService;
    private final TurPersonaAudioJobEventBus eventBus;
    private final TurTranscriptionProperty props;

    private final ThreadPoolExecutor pool;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public TurPersonaAudioJobService(TurTranscriptionService transcriptionService,
            TurPersonaAudioDeriveService deriveService,
            TurPersonaAudioJobEventBus eventBus,
            TurConfigProperties configProperties) {
        this.transcriptionService = transcriptionService;
        this.deriveService = deriveService;
        this.eventBus = eventBus;
        this.props = configProperties.getTranscription() != null
                ? configProperties.getTranscription()
                : new TurTranscriptionProperty();

        int workers = Math.max(1, props.getAsyncWorkers());
        int queue = Math.max(1, props.getAsyncQueueCapacity());
        this.pool = new ThreadPoolExecutor(workers, workers, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queue), runnable -> {
                    Thread t = new Thread(runnable, "tur-persona-audio-job");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
        log.info("[PersonaAudioJob] async pool ready (workers={}, queue={})", workers, queue);
    }

    /**
     * Accept audio for background persona derivation and return the initial
     * (QUEUED) status. The audio is held only until a worker consumes it.
     *
     * @throws TurTranscriptionBusyException when the pool + queue are saturated
     * @throws IllegalArgumentException      when the audio is null/empty
     */
    public TurPersonaAudioJobStatus submit(byte[] audio, String mimeType, String languageHint) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Audio file is required");
        }
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId);
        jobs.put(jobId, job);
        eventBus.publish(job.snapshot());
        try {
            pool.execute(() -> run(job, audio, mimeType, languageHint));
        } catch (RejectedExecutionException e) {
            jobs.remove(jobId);
            throw new TurTranscriptionBusyException(
                    "Persona-from-audio workers are all busy and the queue is full; retry shortly.");
        }
        return job.snapshot();
    }

    /** Current status of a job, or empty when unknown/evicted. */
    public Optional<TurPersonaAudioJobStatus> getStatus(String jobId) {
        Job job = jobId == null ? null : jobs.get(jobId);
        return Optional.ofNullable(job).map(Job::snapshot);
    }

    private void run(Job job, byte[] audio, String mimeType, String languageHint) {
        job.markTranscribing();
        eventBus.publish(job.snapshot());
        log.info("[PersonaAudioJob] job {} started — transcribing {} bytes", job.jobId, audio.length);
        try {
            TurTranscriptionResult transcription = transcriptionService.transcribe(
                    audio, mimeType, languageHint,
                    (completed, total) -> {
                        boolean advanced = job.setChunkProgress(completed, total);
                        if (advanced) {
                            log.info("[PersonaAudioJob] job {} transcription {}/{} chunks",
                                    job.jobId, completed, total);
                            eventBus.publish(job.snapshot());
                        }
                    });
            if (transcription == null || !transcription.success()
                    || StringUtils.isBlank(transcription.text())) {
                job.markFailed(transcription == null ? "Transcription failed."
                        : StringUtils.defaultIfBlank(transcription.error(),
                                "Could not transcribe the audio."));
                eventBus.publish(job.snapshot());
                return;
            }

            job.markAnalyzing();
            eventBus.publish(job.snapshot());
            log.info("[PersonaAudioJob] job {} transcription done — analysing transcript ({} chars)",
                    job.jobId, transcription.text().length());

            DraftResult draft = deriveService.draftFromTranscript(transcription.text());
            if (draft.success() && draft.draft() != null) {
                log.info("[PersonaAudioJob] job {} succeeded — draft name='{}', systemInstruction {} chars",
                        job.jobId, draft.draft().getName(),
                        draft.draft().getSystemInstruction() == null ? 0
                                : draft.draft().getSystemInstruction().length());
                job.markSucceeded(draft.draft(), draft.transcript());
            } else {
                job.markFailed(StringUtils.defaultIfBlank(draft.error(),
                        "Could not draft a persona from the transcript."));
            }
        } catch (RuntimeException e) {
            log.warn("[PersonaAudioJob] job {} failed: {}", job.jobId, e.getMessage());
            job.markFailed("Persona-from-audio error: " + e.getMessage());
        }
        eventBus.publish(job.snapshot());
    }

    /**
     * Evict terminal jobs older than the retention window so completed drafts
     * don't accumulate. Runs every 10 minutes; queued/running jobs are untouched.
     */
    @Scheduled(fixedDelayString = "PT10M")
    public void evictExpired() {
        long retentionMs = Math.max(60, props.getAsyncJobRetentionSeconds()) * 1000L;
        long cutoff = System.currentTimeMillis() - retentionMs;
        int before = jobs.size();
        jobs.values().removeIf(job -> {
            TurPersonaAudioJobStatus s = job.snapshot();
            return s.terminal() && s.finishedAt() > 0 && s.finishedAt() < cutoff;
        });
        int removed = before - jobs.size();
        if (removed > 0) {
            log.debug("[PersonaAudioJob] evicted {} expired job(s)", removed);
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    /**
     * Mutable per-job holder. All transitions are guarded on the instance monitor
     * so {@link #snapshot()} always reads a consistent state; callers only ever
     * see the immutable {@link TurPersonaAudioJobStatus}.
     */
    private static final class Job {
        private final String jobId;
        private final long submittedAt = System.currentTimeMillis();
        private TurPersonaAudioJobState state = TurPersonaAudioJobState.QUEUED;
        private int completedChunks;
        private int totalChunks;
        private TurPersonaDto draft;
        private String transcript;
        private String error;
        private long startedAt;
        private long finishedAt;

        Job(String jobId) {
            this.jobId = jobId;
        }

        synchronized void markTranscribing() {
            this.state = TurPersonaAudioJobState.TRANSCRIBING;
            this.startedAt = System.currentTimeMillis();
        }

        /**
         * Record chunk progress monotonically (parallel completions can call this
         * out of order). Returns {@code true} only when the count actually
         * advanced, so the caller can skip a redundant/rewinding SSE publish.
         */
        synchronized boolean setChunkProgress(int completed, int total) {
            boolean changed = false;
            if (total > this.totalChunks) {
                this.totalChunks = total;
                changed = true;
            }
            if (completed > this.completedChunks) {
                this.completedChunks = completed;
                changed = true;
            }
            return changed;
        }

        synchronized void markAnalyzing() {
            this.state = TurPersonaAudioJobState.ANALYZING;
        }

        synchronized void markSucceeded(TurPersonaDto draft, String transcript) {
            this.state = TurPersonaAudioJobState.SUCCEEDED;
            this.draft = draft;
            this.transcript = transcript;
            this.finishedAt = System.currentTimeMillis();
        }

        synchronized void markFailed(String error) {
            this.state = TurPersonaAudioJobState.FAILED;
            this.error = error;
            this.finishedAt = System.currentTimeMillis();
        }

        synchronized TurPersonaAudioJobStatus snapshot() {
            return new TurPersonaAudioJobStatus(jobId, state, completedChunks, totalChunks,
                    draft, transcript, error, submittedAt, startedAt, finishedAt);
        }
    }
}
