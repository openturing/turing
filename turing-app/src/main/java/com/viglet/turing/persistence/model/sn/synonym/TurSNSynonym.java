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
package com.viglet.turing.persistence.model.sn.synonym;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.persistence.model.sn.TurSNSite;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T662 / §XXXIX (Block AP) — the engine-agnostic, per-site + per-locale synonym
 * rule that is Turing's single source of truth. A rule is authored once and the
 * search-engine plugin ({@code applySynonyms}, T663+) pushes it into whichever
 * engine backs the site (Solr/Elasticsearch/Lucene), the same way the field
 * manifest (T382/T386) is the neutral schema that the plugin applies.
 *
 * <p>Scoping mirrors the other {@code TurSNSite} children (spotlight, locale):
 * the row belongs to a {@link TurSNSite}, which carries the {@code @TenantId},
 * so multi-tenant isolation flows through the FK rather than a duplicated
 * tenant column here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "sn_site_synonym")
public class TurSNSynonym implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Optional human label for the rule (helps the admin UI list). */
    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TurSNSynonymType type = TurSNSynonymType.REGULAR;

    /**
     * The locale the rule applies to. Synonyms are per-locale because the same
     * token set rarely holds across languages; the engine applies them to that
     * locale's index/core only.
     */
    @Column(nullable = false, length = 20)
    private Locale language;

    /**
     * The left-hand side for directional rules: the expanded input for
     * {@link TurSNSynonymType#ONE_WAY}, the mistyped word for the
     * {@code ALTERNATIVE_CORRECTION_*} types, and the tokenised slot for
     * {@link TurSNSynonymType#PLACEHOLDER}. Unused (null) for
     * {@link TurSNSynonymType#REGULAR}.
     */
    @Column(length = 2000)
    private String input;

    /**
     * The rule's token list: the equivalent set (REGULAR), the expansions
     * (ONE_WAY), the correction alternatives (ALTERNATIVE_CORRECTION_*) or the
     * placeholder replacements (PLACEHOLDER). Ordered for stable round-tripping.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sn_site_synonym_term",
            joinColumns = @JoinColumn(name = "synonym_id"))
    @OrderColumn(name = "termOrder")
    @Column(name = "term", length = 2000)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<String> terms = new ArrayList<>();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column
    private LocalDateTime modificationDate;

    // bi-directional many-to-one association to TurSNSite
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sn_site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSite turSNSite;

    public void setTerms(List<String> terms) {
        this.terms.clear();
        if (terms != null) {
            this.terms.addAll(terms);
        }
    }
}
