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

package com.viglet.turing.persistence.model.sn.metric;

import java.io.Serial;
import java.io.Serializable;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Click event tied to a previous search — records that a user, after issuing
 * a query, opened a specific result. Combined with {@link TurSNSiteMetricAccess}
 * this enables real CTR analysis (clicks ÷ searches) per term/site/locale,
 * surfacing terms whose results exist but get ignored (relevance gap) versus
 * terms with no results at all (content gap).
 *
 * <p>The {@code sanatizedTerm} column mirrors the same NFD-stripped, lowercased
 * shape used by {@link TurSNSiteMetricAccess} so dashboards can JOIN both
 * tables on a stable key without case/accent skew.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Getter
@Entity
@Table(name = "sn_site_metric_click")
public class TurSNSiteMetricClick implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Setter
    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Setter
    private Instant accessDate;

    @Setter
    @Column(length = 50)
    private String userId;

    @Column
    private String term;

    @Column
    private String sanatizedTerm;

    @Setter
    @Column
    private Locale language;

    /**
     * Identifier of the result the user clicked. Free-form to accommodate any
     * documentId the search engine returns (URL, UUID, business ID, …).
     */
    @Setter
    @Column(length = 255)
    private String documentId;

    /**
     * 1-based rank of the clicked result within the page the user was viewing.
     * Useful to study scroll fatigue — most clicks are typically on the top 5.
     */
    @Setter
    @Column
    private int position;

    @Setter
    @ManyToOne
    @JoinColumn(name = "sn_site_id", nullable = false)
    private TurSNSite turSNSite;

    /**
     * Setting the term also derives the {@code sanatizedTerm} so admin
     * dashboards group equivalent queries (case/accent-insensitive). Mirrors
     * the contract of {@link TurSNSiteMetricAccess#setTerm(String)}.
     */
    public void setTerm(String term) {
        this.term = term;
        if (term == null) {
            this.sanatizedTerm = null;
            return;
        }
        this.sanatizedTerm = Normalizer.normalize(term, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("( )+", " ")
                .toLowerCase()
                .trim();
    }
}
