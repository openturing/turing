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
package com.viglet.turing.persistence.dto.kb;

import java.util.List;

/**
 * T675 / §XL (Block AQ) — a <em>draft</em> microthesaurus tree produced by
 * LLM-assisted generation, in the structured (JSON) form of the Turing Thesaurus
 * Exchange authority file (ADR 0003 / T673). It is <strong>never persisted by
 * generation</strong>: the endpoint returns it for review, and only an explicit
 * accept materialises it (via the shipped T673 importer). Draft-local ids are
 * arbitrary tokens the LLM assigns to wire {@code broader}/{@code related} edges;
 * they become the terms' {@code externalId} after import.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurThesaurusDraft(
        String name,
        String description,
        /** ISO language tag (e.g. {@code pt}, {@code en}) for the whole tree. */
        String language,
        /** Domain label (e.g. {@code EDUCATION}); defaults to {@code GENERAL}. */
        String domain,
        List<TurThesaurusDraftTerm> terms) {

    /**
     * One draft term (a preferred descriptor).
     *
     * @param id         draft-local stable id (referenced by {@code broader}/{@code related})
     * @param label      preferred label
     * @param scopeNote  optional usage note
     * @param broader    draft-local id of the parent (broader) term, or {@code null} for a root
     * @param related    draft-local ids of associatively related terms (RT)
     * @param variations recognition surface forms (spelling / synonym variants)
     */
    public record TurThesaurusDraftTerm(
            String id,
            String label,
            String scopeNote,
            String broader,
            List<String> related,
            List<TurThesaurusDraftVariation> variations) {
    }

    /**
     * A recognition surface form for a term.
     *
     * @param surfaceForm     the text to recognise in documents
     * @param caseSensitive   whether matching is case-sensitive
     * @param accentSensitive whether matching is accent-sensitive
     */
    public record TurThesaurusDraftVariation(
            String surfaceForm,
            boolean caseSensitive,
            boolean accentSensitive) {
    }
}
