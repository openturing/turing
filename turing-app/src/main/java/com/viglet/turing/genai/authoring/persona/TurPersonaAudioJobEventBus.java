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

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * T715 / §XLII.7 — in-process pub/sub for async persona-from-audio job state,
 * keyed by {@code jobId}. The direct analogue of
 * {@link com.viglet.turing.genai.transcription.TurTranscriptionJobEventBus}:
 * {@link TurPersonaAudioJobService} publishes a fresh {@link TurPersonaAudioJobStatus}
 * on every transition and every transcription-chunk completion, and the SSE
 * endpoint relays them for one job until it terminates.
 *
 * <p>Single-node only — the sink lives in-process. A dropped intermediate event
 * self-heals because the next transition republishes the full snapshot and a
 * fresh subscriber receives the current snapshot via {@code startWith}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurPersonaAudioJobEventBus {

    private final Sinks.Many<TurPersonaAudioJobStatus> sink =
            Sinks.many().multicast().directBestEffort();

    /** Publish a job snapshot. A null status or one with a blank id is ignored. */
    public void publish(TurPersonaAudioJobStatus status) {
        if (status == null || status.jobId() == null || status.jobId().isBlank()) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(status);
        if (result.isFailure()) {
            log.debug("[PersonaAudioJobBus] dropped event for job={} state={}: {}",
                    status.jobId(), status.state(), result);
        }
    }

    /**
     * A hot, filtered view of the bus for one job — subscribers receive only
     * snapshots emitted after subscription. The SSE endpoint prepends the current
     * snapshot and completes on the first terminal one.
     */
    public Flux<TurPersonaAudioJobStatus> subscribe(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Flux.empty();
        }
        return sink.asFlux().filter(status -> jobId.equals(status.jobId()));
    }
}
