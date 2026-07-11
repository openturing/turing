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
package com.viglet.turing.plugins.se.lucene;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;
import com.viglet.turing.plugins.se.TurSESynonymRule;

/**
 * T665 / §XXXIX (Block AP) — pure mapping of Turing's engine-neutral synonym
 * rules onto a Lucene {@link SynonymMap} for a query-time
 * {@code SynonymGraphFilter} over the embedded store. Regular (bidirectional)
 * and one-way rules are natural; corrections degrade to one-way with a warning
 * (Lucene's filter has no per-typo budget); placeholders exceed the filter's
 * model and are surfaced as unsupported — the honest "Lucene limits" caveat —
 * rather than silently dropped.
 *
 * <p>Terms are lowercased so they match the {@code StandardAnalyzer}-lowercased
 * tokens the index was written with. Multi-word phrases (e.g. "galaxy tab") are
 * joined with {@link SynonymMap#WORD_SEPARATOR} via
 * {@link SynonymMap.Builder#join}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurLuceneSynonymMapBuilder {

    private TurLuceneSynonymMapBuilder() {
    }

    public static TurLuceneSynonymMap build(List<TurSESynonymRule> rules) {
        SynonymMap.Builder builder = new SynonymMap.Builder(true);
        Set<TurSNSynonymType> unsupported = EnumSet.noneOf(TurSNSynonymType.class);
        List<String> warnings = new ArrayList<>();
        int applied = 0;

        if (rules != null) {
            for (TurSESynonymRule rule : rules) {
                if (rule == null || rule.type() == null) {
                    continue;
                }
                boolean contributed = switch (rule.type()) {
                    case REGULAR -> addRegular(builder, rule.terms());
                    case ONE_WAY -> addDirectional(builder, rule.input(), rule.terms());
                    case ALTERNATIVE_CORRECTION_1, ALTERNATIVE_CORRECTION_2 ->
                            addCorrection(builder, warnings, rule);
                    case PLACEHOLDER -> {
                        unsupported.add(TurSNSynonymType.PLACEHOLDER);
                        warnings.add("Placeholder synonym '" + safe(rule.input())
                                + "' is not supported on Lucene and was skipped.");
                        yield false;
                    }
                };
                if (contributed) {
                    applied++;
                }
            }
        }

        SynonymMap map = null;
        if (applied > 0) {
            try {
                map = builder.build();
            } catch (java.io.IOException e) {
                // Building an in-memory FST never does real I/O; treat as "no map".
                warnings.add("Failed to build Lucene synonym map: " + e.getMessage());
            }
        }
        return new TurLuceneSynonymMap(map, applied, unsupported, warnings);
    }

    private static boolean addRegular(SynonymMap.Builder builder, List<String> terms) {
        List<String> clean = clean(terms);
        if (clean.size() < 2) {
            return false;
        }
        // Fully-equivalent set: every term expands to every other (both ways).
        for (String from : clean) {
            for (String to : clean) {
                if (!from.equals(to)) {
                    builder.add(charsRef(from), charsRef(to), true);
                }
            }
        }
        return true;
    }

    private static boolean addDirectional(SynonymMap.Builder builder, String input, List<String> terms) {
        String key = input == null ? null : input.trim().toLowerCase(Locale.ROOT);
        List<String> clean = clean(terms);
        if (key == null || key.isEmpty() || clean.isEmpty()) {
            return false;
        }
        boolean any = false;
        for (String to : clean) {
            if (!to.equals(key)) {
                builder.add(charsRef(key), charsRef(to), true);
                any = true;
            }
        }
        return any;
    }

    private static boolean addCorrection(SynonymMap.Builder builder, List<String> warnings,
            TurSESynonymRule rule) {
        boolean contributed = addDirectional(builder, rule.input(), rule.terms());
        if (contributed) {
            warnings.add("Correction '" + safe(rule.input())
                    + "' degraded to a one-way synonym (Lucene has no per-rule typo budget).");
        }
        return contributed;
    }

    /** Builds a Lucene {@link CharsRef} for a (possibly multi-word) phrase. */
    private static CharsRef charsRef(String phrase) {
        String[] words = phrase.split("\\s+");
        if (words.length == 1) {
            return new CharsRef(phrase);
        }
        return SynonymMap.Builder.join(words, new CharsRefBuilder());
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
            String norm = term.trim().toLowerCase(Locale.ROOT);
            if (!norm.isEmpty() && !out.contains(norm)) {
                out.add(norm);
            }
        }
        return out;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
