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

import com.viglet.turing.genai.distillation.TurOpenAiDistillationService.DistillResult;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T191 / §X.15.e — "agent that improves itself overnight". A nightly,
 * cluster-once sweep that, for every agent that opted in
 * ({@code overnightImprovementEnabled}), kicks off a <b>propose-only</b>
 * distillation: its recent production traffic (T167 stored completions) is
 * distilled into a fine-tuned candidate (T169), the {@link TurDistillationJobPoller}
 * later scores it against the agent's eval golden sets (T126/T286), and — only if
 * it beats the incumbent — a {@code DISTILLATION_CANDIDATE} suggestion is opened
 * for human approval (T447 surface). Nothing is ever auto-applied.
 *
 * <p>The flywheel composes existing pieces; this job is just the nightly trigger.
 * Each agent is isolated (one failure never aborts the sweep), and the whole
 * thing is a cheap no-op when the distillation tier is off or no agent opted in.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOvernightImprovementJob {

    private final TurAIAgentRepository agentRepository;
    private final TurOpenAiDistillationService distillationService;

    public TurOvernightImprovementJob(TurAIAgentRepository agentRepository,
            TurOpenAiDistillationService distillationService) {
        this.agentRepository = agentRepository;
        this.distillationService = distillationService;
    }

    @Scheduled(cron = "${turing.distillation.overnight-cron:0 20 3 * * *}")
    @SchedulerLock(name = "agentOvernightImprovement", lockAtMostFor = "PT4H", lockAtLeastFor = "PT1S")
    public void run() {
        int opted = 0;
        int started = 0;
        for (TurAIAgent agent : agentRepository.findAll()) {
            if (!agent.isOvernightImprovementEnabled()) {
                continue;
            }
            opted++;
            try {
                DistillResult result = distillationService.distillForReview(agent.getId());
                if (result.started()) {
                    started++;
                    log.info("[Overnight] agent {} distillation candidate submitted ({})",
                            agent.getId(), result.job() == null ? "?" : result.job().getFineTuneJobId());
                } else {
                    log.debug("[Overnight] agent {} not distilled this run: {}",
                            agent.getId(), result.reason());
                }
            } catch (RuntimeException e) {
                log.error("[Overnight] distillation failed for agent={}: {}", agent.getId(),
                        e.getMessage());
            }
        }
        if (opted > 0) {
            log.info("[Overnight] swept {} opted-in agent(s); started {} distillation candidate(s)",
                    opted, started);
        }
    }
}
