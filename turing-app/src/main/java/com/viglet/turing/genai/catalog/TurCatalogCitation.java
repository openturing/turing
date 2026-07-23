/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog;

import java.util.Map;

/**
 * T392 / §XX.12 — one cited catalog result returned by the grounded
 * conversational catalog copilot. It is the catalog analogue of
 * {@link com.viglet.turing.genai.rag.TurRagSource}: the wire shape that carries a
 * single index hit out to the client so a "talk to the catalog" answer can be
 * traced back to the document it came from.
 *
 * <p>The {@code rank} is the citation's 1-based footnote number — its order of
 * first appearance in the answer, the same number the (renumbered) answer text
 * cites as {@code [n]} (T800 / §LIV.10). It is <em>not</em> the row's position in
 * the underlying result list: in the T793 stuff-all path the LLM grounds on the
 * whole catalog, so the raw ranks run into the hundreds and are compacted to
 * {@code 1..k} before they reach the client. The optional
 * {@code rankingExplanation} carries the T389 objective-ranking breakdown
 * (lexical / semantic / fused rank, RRF score) when the site runs a hybrid
 * ranking mode, so the ranking stays auditable; it is {@code null} on the legacy
 * lexical path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCatalogCitation(
        int rank,
        String id,
        String title,
        String url,
        Double score,
        Map<String, Object> rankingExplanation) {
}
