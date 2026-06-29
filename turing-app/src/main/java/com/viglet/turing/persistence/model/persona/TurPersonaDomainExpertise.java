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
 * How much domain expertise an {@link TurPersonaAudience} brings — drives the
 * reader's tolerance for jargon and unexplained acronyms. A {@link #NOVICE}
 * reader flags specialist terminology the content-fit evaluator would let pass
 * for an {@link #EXPERT}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaDomainExpertise {
    NOVICE,
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}
