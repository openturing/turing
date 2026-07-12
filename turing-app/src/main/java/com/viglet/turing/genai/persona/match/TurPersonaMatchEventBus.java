/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process pub/sub for Persona Match analysis progress (Block AT / §XLIII,
 * T698), keyed by {@code projectId}. Mirrors {@code TurTranscriptionJobEventBus}
 * but its {@link #publish} is {@code synchronized} because the N×N runner emits
 * cell events from a bounded worker pool — concurrent {@code tryEmitNext} on a
 * best-effort multicast sink is not otherwise serialization-safe.
 *
 * <p>Single-node only. A dropped intermediate event self-heals: the persisted
 * matrix (fetched after the run) is authoritative, and the SSE stream is a live
 * convenience for the heatmap fill.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurPersonaMatchEventBus {

    private final Sinks.Many<TurPersonaMatchRunEvent> sink =
            Sinks.many().multicast().directBestEffort();

    public synchronized void publish(TurPersonaMatchRunEvent event) {
        if (event == null || event.projectId() == null || event.projectId().isBlank()) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.debug("[PersonaMatchBus] dropped {} event for project={}: {}",
                    event.type(), event.projectId(), result);
        }
    }

    /** A hot, filtered view of the bus for one project. */
    public Flux<TurPersonaMatchRunEvent> subscribe(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Flux.empty();
        }
        return sink.asFlux().filter(event -> projectId.equals(event.projectId()));
    }
}
