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
package com.viglet.turing.sn.kb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T671 / §XL (Block AQ) — an immutable, compiled recognition dictionary for one
 * (site, locale): a folded-text Aho-Corasick automaton over every surface form
 * (preferred labels + variations + {@code USE}/{@code USED_FOR} synonyms) mapping
 * to the {@link TurRecognizedTerm} canonical term. {@link #recognize(String)}
 * finds every mentioned term in a single pass, enforcing word boundaries and
 * re-verifying each variation's {@code case}/{@code accent} flags against the
 * original text.
 *
 * <p>Build it with the {@link Builder}; the compiled instance is cached per
 * (site, locale) by {@link TurMicrothesaurusDictionaryService} and is safe to
 * share (read-only).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurRecognitionDictionary {

    /** Pattern payload: the original surface form + its match flags + resolved term. */
    private record SurfacePattern(String surfaceForm, boolean caseSensitive,
            boolean accentSensitive, TurRecognizedTerm term) {
    }

    private final TurAhoCorasick<SurfacePattern> automaton;
    private final int surfaceFormCount;

    private TurRecognitionDictionary(TurAhoCorasick<SurfacePattern> automaton, int surfaceFormCount) {
        this.automaton = automaton;
        this.surfaceFormCount = surfaceFormCount;
    }

    public boolean isEmpty() {
        return surfaceFormCount == 0;
    }

    public int surfaceFormCount() {
        return surfaceFormCount;
    }

    /**
     * Returns every canonical term recognised in {@code text}, de-duplicated by
     * term id (first hit wins), preserving discovery order.
     */
    public Collection<TurRecognizedTerm> recognize(String text) {
        Map<String, TurRecognizedTerm> byId = new LinkedHashMap<>();
        if (text == null || text.isEmpty() || surfaceFormCount == 0) {
            return byId.values();
        }
        String folded = TurTextNormalizer.fold(text);
        for (TurAhoCorasick.Hit<SurfacePattern> hit : automaton.search(folded)) {
            int start = hit.start();
            int end = hit.end();
            if (!wordBoundary(text, start, end)) {
                continue;
            }
            String slice = text.substring(start, end);
            SurfacePattern pattern = hit.payload();
            if (matchesFlags(slice, pattern)) {
                byId.putIfAbsent(pattern.term().termId(), pattern.term());
            }
        }
        return byId.values();
    }

    /** True when the [start, end) span is not glued to an adjacent word character. */
    private static boolean wordBoundary(String text, int start, int end) {
        boolean leftOk = start == 0 || !TurTextNormalizer.isWordChar(text.charAt(start - 1));
        boolean rightOk = end == text.length() || !TurTextNormalizer.isWordChar(text.charAt(end));
        return leftOk && rightOk;
    }

    /** Re-verifies the matched slice against the variation's case/accent flags. */
    private static boolean matchesFlags(String slice, SurfacePattern pattern) {
        String surface = pattern.surfaceForm();
        boolean cs = pattern.caseSensitive();
        boolean as = pattern.accentSensitive();
        if (as && cs) {
            return slice.equals(surface);
        }
        if (as) {
            return slice.equalsIgnoreCase(surface);
        }
        if (cs) {
            return TurTextNormalizer.foldAccentsKeepCase(slice)
                    .equals(TurTextNormalizer.foldAccentsKeepCase(surface));
        }
        // accent-insensitive + case-insensitive: the automaton already matched the full fold.
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Accumulates surface forms then compiles the automaton once. */
    public static final class Builder {
        private final TurAhoCorasick<SurfacePattern> automaton = new TurAhoCorasick<>();
        private final List<String> added = new ArrayList<>();

        /**
         * Registers a surface form. Blank forms are ignored; the folded form is
         * what the automaton matches on (so accent/case-insensitive matching is
         * the baseline and the flags only tighten it).
         */
        public Builder add(String surfaceForm, boolean caseSensitive, boolean accentSensitive,
                TurRecognizedTerm term) {
            if (surfaceForm == null || surfaceForm.isBlank() || term == null) {
                return this;
            }
            String trimmed = surfaceForm.trim();
            String folded = TurTextNormalizer.fold(trimmed);
            if (folded.isEmpty()) {
                return this;
            }
            automaton.add(folded, new SurfacePattern(trimmed, caseSensitive, accentSensitive, term));
            added.add(folded);
            return this;
        }

        public TurRecognitionDictionary build() {
            automaton.build();
            return new TurRecognitionDictionary(automaton, added.size());
        }
    }
}
