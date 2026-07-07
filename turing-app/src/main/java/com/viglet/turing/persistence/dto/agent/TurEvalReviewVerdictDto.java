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

import java.time.LocalDateTime;

import com.viglet.turing.persistence.model.agent.TurEvalReviewVerdict;

/**
 * T594 / §XXXIII.9 — API view of one reviewer's verdict on a review task.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalReviewVerdictDto(
        String id,
        String reviewer,
        boolean pass,
        double score,
        String notes,
        LocalDateTime reviewedAt) {

    public static TurEvalReviewVerdictDto fromEntity(TurEvalReviewVerdict v) {
        return new TurEvalReviewVerdictDto(v.getId(), v.getReviewer(), v.isReviewedPass(),
                v.getReviewedScore(), v.getNotes(), v.getReviewedAt());
    }
}
