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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymDto;
import com.viglet.turing.persistence.model.sn.synonym.TurSNSynonymType;

/**
 * T666 / §XXXIX (Block AP) — pure mapping of the raw Algolia synonym objects
 * fetched by {@code TurAlgoliaClient.fetchSynonyms} onto Turing
 * {@link TurSNSynonymDto}s, closing the migration loop the T658 importer could
 * only surface. Algolia's five synonym types map 1:1 onto Turing's:
 *
 * <ul>
 *   <li>{@code synonym} (or no {@code type}) → REGULAR ({@code synonyms}).</li>
 *   <li>{@code oneWaySynonym} → ONE_WAY ({@code input} + {@code synonyms}).</li>
 *   <li>{@code altCorrection1} → ALTERNATIVE_CORRECTION_1 ({@code word} + {@code corrections}).</li>
 *   <li>{@code altCorrection2} → ALTERNATIVE_CORRECTION_2 ({@code word} + {@code corrections}).</li>
 *   <li>{@code placeholder} → PLACEHOLDER ({@code placeholder} + {@code replacements}).</li>
 * </ul>
 *
 * Malformed entries (missing the terms a type requires) are skipped rather than
 * producing an invalid rule.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurAlgoliaSynonymMapper {

    private TurAlgoliaSynonymMapper() {
    }

    public static List<TurSNSynonymDto> toDtos(List<Map<String, Object>> algoliaSynonyms, Locale locale) {
        List<TurSNSynonymDto> out = new ArrayList<>();
        if (algoliaSynonyms == null) {
            return out;
        }
        for (Map<String, Object> raw : algoliaSynonyms) {
            if (raw != null) {
                toDto(raw, locale).ifPresent(out::add);
            }
        }
        return out;
    }

    private static java.util.Optional<TurSNSynonymDto> toDto(Map<String, Object> raw, Locale locale) {
        String type = str(raw.get("type"));
        String name = str(raw.get("objectID"));
        return switch (type == null ? "synonym" : type) {
            case "synonym" -> dto(name, TurSNSynonymType.REGULAR, locale, null, strings(raw.get("synonyms")));
            case "oneWaySynonym" -> dto(name, TurSNSynonymType.ONE_WAY, locale,
                    str(raw.get("input")), strings(raw.get("synonyms")));
            case "altCorrection1" -> dto(name, TurSNSynonymType.ALTERNATIVE_CORRECTION_1, locale,
                    str(raw.get("word")), strings(raw.get("corrections")));
            case "altCorrection2" -> dto(name, TurSNSynonymType.ALTERNATIVE_CORRECTION_2, locale,
                    str(raw.get("word")), strings(raw.get("corrections")));
            case "placeholder" -> dto(name, TurSNSynonymType.PLACEHOLDER, locale,
                    str(raw.get("placeholder")), strings(raw.get("replacements")));
            default -> java.util.Optional.empty();
        };
    }

    private static java.util.Optional<TurSNSynonymDto> dto(String name, TurSNSynonymType type, Locale locale,
            String input, List<String> terms) {
        // A regular rule needs >=2 terms; every directional rule needs an input and >=1 term.
        boolean valid = type == TurSNSynonymType.REGULAR
                ? terms.size() >= 2
                : input != null && !input.isBlank() && !terms.isEmpty();
        if (!valid) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new TurSNSynonymDto(null, name, type, locale, input, terms, true));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String s = item.toString().trim();
                    if (!s.isEmpty()) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }
}
