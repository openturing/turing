/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.research;

/**
 * Lifecycle status of a single {@link TurResearchInterview} within a study run
 * (Block AW / §XLVI.2, T721). {@code PENDING} is the freshly-created default;
 * the runner flips it to {@code COMPLETED} once a transcript is captured or
 * {@code FAILED} (fail-open) when no LLM is available or the interview errors.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurResearchInterviewStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
