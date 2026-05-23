/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.rag;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.rag.TurRagBm25Core;

/**
 * T24b / §III.2 — JPA repository for {@link TurRagBm25Core}.
 *
 * <p>Query patterns are driven by the runtime hybrid retrieval path:
 *
 * <ul>
 *   <li>{@link #findByTurStoreInstance_IdAndLocale} — fetch the core for
 *       a (vector store, query locale) tuple on every RAG query.</li>
 *   <li>{@link #findByTurStoreInstance_Id} — list all cores belonging to
 *       a store, used by the admin status panel + the reindex orchestrator
 *       to fan out work.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public interface TurRagBm25CoreRepository extends JpaRepository<TurRagBm25Core, String> {

    Optional<TurRagBm25Core> findByTurStoreInstance_IdAndLocale(String storeInstanceId, Locale locale);

    List<TurRagBm25Core> findByTurStoreInstance_Id(String storeInstanceId);
}
