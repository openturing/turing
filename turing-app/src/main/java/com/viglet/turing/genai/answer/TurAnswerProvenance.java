/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

/**
 * T839 / §LXII.8 — the retrieval provenance the answer service knows up front (the
 * site's knowledge-base mode and the rerank configuration), handed to
 * {@link TurAnswerStreamFramer} so it rides the terminal {@code trace} event. The
 * per-source rank breakdown travels separately on the {@code citations} event
 * (each citation's {@code rankingExplanation}).
 *
 * @param kbMode         the site's {@code TurSNKnowledgeBaseMode}, or {@code null}
 * @param rerankStrategy the rerank strategy applied, or {@code null} when off
 * @param rerankEnabled  whether the SN RAG reranker ran
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAnswerProvenance(String kbMode, String rerankStrategy, boolean rerankEnabled) {

    /** Empty provenance — nothing known (used when the framer is called bare). */
    public static final TurAnswerProvenance NONE = new TurAnswerProvenance(null, null, false);
}
