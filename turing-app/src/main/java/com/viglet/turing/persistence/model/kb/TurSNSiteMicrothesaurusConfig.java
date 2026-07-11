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

import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.persistence.model.sn.TurSNSite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T670 / §XL (Block AQ) — the per-site index configuration for microthesaurus
 * expansion (one row per SN site). It records the backing Solr field name, the
 * query-time {@code qf} boost, and whether the recognition dictionary should
 * fold in {@code USE}/{@code USED_FOR} synonym surface forms. Absence of a row
 * (or {@link #enabled} = {@code false}) means the enricher stays out of the
 * indexing path (the ADR-0003 opt-in discipline).
 *
 * <p>Defaults match ADR 0003 §2: field {@code microthesaurus_terms}, boost
 * {@code 0.8} (below title/body), synonyms on.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "sn_site_microthesaurus_config")
public class TurSNSiteMicrothesaurusConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** ADR-0003 default backing field name. */
    public static final String DEFAULT_FIELD_NAME = "microthesaurus_terms";
    /** ADR-0003 default {@code qf} boost — below title/body so recall never dominates. */
    public static final double DEFAULT_BOOST = 0.8d;
    /** T677 default hierarchical concept-facet field name (path tokens). */
    public static final String DEFAULT_PATH_FIELD_NAME = "microthesaurus_path";

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Master toggle: when {@code false} the site keeps its selections but is not enriched. */
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, length = 100)
    private String fieldName = DEFAULT_FIELD_NAME;

    @Column(nullable = false)
    private double boost = DEFAULT_BOOST;

    @Column(nullable = false)
    private boolean includeSynonyms = true;

    /**
     * T677 — when {@code true} the enricher also writes level-prefixed cumulative
     * path tokens ({@code 0/Doença}, {@code 1/Doença/Doença respiratória}, …) to
     * {@link #pathFieldName} for Semantic-Navigation-style hierarchical drill-down
     * faceting. Off by default — the MVP keeps only the flat terms field.
     */
    @Column(nullable = false)
    private boolean pathFacetEnabled = false;

    @Column(nullable = false, length = 100)
    private String pathFieldName = DEFAULT_PATH_FIELD_NAME;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sn_site_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSite turSNSite;
}
