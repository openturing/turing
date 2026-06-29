/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * F.9 / §X.10.c — the cluster-wide-once poller that drives in-flight
 * distillation fine-tune jobs to completion (T169).
 *
 * <p>On each tick (cron {@code turing.distillation.poll-cron}, ShedLock so only
 * one node runs it) it asks {@link TurOpenAiDistillationService#pollPending} to
 * refresh every RUNNING job from OpenAI; when a fine-tune succeeds the service
 * runs the agent eval gate against the candidate and swaps the model only if it
 * passes. No-op when {@code turing.distillation.enabled=false}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurDistillationJobPoller {

    private final TurOpenAiDistillationService distillationService;
    private final TurConfigProperties configProperties;

    public TurDistillationJobPoller(TurOpenAiDistillationService distillationService,
            TurConfigProperties configProperties) {
        this.distillationService = distillationService;
        this.configProperties = configProperties;
    }

    @Scheduled(cron = "${turing.distillation.poll-cron:0 */15 * * * *}")
    @SchedulerLock(name = "distillationJobPoller", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void poll() {
        if (!configProperties.getDistillation().isEnabled()) {
            return;
        }
        distillationService.pollPending();
    }
}
