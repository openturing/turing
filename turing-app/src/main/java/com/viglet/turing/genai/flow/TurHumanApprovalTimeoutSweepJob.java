/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurChatHumanApproval;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T119 / §IX.5.a — sweeps {@code humanApproval} records whose
 * {@code timeoutSeconds} deadline elapsed without an operator decision. Each
 * expired record is auto-resolved per its {@code timeoutBehavior}
 * ({@code auto_reject} by default), the resolved decision is written into the
 * approval slot, and the parked flow advances — so a conversation never hangs
 * forever waiting on a human who never showed up.
 *
 * <p>Runs every minute (overridable via
 * {@code turing.genai.human-approval.sweep-cron}). Cluster-wide-once via
 * ShedLock so multi-node deploys don't double-resolve the same record. Records
 * with no deadline ({@code expiresAt = null}) are wait-indefinitely and never
 * swept.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurHumanApprovalTimeoutSweepJob {

    private final TurHumanApprovalService approvalService;
    private final TurChatFlowEngineService chatFlowEngineService;

    public TurHumanApprovalTimeoutSweepJob(TurHumanApprovalService approvalService,
            TurChatFlowEngineService chatFlowEngineService) {
        this.approvalService = approvalService;
        this.chatFlowEngineService = chatFlowEngineService;
    }

    @Scheduled(cron = "${turing.genai.human-approval.sweep-cron:0 */1 * * * *}")
    @SchedulerLock(name = "humanApprovalTimeoutSweep",
            lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void run() {
        List<TurChatHumanApproval> expired = approvalService.findExpired();
        if (expired.isEmpty()) {
            return;
        }
        int resolved = 0;
        for (TurChatHumanApproval approval : expired) {
            try {
                String decision = approvalService.markTimedOut(approval);
                chatFlowEngineService.resumeSuspendedFlow(
                        approval.getConversationId(),
                        Map.of(approval.getApprovalSlot(), decision),
                        "timeout");
                resolved++;
            } catch (RuntimeException e) {
                log.warn("[HumanApproval] timeout sweep failed for token='{}' conv='{}': {}",
                        approval.getResumeToken(), approval.getConversationId(), e.getMessage());
            }
        }
        log.info("[HumanApproval] timeout sweep auto-resolved {} expired approval(s)", resolved);
    }
}
