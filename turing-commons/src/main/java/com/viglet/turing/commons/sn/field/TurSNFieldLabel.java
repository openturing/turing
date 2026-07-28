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
package com.viglet.turing.commons.sn.field;

/**
 * T809 / §LVI.2 (Block BH) — last-resort display label for an SN field whose
 * human label was never configured.
 *
 * <p>A facet group header resolves to the locale-matched
 * {@code TurSNSiteFieldExtFacet} label, else the field's {@code facetName}. A
 * field created by the auto-create paths (crawl / structured feed) has both
 * null, so the header used to render blank even though the facet itself was
 * populated. {@link #humanize(String)} turns the raw field name into a readable
 * label ({@code pricing_inputPer1M} → {@code "Pricing Input Per 1M"}) so the
 * header always says <em>something</em>. Configured labels always win — this is
 * only the fallback.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNFieldLabel {

    /**
     * Word boundaries used to split a field name: lowercase→uppercase
     * ({@code contextWindow}), an acronym run followed by a word
     * ({@code URLField}) and letter→digit ({@code per1M}). A digit followed by
     * letters is deliberately <em>not</em> a boundary so units stay glued
     * ({@code 1M}).
     */
    private static final String WORD_BOUNDARY =
            "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[a-zA-Z])(?=[0-9])";

    private TurSNFieldLabel() {
        throw new IllegalStateException("Semantic Navigation Field Label utility class");
    }

    /**
     * Returns {@code label} when it carries text, otherwise the humanized
     * {@code fieldName}. The single call shape used by the facet renderers.
     */
    public static String orHumanized(String label, String fieldName) {
        return hasText(label) ? label : humanize(fieldName);
    }

    /**
     * Turns a raw field name into a readable label: {@code _}/{@code -}/{@code .}
     * become spaces, camelCase and acronym runs are split into words, and each
     * word is capitalized without lowercasing the rest (so {@code URL} stays
     * {@code URL} and {@code 1M} stays {@code 1M}).
     *
     * <p>Returns {@code null} for a null/blank name — a caller with nothing to
     * humanize should not get an empty string that looks like a real label.
     */
    public static String humanize(String fieldName) {
        if (!hasText(fieldName)) {
            return null;
        }
        String spaced = fieldName.trim()
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ');
        StringBuilder label = new StringBuilder();
        for (String chunk : spaced.split("\\s+")) {
            for (String word : chunk.split(WORD_BOUNDARY)) {
                if (word.isEmpty()) {
                    continue;
                }
                if (!label.isEmpty()) {
                    label.append(' ');
                }
                label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return label.isEmpty() ? null : label.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
