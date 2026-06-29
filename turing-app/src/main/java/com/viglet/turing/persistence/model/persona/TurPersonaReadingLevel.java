/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona;

/**
 * Education / reading-grade proxy for an {@link TurPersonaAudience}. The band
 * is the field that makes the "pouco estudo" (low-education) example crisp: a
 * persona at {@link #ELEMENTARY} should reject dense, jargon-heavy text. The
 * readability scorer (T466) maps the band to an approximate Flesch–Kincaid
 * grade ceiling.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaReadingLevel {
    /** ~grades 1-5, very simple language. */
    ELEMENTARY(5),
    /** ~grades 6-8. */
    MIDDLE(8),
    /** ~grades 9-12, secondary education. */
    SECONDARY(12),
    /** undergraduate / college reader. */
    UNDERGRADUATE(15),
    /** graduate / specialist reader, tolerates dense academic prose. */
    GRADUATE(18);

    private final int approxGradeCeiling;

    TurPersonaReadingLevel(int approxGradeCeiling) {
        this.approxGradeCeiling = approxGradeCeiling;
    }

    /**
     * Approximate Flesch–Kincaid grade level this reader can comfortably
     * handle. Text above this grade is read as "too complex" by the
     * deterministic readability scorer.
     */
    public int getApproxGradeCeiling() {
        return approxGradeCeiling;
    }
}
