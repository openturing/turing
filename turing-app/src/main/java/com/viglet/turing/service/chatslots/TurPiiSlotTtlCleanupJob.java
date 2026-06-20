/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * T61 — hourly cleanup of {@code pii_*} slot values older than the
 * configured retention TTL. Reads the TTL from
 * {@link TurPiiSlotService#getTtlHours()} (default 24h); {@code 0} or
 * negative disables enforcement.
 *
 * <p>The job walks every {@code chat_flow_state} row whose
 * {@code updatedAt} is older than the threshold and, for each state,
 * clears every slot value whose name starts with {@code pii_}. We rely
 * on {@code updatedAt} as the lower bound on PII age — finer-grained
 * per-slot timestamps would require either a separate write table per
 * slot or denormalising the audit log into the cleanup path; the
 * {@code chat_flow_state}-grained approach matches how the conversation
 * naturally lives or dies. A state actively in use is always within
 * the window because {@code @PreUpdate} touches {@code updatedAt} on
 * every write.
 *
 * <p>Cluster-wide-once via ShedLock — multi-node deploys don't race
 * each other on the same delete.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurPiiSlotTtlCleanupJob {

    private final TurPiiSlotService piiSlotService;
    private final TurChatFlowStateRepository stateRepository;

    public TurPiiSlotTtlCleanupJob(TurPiiSlotService piiSlotService,
            TurChatFlowStateRepository stateRepository) {
        this.piiSlotService = piiSlotService;
        this.stateRepository = stateRepository;
    }

    @Scheduled(cron = "${turing.chat.pii.cleanup.cron:0 23 * * * *}")
    @SchedulerLock(name = "piiSlotTtlCleanup",
            lockAtMostFor = "PT15M", lockAtLeastFor = "PT1S")
    @Transactional
    public void run() {
        int ttlHours = piiSlotService.getTtlHours();
        if (ttlHours <= 0) {
            log.debug("[PiiCleanup] disabled (ttlHours={})", ttlHours);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.systemDefault()).minus(Duration.ofHours(ttlHours));
        List<TurChatFlowState> stale = stateRepository.findAll().stream()
                .filter(s -> s.getUpdatedAt() != null && s.getUpdatedAt().isBefore(cutoff))
                .toList();
        int statesTouched = 0;
        int slotsCleared = 0;
        for (TurChatFlowState state : stale) {
            Map<String, String> vars = new LinkedHashMap<>(ChatFlowOps.readVariables(state));
            int before = vars.size();
            vars.keySet().removeIf(TurPiiSlotService::isPiiSlot);
            int removed = before - vars.size();
            if (removed > 0) {
                ChatFlowOps.writeVariables(state, vars);
                stateRepository.save(state);
                statesTouched++;
                slotsCleared += removed;
            }
        }
        if (statesTouched > 0) {
            log.info("[PiiCleanup] cleared {} pii_* slot(s) across {} state(s) older than {} (ttl={}h)",
                    slotsCleared, statesTouched, cutoff, ttlHours);
        } else {
            log.debug("[PiiCleanup] no stale pii_* slots to clear (cutoff={})", cutoff);
        }
    }
}
