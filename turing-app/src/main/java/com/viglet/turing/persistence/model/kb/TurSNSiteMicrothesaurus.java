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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T670 / §XL (Block AQ) — the join that records that an SN site has
 * <em>selected</em> a {@link TurMicrothesaurus} for index-time expansion. The
 * selection is <strong>opt-in</strong>: a site with no rows here indexes exactly
 * as it did before (byte-identical legacy path). The library ({@code kb_*}
 * tables) is authored once and reused; this table is the per-site subscription.
 *
 * <p>The microthesaurus is referenced by a <em>soft FK</em>
 * ({@link #microthesaurusId}) rather than a JPA association: the recognition
 * dictionary (T671) resolves the selected ids to trees in a single batch load,
 * and the join must not drag a lazy KB aggregate onto the SN-site graph. The SN
 * site <em>is</em> a real {@code @ManyToOne} so the selection is deleted with the
 * site.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "sn_site_microthesaurus",
        uniqueConstraints = @UniqueConstraint(name = "uk_sn_site_microthesaurus",
                columnNames = {"sn_site_id", "microthesaurusId"}))
public class TurSNSiteMicrothesaurus implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Soft FK to {@link TurMicrothesaurus}'s {@code getId()} in the KB library. */
    @Column(nullable = false, length = 255)
    private String microthesaurusId;

    /** Per-selection toggle; a disabled row keeps the choice but skips expansion. */
    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sn_site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private TurSNSite turSNSite;
}
