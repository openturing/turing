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
 * How often a {@link TurResearchStudy} re-runs its cohort interviews (Block AW /
 * §XLVI.4, T729 — Continuous Insight). {@link #MANUAL} studies only run on an
 * explicit "run now"; {@link #DAILY}/{@link #WEEKLY} are picked up by the
 * scheduled re-run job (cluster-wide-once via ShedLock), turning one-shot research
 * into continuous validation. Mirrors {@code TurPersonaMatchSchedule} (Block AT).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurResearchSchedule {
    MANUAL,
    DAILY,
    WEEKLY
}
