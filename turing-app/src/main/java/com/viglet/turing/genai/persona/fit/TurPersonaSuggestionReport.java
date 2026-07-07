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
 * Auto-suggest report (Block AA / §XXVI.8): given a piece of content and a set
 * of audience personas, the {@link TurContentFitResult} of each is batched and
 * the personas are ranked by fit, so editorial routing / Block Z A/B persona
 * selection can answer "which persona is this content even <em>for</em>?".
 *
 * <p>{@code rankings} is sorted best-fit first; {@code bestPersonaId} mirrors
 * the head of that list for convenience (null when no audience persona was
 * evaluated).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaSuggestionReport(
        String bestPersonaId,
        String bestPersonaName,
        double bestScore,
        int evaluatedCount,
        boolean llmAvailable,
        List<TurPersonaSuggestion> rankings) {
}
