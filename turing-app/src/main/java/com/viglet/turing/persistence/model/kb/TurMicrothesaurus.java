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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T668 / §XL (Block AQ) — a single microthesaurus: a hierarchical tree of
 * {@link TurThesaurusTerm}s in <strong>one language and one domain</strong>
 * (the Turing Thesaurus Exchange "authority file"). It belongs to a
 * {@link TurKnowledgeBase} and is the unit an SN site selects for index-time
 * expansion (T670). Locale-filtering is automatic: only microthesauri whose
 * {@link #language} equals a document's locale participate when it is indexed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "kb_microthesaurus")
@JsonIgnoreProperties({"turKnowledgeBase"})
public class TurMicrothesaurus implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    /** The single language of this tree (one microthesaurus = one language). */
    @Column(nullable = false, length = 20)
    private Locale language;

    /**
     * Knowledge domain / subject area (e.g. {@code GENERAL}, {@code EDUCATION},
     * {@code MEDICINE}). A free String, not an enum, so new domains need no
     * migration.
     */
    @Column(nullable = false, length = 100)
    private String domain;

    @Column
    private LocalDateTime modificationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_base_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurKnowledgeBase turKnowledgeBase;

    @OneToMany(mappedBy = "turMicrothesaurus", orphanRemoval = true,
            fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurThesaurusTerm> turThesaurusTerms = new HashSet<>();

    public void setTurThesaurusTerms(Set<TurThesaurusTerm> turThesaurusTerms) {
        this.turThesaurusTerms.clear();
        if (turThesaurusTerms != null) {
            this.turThesaurusTerms.addAll(turThesaurusTerms);
        }
    }
}
