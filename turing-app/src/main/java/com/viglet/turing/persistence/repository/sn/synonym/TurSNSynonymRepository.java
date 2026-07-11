/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.persistence.repository.sn.synonym;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonym;

/**
 * T662 / §XXXIX (Block AP) — repository for the engine-agnostic synonym store.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurSNSynonymRepository extends JpaRepository<TurSNSynonym, String> {

    List<TurSNSynonym> findByTurSNSite(Sort sort, TurSNSite turSNSite);

    List<TurSNSynonym> findByTurSNSiteAndLanguage(TurSNSite turSNSite, Locale language);

    /**
     * Loads the enabled synonym rules for a (site, locale) with their term lists
     * eagerly fetched, so the engine-apply path (T663+) can read
     * {@link TurSNSynonym}'s {@code getTerms()} outside a persistence context without
     * tripping lazy-init (the project runs with
     * {@code enable_lazy_load_no_trans=false}).
     */
    @Query("""
            SELECT DISTINCT s FROM TurSNSynonym s
            LEFT JOIN FETCH s.terms
            WHERE s.turSNSite = :site AND s.language = :language AND s.enabled = true
            """)
    List<TurSNSynonym> findEnabledForApply(@Param("site") TurSNSite site,
            @Param("language") Locale language);
}
