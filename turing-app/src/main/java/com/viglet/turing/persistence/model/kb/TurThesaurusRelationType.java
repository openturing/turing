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

/**
 * T668 / §XL (Block AQ) — the thesaurus relationship vocabulary, grounded in the
 * Turing Thesaurus Exchange relation type set (BT/NT/RT/U/UF)
 * (see {@code docs/references/semantic-navigation-authority-file-sample.xml}).
 *
 * <p>The <strong>hierarchical spine</strong> ({@link #BROADER}/{@link #NARROWER},
 * i.e. {@code BT}/{@code NT}) is NOT stored here — it lives on
 * {@link TurThesaurusTerm}'s {@code getParentTermId()} so the index-time ancestor walk
 * (T672) stays a plain loop. These enum values model everything <em>else</em> a
 * term can point at: the associative and equivalence relations plus custom links.
 * {@code BROADER}/{@code NARROWER} remain in the enum so a full round-trip through
 * XML import/export (T673) can also represent the hierarchy explicitly when the
 * source does.
 *
 * <ul>
 *   <li>{@link #BROADER} — {@code BT}: points at a more general term (parent).</li>
 *   <li>{@link #NARROWER} — {@code NT}: points at a more specific term (child);
 *       the reciprocal inverse of {@code BT}.</li>
 *   <li>{@link #RELATED} — {@code RT}: symmetric associative link.</li>
 *   <li>{@link #USE} — {@code U}: this (non-preferred) term should be replaced by
 *       the target preferred term.</li>
 *   <li>{@link #USED_FOR} — {@code UF}: this (preferred) term is used instead of
 *       the target non-preferred term; the reciprocal inverse of {@code U}.</li>
 *   <li>{@link #CUSTOM} — an extension link with caller-defined semantics.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurThesaurusRelationType {
    BROADER,
    NARROWER,
    RELATED,
    USE,
    USED_FOR,
    CUSTOM
}
