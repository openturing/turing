/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.plugins.se;

import java.util.Map;

/**
 * T24b / §III.2 — minimal hit row returned by
 * {@link TurSearchEnginePlugin#retrieveStandalone}. Standalone searches
 * (RAG BM25 cores, not tied to any {@code TurSNSite}) don't carry the
 * pagination / facet / spellcheck baggage of {@code TurSEResults}, so we
 * use a tight record that maps cleanly onto a Spring AI {@code Document}.
 *
 * <p><b>id</b> is the document id as stored in the SE — for RAG cores
 * this matches the vector store chunk id exactly, so the
 * {@code reciprocalRankFusion} in {@code TurLuceneVectorStore} can join
 * on id across the two retrieval lists.
 *
 * <p><b>content</b> is the stored {@code content} field text (the chunk
 * body). Plugins must populate this — RAG fusion + prompt building need
 * the actual text, not just the id.
 *
 * <p><b>metadata</b> is a flat map of any extra stored fields the SE
 * returned ({@code assetId}, {@code chunkIndex}, {@code sourceFile}).
 * Plugins may include engine-specific debug fields; callers should not
 * rely on a fixed schema beyond the canonical chunk fields.
 *
 * <p><b>score</b> is the raw relevance score from the SE — Lucene BM25
 * value (Solr / ES / Lucene). Used as a tie-breaker inside RRF only;
 * cross-engine score comparability is NOT guaranteed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public record TurSEStandaloneHit(String id, String content, Map<String, Object> metadata, double score) {
}
