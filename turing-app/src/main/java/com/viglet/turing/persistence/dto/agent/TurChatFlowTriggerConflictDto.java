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

import java.util.List;

/**
 * One pair of chat flows whose {@code triggerDescription} stems overlap enough
 * for the procedural router to score them ambiguously. Emitted by
 * {@link com.viglet.turing.genai.flow.TurTriggerConflictService} and surfaced
 * in the admin chat-flow UI so authors can disambiguate before the LLM
 * fallback masks the conflict.
 *
 * <p>Pairs are reported once (deterministic ordering by id) — the UI is
 * responsible for grouping back to a per-flow view if needed.
 *
 * @param flowAId            id of the lexicographically earlier flow in the pair
 * @param flowAName          display name of flow A
 * @param flowBId            id of the lexicographically later flow in the pair
 * @param flowBName          display name of flow B
 * @param severity           {@code "HIGH"} when stems overlap heavily enough
 *                           that the dominance ratio cannot separate the two;
 *                           {@code "WARNING"} when the overlap is notable but
 *                           still survivable.
 * @param similarity         Jaccard similarity of the stemmed token sets,
 *                           {@code 0..1}.
 * @param intersectionSize   number of stems shared by both descriptions.
 * @param overlappingTokens  up to 8 of the shared stems, sorted by length (desc)
 *                           then alphabetically — drives the highlighted-words
 *                           UI without blowing up the payload.
 * @param suggestion         localized one-line disambiguation hint (PT or EN
 *                           depending on the analyzer that scored the pair).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatFlowTriggerConflictDto(
        String flowAId,
        String flowAName,
        String flowBId,
        String flowBName,
        String severity,
        double similarity,
        int intersectionSize,
        List<String> overlappingTokens,
        String suggestion) {
}
