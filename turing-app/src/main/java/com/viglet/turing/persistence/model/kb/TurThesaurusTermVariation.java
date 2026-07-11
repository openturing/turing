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
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * T668 / §XL (Block AQ) — a single <em>surface form</em> under which a
 * {@link TurThesaurusTerm} can be recognised in document text at index time
 * (T671). It maps the Turing Thesaurus Exchange authority-file {@code <variation>}
 * (see {@code docs/references/semantic-navigation-authority-file-sample.xml}): the
 * preferred label plus any spelling variants and {@code USE}/{@code USED_FOR}
 * non-preferred forms all become variations so a document that uses a synonym is
 * still recognised and still expanded to the canonical hierarchical path.
 *
 * <p>The source's textual match flags are normalised to booleans the matcher
 * reads directly: {@code case="ci"} → {@code caseSensitive=false},
 * {@code accent="as"} → {@code accentSensitive=true}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Embeddable
@NoArgsConstructor
public class TurThesaurusTermVariation implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The surface form matched in text (column avoids the reserved word "value"). */
    @Column(name = "surfaceForm", length = 1000)
    private String surfaceForm;

    /** Relative match weight (default {@code 100.0}). */
    @Column
    private double weight = 100.0d;

    /** Whether matching is case-sensitive (source {@code case="cs"}); default no. */
    @Column
    private boolean caseSensitive = false;

    /** Whether matching is accent-sensitive (source {@code accent="as"}); default yes. */
    @Column
    private boolean accentSensitive = true;

    /**
     * The variation's language. Usually the owning microthesaurus' language;
     * kept per-variation for faithful round-tripping of the source format.
     */
    @Column(length = 20)
    private Locale language;

    public TurThesaurusTermVariation(String surfaceForm, Locale language) {
        this.surfaceForm = surfaceForm;
        this.language = language;
    }
}
