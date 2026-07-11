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
package com.viglet.turing.sn.synonym;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * T662 / §XXXIX (Block AP) — pure validation + normalization for a synonym
 * rule, split out of the service so it can be unit-tested without Spring or a
 * repository. Trims and de-duplicates tokens (case-insensitively, keeping the
 * first casing), and enforces the per-type shape that Algolia's model also
 * requires, so a malformed rule never reaches an engine plugin.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNSynonymNormalizer {

    /** The cleaned rule payload. */
    public record Normalized(String input, List<String> terms) {
    }

    private TurSNSynonymNormalizer() {
    }

    /**
     * @throws IllegalArgumentException when the rule is empty or violates its
     *         per-type minimums (translated to HTTP 400 by the service).
     */
    public static Normalized normalize(TurSNSynonymType type, String input, List<String> terms) {
        if (type == null) {
            throw new IllegalArgumentException("Synonym type is required");
        }
        String cleanInput = blankToNull(input);
        List<String> cleanTerms = dedupe(terms);

        if (type == TurSNSynonymType.REGULAR) {
            // Regular rules are symmetric: the whole set is equivalent, so an
            // input has no meaning and at least two tokens are required.
            cleanInput = null;
            if (cleanTerms.size() < 2) {
                throw new IllegalArgumentException(
                        "A REGULAR synonym needs at least two distinct terms");
            }
            return new Normalized(null, cleanTerms);
        }

        // ONE_WAY / ALTERNATIVE_CORRECTION_1|2 / PLACEHOLDER are all directional:
        // an input on the left, one or more targets on the right.
        if (cleanInput == null) {
            throw new IllegalArgumentException(
                    "A " + type + " synonym needs a non-blank input");
        }
        if (cleanTerms.isEmpty()) {
            throw new IllegalArgumentException(
                    "A " + type + " synonym needs at least one term");
        }
        return new Normalized(cleanInput, cleanTerms);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims, drops blanks, and removes case-insensitive duplicates (first wins). */
    private static List<String> dedupe(List<String> terms) {
        if (terms == null) {
            return new ArrayList<>();
        }
        Map<String, String> seen = new LinkedHashMap<>();
        for (String term : terms) {
            if (term == null) {
                continue;
            }
            String trimmed = term.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            seen.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(seen.values());
    }
}
