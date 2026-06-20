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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;
import com.viglet.turing.persistence.model.agent.TurSubmissionRetention;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T66 / §VII.6.g — enforces each agent's {@link TurSubmissionRetention}
 * policy over the completed {@code chat_flow_submission} archive.
 *
 * <p>Two entry points, one per time-driven vs event-driven mode:
 * <ul>
 *   <li>{@link #purgeExpiredSubmissions()} — swept by the daily
 *       {@code TurSubmissionRetentionCleanupJob}; deletes submissions older
 *       than {@code submissionRetentionDays} for every agent in
 *       {@code RETAIN_DAYS} mode.</li>
 *   <li>{@link #applyPostExportRetention(String)} — called by the T65 export
 *       endpoint after the bundle has been built; deletes a conversation's
 *       submissions when the owning agent is in {@code DELETE_AFTER_EXPORT}
 *       mode.</li>
 * </ul>
 *
 * <p>{@code RETAIN_FOREVER} (the default) is a no-op in both paths, so
 * existing deployments keep every submission until an operator opts in. This
 * service deliberately touches only {@code chat_flow_submission}; live-state
 * {@code pii_*} expiry is the separate concern of {@code TurPiiSlotTtlCleanupJob}
 * (T61).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSubmissionRetentionService {

    private final TurAIAgentRepository agentRepository;
    private final TurChatFlowSubmissionRepository submissionRepository;

    public TurSubmissionRetentionService(TurAIAgentRepository agentRepository,
            TurChatFlowSubmissionRepository submissionRepository) {
        this.agentRepository = agentRepository;
        this.submissionRepository = submissionRepository;
    }

    /** Source of "now" for the retention cutoff; tests pin it for determinism. */
    private Clock clock = Clock.systemDefaultZone();

    /** Visible for testing — pin the clock so the cutoff is deterministic. */
    void setClockForTest(Clock clock) {
        this.clock = clock;
    }

    /**
     * Deletes every submission older than the configured day threshold for
     * each agent in {@code RETAIN_DAYS} mode. Agents in {@code RETAIN_FOREVER}
     * / {@code DELETE_AFTER_EXPORT} mode, and {@code RETAIN_DAYS} agents whose
     * day count is null or non-positive, are skipped.
     *
     * @return total number of submissions deleted across all agents
     */
    @Transactional
    public int purgeExpiredSubmissions() {
        LocalDateTime now = LocalDateTime.now(clock);
        int totalDeleted = 0;
        for (TurAIAgent agent : agentRepository.findAll()) {
            if (agent.getSubmissionRetention() != TurSubmissionRetention.RETAIN_DAYS) {
                continue;
            }
            Integer days = agent.getSubmissionRetentionDays();
            // RETAIN_DAYS selected but no (or invalid) threshold — treat as
            // disabled so a half-configured policy never silently deletes.
            if (days != null && days > 0) {
                LocalDateTime cutoff = now.minusDays(days);
                List<TurChatFlowSubmission> expired =
                        submissionRepository.findByFlow_TurAIAgent_IdAndCompletedAtBefore(agent.getId(), cutoff);
                if (!expired.isEmpty()) {
                    submissionRepository.deleteAll(expired);
                    totalDeleted += expired.size();
                    log.info("[SubmissionRetention] deleted {} submission(s) older than {} day(s) "
                            + "(cutoff={}) for agent '{}' ({})",
                            expired.size(), days, cutoff, agent.getTitle(), agent.getId());
                }
            }
        }
        if (totalDeleted == 0) {
            log.debug("[SubmissionRetention] no expired submissions to purge (checked at {})", now);
        }
        return totalDeleted;
    }

    /**
     * Deletes the submissions of a just-exported conversation when (and only
     * when) the owning agent is in {@code DELETE_AFTER_EXPORT} mode. A
     * conversation that spans several flows/agents only drops the submissions
     * whose agent opted into post-export deletion. Best-effort: persistence
     * failures are swallowed + logged so a retention hiccup never breaks the
     * export download.
     *
     * @return number of submissions deleted for the conversation
     */
    @Transactional
    public int applyPostExportRetention(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        try {
            List<TurChatFlowSubmission> toDelete =
                    submissionRepository.findByConversationIdOrderByCompletedAtDesc(conversationId).stream()
                            .filter(TurSubmissionRetentionService::isDeleteAfterExport)
                            .toList();
            if (toDelete.isEmpty()) {
                return 0;
            }
            submissionRepository.deleteAll(toDelete);
            log.info("[SubmissionRetention] purged {} submission(s) for conversation '{}' after export "
                    + "(DELETE_AFTER_EXPORT)", toDelete.size(), conversationId);
            return toDelete.size();
        } catch (RuntimeException e) {
            log.warn("[SubmissionRetention] post-export purge failed for conversation '{}': {}",
                    conversationId, e.getMessage());
            return 0;
        }
    }

    private static boolean isDeleteAfterExport(TurChatFlowSubmission submission) {
        TurChatFlow flow = submission.getFlow();
        if (flow == null) {
            return false;
        }
        TurAIAgent agent = flow.getTurAIAgent();
        return agent != null
                && agent.getSubmissionRetention() == TurSubmissionRetention.DELETE_AFTER_EXPORT;
    }
}
