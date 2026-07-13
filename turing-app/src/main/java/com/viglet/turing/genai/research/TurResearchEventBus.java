/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process pub/sub for study cohort-interview progress (Block AW / §XLVI.2,
 * T721), keyed by {@code studyId}. Mirrors {@code TurPersonaMatchEventBus}: its
 * {@link #publish} is {@code synchronized} because the N-persona runner emits
 * interview events from a bounded worker pool, and concurrent {@code tryEmitNext}
 * on a best-effort multicast sink is not otherwise serialization-safe.
 *
 * <p>Single-node only. A dropped intermediate event self-heals: the persisted
 * interviews (fetched after the run) are authoritative; the SSE stream is a live
 * convenience for the studio's interview feed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurResearchEventBus {

    private final Sinks.Many<TurResearchRunEvent> sink =
            Sinks.many().multicast().directBestEffort();

    public synchronized void publish(TurResearchRunEvent event) {
        if (event == null || event.studyId() == null || event.studyId().isBlank()) {
            return;
        }
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.debug("[ResearchBus] dropped {} event for study={}: {}",
                    event.type(), event.studyId(), result);
        }
    }

    /** A hot, filtered view of the bus for one study. */
    public Flux<TurResearchRunEvent> subscribe(String studyId) {
        if (studyId == null || studyId.isBlank()) {
            return Flux.empty();
        }
        return sink.asFlux().filter(event -> studyId.equals(event.studyId()));
    }
}
