/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.time.Instant;

import com.viglet.turing.persistence.model.agent.TurOnlineEvalSnapshot;

/**
 * T603 / §XXXIII.18 — wire view of one online-eval snapshot. {@code note}
 * carries a fail-open explanation ("no recent sessions", "analytics store
 * disabled", …) for a snapshot that was <b>not</b> persisted; it is {@code null}
 * for a real, persisted snapshot.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurOnlineEvalSnapshotDto(
        String id,
        String agentId,
        Instant createdAt,
        Instant windowStart,
        Instant windowEnd,
        int sampledSessions,
        int gradedSessions,
        String graderStackId,
        double meanScore,
        double passRate,
        double negativeSentimentRate,
        double staleCitationRate,
        double failingSessionRate,
        boolean baseline,
        boolean driftDetected,
        String driftReason,
        String note) {

    public static TurOnlineEvalSnapshotDto of(TurOnlineEvalSnapshot s) {
        return new TurOnlineEvalSnapshotDto(s.getId(), s.getAgentId(), s.getCreatedAt(),
                s.getWindowStart(), s.getWindowEnd(), s.getSampledSessions(), s.getGradedSessions(),
                s.getGraderStackId(), s.getMeanScore(), s.getPassRate(), s.getNegativeSentimentRate(),
                s.getStaleCitationRate(), s.getFailingSessionRate(), s.isBaseline(),
                s.isDriftDetected(), s.getDriftReason(), null);
    }

    /** A non-persisted, fail-open "nothing to grade" result carrying only a reason. */
    public static TurOnlineEvalSnapshotDto unavailable(String agentId, Instant now, String note) {
        return new TurOnlineEvalSnapshotDto(null, agentId, now, now, now, 0, 0, null,
                -1d, -1d, 0d, -1d, 0d, false, false, null, note);
    }
}
