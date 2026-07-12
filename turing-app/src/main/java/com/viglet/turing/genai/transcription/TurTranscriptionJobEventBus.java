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

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * T692 / §XLII.6 — in-process pub/sub for async transcription job state changes,
 * keyed by {@code jobId}. The direct analogue of {@link com.viglet.turing.genai.workspace.TurWorkspaceEventBus}:
 * {@link TurTranscriptionJobService} publishes a fresh
 * {@link TurTranscriptionJobStatus} snapshot on every transition (queued →
 * running → terminal), and the SSE endpoint relays the snapshots for one job
 * until it terminates.
 *
 * <p>Single-node only — the sink lives in-process. Backpressure uses
 * {@code tryEmitNext} with the {@code FAIL_FAST} contract so a slow SSE
 * subscriber never blocks a worker; a dropped intermediate event self-heals
 * because the next transition republishes the full snapshot and a fresh
 * subscriber receives the current snapshot via {@code startWith} at the endpoint.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurTranscriptionJobEventBus {

    private final Sinks.Many<TurTranscriptionJobStatus> sink =
            Sinks.many().multicast().directBestEffort();

    /** Publish a job snapshot. A null status or one with a blank id is ignored. */
    public void publish(TurTranscriptionJobStatus status) {
        if (status == null || status.jobId() == null || status.jobId().isBlank()) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(status);
        if (result.isFailure()) {
            log.debug("[TranscriptionJobBus] dropped event for job={} state={}: {}",
                    status.jobId(), status.state(), result);
        }
    }

    /**
     * A hot, filtered view of the bus for one job — subscribers receive only
     * snapshots emitted after subscription. The SSE endpoint prepends the current
     * snapshot and completes on the first terminal one.
     */
    public Flux<TurTranscriptionJobStatus> subscribe(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Flux.empty();
        }
        return sink.asFlux().filter(status -> jobId.equals(status.jobId()));
    }
}
