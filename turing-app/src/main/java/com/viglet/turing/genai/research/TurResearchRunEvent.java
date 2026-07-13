/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import java.time.Instant;

import com.viglet.turing.genai.research.dto.TurResearchInterviewDto;

/**
 * A progress event streamed over SSE while a study's cohort interviews run
 * (Block AW / §XLVI.2, T721). The studio (T730) subscribes and fills the live
 * interview feed: {@link Type#STARTED} carries the participant count,
 * {@link Type#INTERVIEW} carries one freshly completed (or reused) transcript,
 * {@link Type#DONE} stamps completion, {@link Type#ERROR} reports a run-level
 * failure. Mirrors {@code TurPersonaMatchRunEvent}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchRunEvent(
        Type type,
        String studyId,
        int total,
        int completed,
        TurResearchInterviewDto interview,
        Instant lastRunAt,
        String error) {

    public enum Type {
        STARTED,
        INTERVIEW,
        DONE,
        ERROR
    }

    public static TurResearchRunEvent started(String studyId, int total) {
        return new TurResearchRunEvent(Type.STARTED, studyId, total, 0, null, null, null);
    }

    public static TurResearchRunEvent interview(String studyId, int total, int completed,
            TurResearchInterviewDto interview) {
        return new TurResearchRunEvent(Type.INTERVIEW, studyId, total, completed, interview, null,
                null);
    }

    public static TurResearchRunEvent done(String studyId, int total, Instant lastRunAt) {
        return new TurResearchRunEvent(Type.DONE, studyId, total, total, null, lastRunAt, null);
    }

    public static TurResearchRunEvent error(String studyId, String error) {
        return new TurResearchRunEvent(Type.ERROR, studyId, 0, 0, null, null, error);
    }
}
