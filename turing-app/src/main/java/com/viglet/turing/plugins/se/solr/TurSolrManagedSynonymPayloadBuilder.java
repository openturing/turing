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
package com.viglet.turing.plugins.se.solr;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.plugins.se.TurSESynonymRule;

/**
 * T663 / §XXXIX (Block AP) — pure mapping of Turing's engine-neutral synonym
 * rules onto Solr's <a href=
 * "https://solr.apache.org/guide/solr/latest/indexing-guide/language-analysis.html#managed-synonyms">Managed
 * Synonyms</a> map form ({@code {"input": ["expansion", ...]}}). Kept free of
 * Spring/HTTP so it is fully unit-testable; the plugin only PUTs the resulting
 * {@link TurSolrSynonymPayload#mappings()} and reloads the core.
 *
 * <p>Type handling:
 * <ul>
 *   <li><b>REGULAR</b> {a,b,c} — symmetric, so each term becomes a key mapping
 *       to the <em>other</em> terms (the standard way to emulate a fully
 *       equivalent set with the directional map form).</li>
 *   <li><b>ONE_WAY</b> {@code input -> [e1, e2]} — a single key mapping.</li>
 *   <li><b>ALTERNATIVE_CORRECTION_1/2</b> — Solr synonyms have no per-rule typo
 *       budget, so these degrade to one-way ({@code input -> terms}) with a
 *       warning (matches the block's "degrade to one-way" rule).</li>
 *   <li><b>PLACEHOLDER</b> — no equivalent in Solr synonyms; reported as
 *       unsupported and skipped (never silently dropped).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSolrManagedSynonymPayloadBuilder {

    private TurSolrManagedSynonymPayloadBuilder() {
    }

    public static TurSolrSynonymPayload build(List<TurSESynonymRule> rules) {
        Map<String, LinkedHashSet<String>> mappings = new LinkedHashMap<>();
        Set<TurSNSynonymType> unsupported = EnumSet.noneOf(TurSNSynonymType.class);
        List<String> warnings = new ArrayList<>();
        int applied = 0;

        if (rules != null) {
            for (TurSESynonymRule rule : rules) {
                if (rule == null || rule.type() == null) {
                    continue;
                }
                boolean contributed = switch (rule.type()) {
                    case REGULAR -> addRegular(mappings, rule.terms());
                    case ONE_WAY -> addDirectional(mappings, rule.input(), rule.terms());
                    case ALTERNATIVE_CORRECTION_1, ALTERNATIVE_CORRECTION_2 ->
                            addCorrection(mappings, warnings, rule);
                    case PLACEHOLDER -> {
                        unsupported.add(TurSNSynonymType.PLACEHOLDER);
                        warnings.add("Placeholder synonym '" + safe(rule.input())
                                + "' is not supported on Solr and was skipped.");
                        yield false;
                    }
                };
                if (contributed) {
                    applied++;
                }
            }
        }

        Map<String, List<String>> out = new LinkedHashMap<>();
        mappings.forEach((key, values) -> {
            if (!values.isEmpty()) {
                out.put(key, new ArrayList<>(values));
            }
        });
        return new TurSolrSynonymPayload(out, applied, unsupported, warnings);
    }

    private static boolean addRegular(Map<String, LinkedHashSet<String>> mappings, List<String> terms) {
        List<String> clean = clean(terms);
        if (clean.size() < 2) {
            return false;
        }
        for (String term : clean) {
            LinkedHashSet<String> others = mappings.computeIfAbsent(term, k -> new LinkedHashSet<>());
            for (String other : clean) {
                if (!other.equalsIgnoreCase(term)) {
                    others.add(other);
                }
            }
        }
        return true;
    }

    private static boolean addDirectional(Map<String, LinkedHashSet<String>> mappings, String input,
            List<String> terms) {
        String key = input == null ? null : input.trim();
        List<String> clean = clean(terms);
        if (key == null || key.isEmpty() || clean.isEmpty()) {
            return false;
        }
        LinkedHashSet<String> values = mappings.computeIfAbsent(key, k -> new LinkedHashSet<>());
        for (String term : clean) {
            if (!term.equalsIgnoreCase(key)) {
                values.add(term);
            }
        }
        return !values.isEmpty();
    }

    private static boolean addCorrection(Map<String, LinkedHashSet<String>> mappings,
            List<String> warnings, TurSESynonymRule rule) {
        boolean contributed = addDirectional(mappings, rule.input(), rule.terms());
        if (contributed) {
            warnings.add("Correction '" + safe(rule.input())
                    + "' degraded to a one-way synonym (Solr has no per-rule typo budget).");
        }
        return contributed;
    }

    private static List<String> clean(List<String> terms) {
        List<String> out = new ArrayList<>();
        if (terms == null) {
            return out;
        }
        for (String term : terms) {
            if (term == null) {
                continue;
            }
            String trimmed = term.trim();
            if (!trimmed.isEmpty() && out.stream().noneMatch(trimmed::equalsIgnoreCase)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /** Lowercases a locale to the managed-resource name segment Solr uses. */
    public static String resourceForLocale(String base, Locale locale) {
        String tag = locale == null ? "und" : locale.toLanguageTag().toLowerCase(Locale.ROOT);
        return base + "_" + tag.replace('-', '_');
    }
}
