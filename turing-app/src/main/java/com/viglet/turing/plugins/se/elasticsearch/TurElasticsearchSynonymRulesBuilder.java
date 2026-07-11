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
package com.viglet.turing.plugins.se.elasticsearch;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.plugins.se.TurSESynonymRule;

/**
 * T664 / §XXXIX (Block AP) — pure mapping of Turing's engine-neutral synonym
 * rules onto Elasticsearch <a href=
 * "https://www.elastic.co/guide/en/elasticsearch/reference/current/synonyms-apis.html">Synonyms
 * API</a> rule lines (the Solr/WordNet line format ES accepts). Kept free of the
 * ES client so it is fully unit-testable; the plugin only PUTs the resulting
 * lines as a synonyms set and lets ES reload the search analyzer (no reindex).
 *
 * <p>Type handling mirrors {@code TurSolrManagedSynonymPayloadBuilder}:
 * <ul>
 *   <li><b>REGULAR</b> {a,b,c} → equivalent line {@code "a, b, c"} (both-way).</li>
 *   <li><b>ONE_WAY</b> {@code input -> [e1,e2]} → explicit mapping
 *       {@code "input => e1, e2"}.</li>
 *   <li><b>ALTERNATIVE_CORRECTION_1/2</b> → degrade to a one-way mapping with a
 *       warning (ES synonyms carry no per-rule typo budget).</li>
 *   <li><b>PLACEHOLDER</b> → reported unsupported and skipped.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurElasticsearchSynonymRulesBuilder {

    private TurElasticsearchSynonymRulesBuilder() {
    }

    public static TurElasticsearchSynonymRules build(List<TurSESynonymRule> rules) {
        List<String> lines = new ArrayList<>();
        Set<TurSNSynonymType> unsupported = EnumSet.noneOf(TurSNSynonymType.class);
        List<String> warnings = new ArrayList<>();
        int applied = 0;

        if (rules != null) {
            for (TurSESynonymRule rule : rules) {
                if (rule == null || rule.type() == null) {
                    continue;
                }
                String line = switch (rule.type()) {
                    case REGULAR -> regularLine(rule.terms());
                    case ONE_WAY -> directionalLine(rule.input(), rule.terms());
                    case ALTERNATIVE_CORRECTION_1, ALTERNATIVE_CORRECTION_2 ->
                            correctionLine(rule, warnings);
                    case PLACEHOLDER -> {
                        unsupported.add(TurSNSynonymType.PLACEHOLDER);
                        warnings.add("Placeholder synonym '" + safe(rule.input())
                                + "' is not supported on Elasticsearch and was skipped.");
                        yield null;
                    }
                };
                if (line != null) {
                    lines.add(line);
                    applied++;
                }
            }
        }
        return new TurElasticsearchSynonymRules(lines, applied, unsupported, warnings);
    }

    private static String regularLine(List<String> terms) {
        List<String> clean = clean(terms);
        return clean.size() < 2 ? null : String.join(", ", clean);
    }

    private static String directionalLine(String input, List<String> terms) {
        String key = input == null ? null : input.trim();
        List<String> clean = clean(terms);
        if (key == null || key.isEmpty() || clean.isEmpty()) {
            return null;
        }
        return key + " => " + String.join(", ", clean);
    }

    private static String correctionLine(TurSESynonymRule rule, List<String> warnings) {
        String line = directionalLine(rule.input(), rule.terms());
        if (line != null) {
            warnings.add("Correction '" + safe(rule.input())
                    + "' degraded to a one-way synonym (Elasticsearch has no per-rule typo budget).");
        }
        return line;
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

    /** A valid ES synonyms-set id for the locale (lowercase, underscores). */
    public static String synonymSetId(String base, Locale locale) {
        String tag = locale == null ? "und" : locale.toLanguageTag().toLowerCase(Locale.ROOT);
        return base + "_" + tag.replace('-', '_');
    }
}
