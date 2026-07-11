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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T668 / §XL (Block AQ) — a node in a {@link TurMicrothesaurus} tree: a preferred
 * term / descriptor. The <strong>broader/narrower hierarchy</strong> (the source
 * {@code BT}/{@code NT} spine) is modelled as a <em>soft</em>
 * {@link #parentTermId} id-pointer (the {@code TurChatFlowState.parentStateId}
 * precedent) rather than a self-referencing JPA association, so the index-time
 * ancestor walk (T672) stays a plain {@code while (parent != null)} loop and
 * avoids recursive lazy loading. {@code parentTermId == null} marks a root term.
 *
 * <p>{@link #variations} are the surface forms under which the term is recognised
 * in text; {@link #relations} carry every non-hierarchical link (associative
 * {@code RT}, equivalence {@code U}/{@code UF}, {@code CUSTOM}). Recognition (via
 * variations) is deliberately separate from expansion (via the parent spine): a
 * document that uses a synonym is still recognised and still expanded to the
 * canonical path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "kb_thesaurus_term")
@JsonIgnoreProperties({"turMicrothesaurus"})
public class TurThesaurusTerm implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The preferred term / descriptor label. */
    @Column(nullable = false, length = 1000)
    private String label;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Sibling ordering within the same parent. */
    @Column
    private int termOrder = 0;

    /** Optional scope note / definition (controlled-vocabulary scope note). */
    @Column(length = 4000)
    private String scopeNote;

    /** The id this term had in its source (e.g. the source system numeric id) for round-trip. */
    @Column(length = 255)
    private String externalId;

    /** Soft FK to the broader (parent) term within the same microthesaurus; null = root. */
    @Column(length = 255)
    private String parentTermId;

    @Column
    private LocalDateTime modificationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "microthesaurus_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurMicrothesaurus turMicrothesaurus;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "kb_thesaurus_term_variation",
            joinColumns = @JoinColumn(name = "term_id"))
    @OrderColumn(name = "variationOrder")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<TurThesaurusTermVariation> variations = new ArrayList<>();

    @OneToMany(mappedBy = "turThesaurusTerm", orphanRemoval = true,
            fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurThesaurusTermRelation> relations = new HashSet<>();

    public void setVariations(List<TurThesaurusTermVariation> variations) {
        this.variations.clear();
        if (variations != null) {
            this.variations.addAll(variations);
        }
    }

    public void setRelations(Set<TurThesaurusTermRelation> relations) {
        this.relations.clear();
        if (relations != null) {
            this.relations.addAll(relations);
        }
    }
}
