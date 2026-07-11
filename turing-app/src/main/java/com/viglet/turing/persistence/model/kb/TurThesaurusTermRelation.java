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
package com.viglet.turing.persistence.model.kb;

import java.io.Serial;
import java.io.Serializable;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T668 / §XL (Block AQ) — a directed, typed edge from a source
 * {@link TurThesaurusTerm} ({@link #turThesaurusTerm}) to a target term
 * ({@link #targetTermId}, a soft id-pointer). It carries the non-hierarchical
 * thesaurus vocabulary — associative {@code RELATED}, equivalence
 * {@code USE}/{@code USED_FOR}, or {@code CUSTOM} — with an explicit
 * {@link #directionality}. The broader/narrower hierarchy itself is NOT a relation
 * row; it lives on {@link TurThesaurusTerm}'s {@code getParentTermId()} so index-time
 * expansion stays a plain parent walk.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "kb_thesaurus_term_relation")
@JsonIgnoreProperties({"turThesaurusTerm"})
public class TurThesaurusTermRelation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TurThesaurusRelationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TurThesaurusRelationDirectionality directionality =
            TurThesaurusRelationDirectionality.UNIDIRECTIONAL;

    /** Soft id-pointer to the target term (kept an id so the target is not eagerly loaded). */
    @Column(length = 255)
    private String targetTermId;

    // the source term that owns this relation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_term_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurThesaurusTerm turThesaurusTerm;
}
