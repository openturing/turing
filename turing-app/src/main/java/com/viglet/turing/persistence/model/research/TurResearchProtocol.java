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
 * The interview protocol a {@link TurResearchStudy} runs (Block AW / §XLVI.2,
 * T720) — the three synth.users interview types, each driven by the shared
 * persona-chat executor (T578):
 * <ul>
 *   <li>{@link #DYNAMIC_SCRIPT} — a goal-driven interviewer sub-loop that asks
 *       adaptive follow-ups until it has enough (or the question cap is hit);</li>
 *   <li>{@link #CUSTOM_SCRIPT} — a fixed, author-supplied question list asked
 *       verbatim in order;</li>
 *   <li>{@link #CONCEPT_TEST} — the adaptive loop seeded with a proposed
 *       concept/message the synthetic user reacts to.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurResearchProtocol {
    DYNAMIC_SCRIPT,
    CUSTOM_SCRIPT,
    CONCEPT_TEST
}
