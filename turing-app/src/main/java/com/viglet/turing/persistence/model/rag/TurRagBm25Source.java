/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.rag;

/**
 * T24b / §III.2 — selects where the BM25 half of the hybrid retrieval
 * lives for a given {@code TurSNSiteGenAi} binding.
 *
 * <ul>
 *   <li>{@link #EMBEDDED}: the BM25 inverted index lives in the same
 *       {@code TurLuceneVectorStore} as the vectors (T24 base). Single
 *       collection, {@code StandardAnalyzer}, zero-config. Default —
 *       admin doesn't have to configure a search engine instance for
 *       RAG to work.</li>
 *   <li>{@link #SE_INSTANCE}: BM25 lives in dedicated per-locale cores
 *       in the configured {@code TurSEInstance} (Solr / Elasticsearch).
 *       Production multi-language path. Requires provisioning cores
 *       per locale via {@code TurRagBm25CoreProvisioner} and per-locale
 *       reindexing — see Phase 4's "Reindex All" admin action.</li>
 * </ul>
 *
 * <p>The flag is honoured only when both {@code ragBm25Fallback} and
 * {@code ragHybridSearch} are enabled on the {@code TurSNSiteGenAi}
 * binding — otherwise RAG runs in strict-vector or fallback-only mode
 * and this enum is ignored.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public enum TurRagBm25Source {
    EMBEDDED,
    SE_INSTANCE
}
