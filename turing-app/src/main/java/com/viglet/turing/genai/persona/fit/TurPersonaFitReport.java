/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

import java.util.List;

/**
 * Aggregated content-fit report for a persona's whole notebook (Block AA /
 * §XXVI.5). One {@link TurContentFitResult} per evaluated source plus the
 * notebook-wide average, rendered in the admin as per-source red/amber/green
 * fit bars (à la T388 field coverage).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaFitReport(
        String personaId,
        double overallScore,
        int evaluatedCount,
        int totalSources,
        boolean llmAvailable,
        boolean canRegenerate,
        List<TurContentFitResult> sources) {
}
