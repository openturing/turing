/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.routine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * T48 — single global subscriber on {@link TurChatSlotEventBus} that nudges
 * the chat-flow engine whenever a slot lands. The engine re-walks any state
 * currently parked on a {@code scheduleAgent} node, so a routine completion
 * advances the flow without the user having to send another message.
 *
 * <p>Loop control: when the engine advances past a {@code scheduleAgent},
 * it publishes the merged slot map. That event re-enters this listener,
 * finds the conversation no longer parked on a {@code scheduleAgent}
 * cursor, and exits cheaply. {@link TurChatFlowEngineService#resumeParkedScheduleAgents}
 * is the loop-breaker — it only re-walks states whose current node is
 * literally {@code scheduleAgent}.
 *
 * <p>{@code @Lazy} on the engine breaks the boot cycle (engine →
 * scheduleAgent executor → JMS queue → engine).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatFlowAutoResumeService {

    private final TurChatSlotEventBus slotEventBus;
    private final TurChatFlowEngineService engine;
    private Disposable subscription;

    public TurChatFlowAutoResumeService(TurChatSlotEventBus slotEventBus,
            @Lazy TurChatFlowEngineService engine) {
        this.slotEventBus = slotEventBus;
        this.engine = engine;
    }

    @PostConstruct
    void subscribe() {
        this.subscription = slotEventBus.subscribeAll()
                .doOnNext(this::handle)
                .subscribe(
                        evt -> { /* doOnNext already handled */ },
                        err -> log.warn("[AutoResume] subscription error: {}", err.getMessage(), err));
        log.debug("[AutoResume] subscribed to slot event bus");
    }

    @PreDestroy
    void unsubscribe() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private void handle(TurChatSessionSlotsDto event) {
        if (event == null || event.conversationId() == null
                || event.conversationId().isBlank()) {
            return;
        }
        try {
            engine.resumeParkedScheduleAgents(event.conversationId());
        } catch (RuntimeException e) {
            log.warn("[AutoResume] resume failed for conv={}: {}",
                    event.conversationId(), e.getMessage(), e);
        }
    }

    /** Test-visible hook so unit tests can exercise the handle path. */
    void onSlotEventForTest(TurChatSessionSlotsDto event) {
        handle(event);
    }
}
