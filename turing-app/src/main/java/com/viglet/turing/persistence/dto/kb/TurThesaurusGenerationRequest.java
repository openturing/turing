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

/**
 * T675 / §XL (Block AQ) — the prompt for LLM-assisted microthesaurus generation:
 * a {@code domain} + {@code language} (and optional free-text {@code guidance})
 * from which a hierarchy is drafted. {@code maxTerms} caps the tree size so the
 * one-shot structured call stays within the output budget.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurThesaurusGenerationRequest(
        String domain,
        String language,
        String guidance,
        Integer maxTerms) {

    /** Default cap when the caller does not specify one. */
    public static final int DEFAULT_MAX_TERMS = 24;

    /** Hard ceiling so a runaway request can't blow the output budget. */
    public static final int MAX_TERMS_CEILING = 60;

    /** The effective, clamped term cap for this request. */
    public int effectiveMaxTerms() {
        int requested = maxTerms == null || maxTerms <= 0 ? DEFAULT_MAX_TERMS : maxTerms;
        return Math.min(requested, MAX_TERMS_CEILING);
    }
}
