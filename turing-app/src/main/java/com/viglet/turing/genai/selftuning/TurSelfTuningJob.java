/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.selftuning;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T447 / §XXIII.6 — nightly, cluster-once self-tuning sweep. For every agent that
 * opted in ({@code selfTuningEnabled}), runs one {@link TurSelfTuningService}
 * cycle that may open a PR-style "suggested change" for human approval. Each
 * agent is isolated (one failure never aborts the sweep). Off by default per
 * agent, so on a fleet with none opted in this is a cheap no-op scan.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSelfTuningJob {

    private final TurAIAgentRepository agentRepository;
    private final TurSelfTuningService selfTuningService;

    public TurSelfTuningJob(TurAIAgentRepository agentRepository,
            TurSelfTuningService selfTuningService) {
        this.agentRepository = agentRepository;
        this.selfTuningService = selfTuningService;
    }

    @Scheduled(cron = "${turing.self-tuning.cron:0 0 2 * * *}")
    @SchedulerLock(name = "agentSelfTuning", lockAtMostFor = "PT4H", lockAtLeastFor = "PT1S")
    public void run() {
        int opted = 0;
        int opened = 0;
        for (TurAIAgent agent : agentRepository.findAll()) {
            if (!agent.isSelfTuningEnabled()) {
                continue;
            }
            opted++;
            try {
                if (selfTuningService.runCycle(agent).isPresent()) {
                    opened++;
                }
            } catch (RuntimeException e) {
                log.error("[SelfTuning] cycle failed for agent={}: {}", agent.getId(),
                        e.getMessage());
            }
        }
        if (opted > 0) {
            log.info("[SelfTuning] swept {} opted-in agent(s); opened {} suggestion(s)",
                    opted, opened);
        }
    }
}
