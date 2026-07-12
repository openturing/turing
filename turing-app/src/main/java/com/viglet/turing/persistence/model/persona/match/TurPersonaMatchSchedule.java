/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona.match;

/**
 * How often a {@link TurPersonaMatchProject} re-runs its N×N fit analysis
 * (Block AT / §XLIII). {@link #MANUAL} projects only run on an explicit
 * "run now"; {@link #DAILY}/{@link #WEEKLY} are picked up by the scheduled
 * re-analysis job (cluster-wide-once via ShedLock).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaMatchSchedule {
    MANUAL,
    DAILY,
    WEEKLY
}
