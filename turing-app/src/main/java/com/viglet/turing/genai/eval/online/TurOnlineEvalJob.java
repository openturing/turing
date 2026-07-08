/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.online;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.dto.agent.TurOnlineEvalSnapshotDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T603 / §XXXIII.18 — nightly, cluster-once continuous-eval sweep. For every
 * agent that opted in ({@code onlineEvalEnabled}), samples its recent live
 * traffic and records a quality snapshot (drift flagged versus the agent's
 * baseline). Each agent is isolated (one failure never aborts the sweep). The
 * whole sweep is skipped unless the global {@code turing.genai.online-eval.enabled}
 * switch is on, so a fleet with the feature off — or with no agent opted in — is
 * a cheap no-op scan.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOnlineEvalJob {

    private final TurAIAgentRepository agentRepository;
    private final TurOnlineEvalService onlineEvalService;
    private final TurConfigProperties configProperties;

    public TurOnlineEvalJob(TurAIAgentRepository agentRepository,
            TurOnlineEvalService onlineEvalService,
            TurConfigProperties configProperties) {
        this.agentRepository = agentRepository;
        this.onlineEvalService = onlineEvalService;
        this.configProperties = configProperties;
    }

    @Scheduled(cron = "${turing.genai.online-eval.cron:0 30 3 * * *}")
    @SchedulerLock(name = "agentOnlineEval", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1S")
    public void run() {
        if (!configProperties.getGenai().getOnlineEval().isEnabled()) {
            return;
        }
        int swept = 0;
        int drifted = 0;
        for (TurAIAgent agent : agentRepository.findAll()) {
            if (!agent.isOnlineEvalEnabled()) {
                continue;
            }
            swept++;
            try {
                TurOnlineEvalSnapshotDto snapshot = onlineEvalService.sampleAndGrade(agent.getId());
                if (snapshot.driftDetected()) {
                    drifted++;
                }
            } catch (RuntimeException e) {
                log.error("[OnlineEval] sweep failed for agent={}: {}", agent.getId(), e.getMessage());
            }
        }
        if (swept > 0) {
            log.info("[OnlineEval] swept {} opted-in agent(s); {} flagged drift", swept, drifted);
        }
    }
}
