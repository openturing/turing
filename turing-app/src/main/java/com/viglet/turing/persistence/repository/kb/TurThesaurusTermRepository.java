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
package com.viglet.turing.persistence.repository.kb;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;

/**
 * T668 / §XL (Block AQ) — repository for thesaurus terms. Roots are the terms
 * whose {@code parentTermId} is null; {@code findByParentTermId} walks children.
 * The {@code JOIN FETCH} finder loads a whole microthesaurus' terms with their
 * variations in one shot so the T671 recognition-dictionary compiler can read
 * them without tripping lazy-init (the project runs with
 * {@code enable_lazy_load_no_trans=false}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurThesaurusTermRepository extends JpaRepository<TurThesaurusTerm, String> {

    List<TurThesaurusTerm> findByTurMicrothesaurus(TurMicrothesaurus turMicrothesaurus);

    List<TurThesaurusTerm> findByTurMicrothesaurusAndParentTermIdIsNull(
            TurMicrothesaurus turMicrothesaurus);

    List<TurThesaurusTerm> findByParentTermId(String parentTermId);

    List<TurThesaurusTerm> findByTurMicrothesaurusAndEnabledTrue(
            TurMicrothesaurus turMicrothesaurus);

    @Query("""
            SELECT DISTINCT t FROM TurThesaurusTerm t
            LEFT JOIN FETCH t.variations
            WHERE t.turMicrothesaurus = :microthesaurus AND t.enabled = true
            """)
    List<TurThesaurusTerm> findEnabledWithVariations(
            @Param("microthesaurus") TurMicrothesaurus microthesaurus);
}
