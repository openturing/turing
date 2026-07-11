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

import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;

/**
 * T668 / §XL (Block AQ) — repository for the non-hierarchical term relations
 * (associative / equivalence / custom). The hierarchical broader/narrower spine
 * is NOT here — it lives on {@code TurThesaurusTerm.parentTermId}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurThesaurusTermRelationRepository
        extends JpaRepository<TurThesaurusTermRelation, String> {

    List<TurThesaurusTermRelation> findByTurThesaurusTerm(TurThesaurusTerm turThesaurusTerm);

    List<TurThesaurusTermRelation> findByTurThesaurusTermAndType(TurThesaurusTerm turThesaurusTerm,
            TurThesaurusRelationType type);

    List<TurThesaurusTermRelation> findByTargetTermId(String targetTermId);
}
