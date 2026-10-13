/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.nl;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a natural-language request into the filters it names, as data (T1085).
 *
 * <p>The one extractor the NL features read: the {@link TurNLFacetParser} maps the
 * text onto the site's field schema, and this service turns that parse into
 * {@link TurSNNLFilters}. Every value is checked against the values the site
 * actually holds, read with one {@code terms} aggregation per field, and spelled
 * the way the site indexes it ({@code sao paulo} becomes {@code São Paulo}); a
 * value the site does not hold is dropped and reported, never sent as a filter
 * that matches nothing. Each filter carries the words of the text it was read
 * from, and a confidence of 1 when those words are the value itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSNNLFilterService {

    static final String TEXT_FIELD = "_text_";
    static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    static final String NOT_IN_SITE = "NOT_IN_SITE";
    static final double LITERAL = 1.0;
    static final double INFERRED = 0.6;
    static final double UNCHECKED = 0.3;
    private static final int GROUNDING_BUCKETS = 1000;
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurNLFacetParser parser;
    private final TurDslSearchService dslSearchService;

    public TurSNNLFilterService(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository, TurNLFacetParser parser,
            TurDslSearchService dslSearchService) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.parser = parser;
        this.dslSearchService = dslSearchService;
    }

    /**
     * @param locale the search locale; {@code null} means the site's first locale
     * @throws IllegalArgumentException when {@code text} is blank
     * @throws TurNotFoundException     when the site does not exist
     */
    @Transactional(readOnly = true)
    public TurSNNLFilters parse(String siteName, String locale, String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("'text' is required");
        }
        TurSNSite site = turSNSiteRepository.findByNameIgnoreCase(siteName)
                .orElseThrow(() -> new TurNotFoundException("SN site not found: " + siteName));
        if (!parser.isAvailable()) {
            return TurSNNLFilters.unavailable(text);
        }
        Map<String, TurNLFacetField> schema = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1)
                .stream().map(TurNLFacetField::of)
                .collect(Collectors.toMap(TurNLFacetField::name, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        String searchLocale = StringUtils.hasText(locale) ? locale : firstLocale(site);

        TurDslQueryRequest parsed;
        try {
            parsed = parser.parse(new TurNLFacetParser.ParseRequest(site.getName(), searchLocale, text,
                    List.copyOf(schema.values())));
        } catch (TurNLFacetParser.TurNLFacetParseException e) {
            log.warn("[T1085] Could not parse '{}' for site '{}': {}", text, siteName, e.getMessage());
            return new TurSNNLFilters(true, text, text, List.of(), List.of(), List.of());
        }
        return ground(site.getName(), searchLocale, text, schema, parsed);
    }

    /**
     * Grounds a parse another caller already made (T1085): the catalog copilot's
     * planners parse with their own schema and passes, and send the result through
     * here so their filters are checked against the site's values like any other.
     *
     * @param schema the schema the parse was made against
     */
    @Transactional(readOnly = true)
    public TurSNNLFilters ground(String siteName, String locale, String text, List<TurNLFacetField> schema,
            TurDslQueryRequest parsed) {
        Map<String, TurNLFacetField> bySchema = new LinkedHashMap<>();
        if (schema != null) {
            schema.forEach(field -> bySchema.putIfAbsent(field.name(), field));
        }
        return ground(siteName, locale, text, bySchema, parsed);
    }

    private TurSNNLFilters ground(String siteName, String searchLocale, String text,
            Map<String, TurNLFacetField> schema, TurDslQueryRequest parsed) {
        Parse flat = flatten(parsed == null ? null : parsed.query());
        List<TurSNNLFilters.Dropped> dropped = new ArrayList<>();
        Map<String, Set<String>> siteValues = siteValues(siteName, searchLocale,
                termFieldsToGround(flat, schema));
        List<TurSNNLFilters.Filter> filters = new ArrayList<>();
        flat.terms().forEach((field, values) -> termFilter(field, values, schema, siteValues, text, dropped)
                .ifPresent(filters::add));
        flat.ranges().forEach((field, range) -> rangeFilter(field, range, schema, text, dropped)
                .ifPresent(filters::add));

        String q = StringUtils.hasText(flat.freeText()) ? flat.freeText().trim() : leftover(text, filters);
        return new TurSNNLFilters(true, text, q, filters, fq(filters), dropped);
    }

    private String firstLocale(TurSNSite site) {
        return turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(site).stream()
                .map(TurSNSiteLocale::getLanguage)
                .filter(java.util.Objects::nonNull)
                .map(Locale::toString)
                .findFirst().orElse(Locale.US.toString());
    }

    // ── Flattening the parser's DSL ─────────────────────────────────────────

    /** What the parse names: term values and ranges per field, and the free text. */
    record Parse(Map<String, List<String>> terms, Map<String, String[]> ranges, String freeText) {
    }

    static Parse flatten(TurDslQuery query) {
        Map<String, List<String>> terms = new LinkedHashMap<>();
        Map<String, String[]> ranges = new LinkedHashMap<>();
        StringBuilder freeText = new StringBuilder();
        walk(query, terms, ranges, freeText);
        return new Parse(terms, ranges, freeText.toString());
    }

    private static void walk(TurDslQuery query, Map<String, List<String>> terms, Map<String, String[]> ranges,
            StringBuilder freeText) {
        switch (query) {
            case null -> {
                // nothing parsed
            }
            case TurDslQuery.Bool bool -> {
                // mustNot is an exclusion, which a filter query of this shape cannot carry
                for (List<TurDslQuery> clauses : List.of(nullSafe(bool.filter()), nullSafe(bool.must()),
                        nullSafe(bool.should()))) {
                    clauses.forEach(clause -> walk(clause, terms, ranges, freeText));
                }
            }
            case TurDslQuery.ConstantScore constantScore -> walk(constantScore.filter(), terms, ranges, freeText);
            case TurDslQuery.Term term -> addTerm(terms, term.field(), term.value());
            case TurDslQuery.Terms many -> nullSafe(many.values()).forEach(v -> addTerm(terms, many.field(), v));
            case TurDslQuery.Range range -> ranges.put(range.field(), new String[] {
                    str(range.gte() != null ? range.gte() : range.gt()),
                    str(range.lte() != null ? range.lte() : range.lt()) });
            case TurDslQuery.Match match -> textOrTerm(match.field(), match.query(), terms, freeText);
            case TurDslQuery.MatchPhrase phrase -> textOrTerm(phrase.field(), phrase.query(), terms, freeText);
            default -> log.debug("[T1085] Ignoring a {} clause in an NL parse", query.getClass().getSimpleName());
        }
    }

    private static void textOrTerm(String field, String value, Map<String, List<String>> terms,
            StringBuilder freeText) {
        if (field == null || TEXT_FIELD.equals(field)) {
            if (StringUtils.hasText(value)) {
                freeText.append(freeText.isEmpty() ? "" : " ").append(value.trim());
            }
        } else {
            addTerm(terms, field, value);
        }
    }

    private static void addTerm(Map<String, List<String>> terms, String field, Object value) {
        String text = str(value);
        if (field != null && text != null) {
            List<String> values = terms.computeIfAbsent(field, k -> new ArrayList<>());
            if (!values.contains(text)) {
                values.add(text);
            }
        }
    }

    // ── Grounding in the site's values ──────────────────────────────────────

    /** Facet fields hold a list of values to check against; other fields pass unchecked. */
    private static List<String> termFieldsToGround(Parse flat, Map<String, TurNLFacetField> schema) {
        return flat.terms().keySet().stream()
                .filter(field -> schema.containsKey(field) && schema.get(field).facet()
                        && !schema.get(field).numeric())
                .toList();
    }

    // ── Back to a query ─────────────────────────────────────────────────────

    /**
     * The grounded filters as a DSL query for the copilot's search: each value
     * filter a term (or terms) filter, a full-text field's value a match, each
     * range a range filter, and {@code q} a match on the full text. The parse's
     * exclusions carry over unchanged, since grounding has nothing to say about
     * what to leave out.
     */
    public static TurDslQuery toQuery(TurSNNLFilters filters, List<TurNLFacetField> schema,
            TurDslQuery parsedQuery) {
        Map<String, TurNLFacetField> bySchema = new LinkedHashMap<>();
        if (schema != null) {
            schema.forEach(field -> bySchema.putIfAbsent(field.name(), field));
        }
        List<TurDslQuery> must = new ArrayList<>();
        List<TurDslQuery> filter = new ArrayList<>();
        for (TurSNNLFilters.Filter f : filters.filters()) {
            TurNLFacetField field = bySchema.get(f.field());
            if (f.isRange()) {
                filter.add(new TurDslQuery.Range(f.field(), bound(f.rangeStart()), null, bound(f.rangeEnd()), null));
            } else if (field != null && field.type() == com.viglet.turing.commons.se.field.TurSEFieldType.TEXT) {
                f.values().forEach(value -> must.add(new TurDslQuery.Match(f.field(), value, null)));
            } else if (f.values().size() == 1) {
                filter.add(new TurDslQuery.Term(f.field(), f.values().get(0)));
            } else {
                filter.add(new TurDslQuery.Terms(f.field(), List.copyOf(f.values())));
            }
        }
        if (StringUtils.hasText(filters.q())) {
            must.add(new TurDslQuery.Match(TEXT_FIELD, filters.q(), null));
        }
        List<TurDslQuery> mustNot = parsedQuery instanceof TurDslQuery.Bool bool && bool.mustNot() != null
                ? bool.mustNot() : List.of();
        if (must.isEmpty() && filter.isEmpty() && mustNot.isEmpty()) {
            return new TurDslQuery.MatchAll();
        }
        return new TurDslQuery.Bool(must, List.of(), mustNot, filter, null);
    }

    /** A range bound as the number it spells, so every engine compares it as one. */
    private static Object bound(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException notLong) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException notNumber) {
                return value;
            }
        }
    }

    /**
     * The values each field holds, by its normalized form, read with one search of
     * {@code terms} aggregations. A field the engine answers no buckets for is
     * absent, and its values pass unchecked.
     */
    private Map<String, Set<String>> siteValues(String siteName, String locale, List<String> fields) {
        if (fields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> aggs = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            aggs.put("f" + i, Map.of("terms", Map.of("field", fields.get(i), "size", GROUNDING_BUCKETS)));
        }
        Map<String, Set<String>> values = new LinkedHashMap<>();
        try {
            TurDslQueryRequest request = MAPPER.convertValue(
                    Map.of("query", Map.of("match_all", Map.of()), "size", 0, "aggs", aggs),
                    TurDslQueryRequest.class);
            Optional<TurDslSearchResponse> response = dslSearchService.search(siteName, locale, request);
            Map<String, TurDslSearchResponse.AggregationResult> results = response
                    .map(TurDslSearchResponse::aggregations).orElse(Map.of());
            for (int i = 0; i < fields.size(); i++) {
                TurDslSearchResponse.AggregationResult result = results == null ? null : results.get("f" + i);
                if (result != null && result.buckets() != null && !result.buckets().isEmpty()) {
                    values.put(fields.get(i), result.buckets().stream()
                            .map(TurDslSearchResponse.Bucket::key)
                            .collect(Collectors.toCollection(LinkedHashSet::new)));
                }
            }
        } catch (RuntimeException e) {
            log.warn("[T1085] Could not read the values of {} on site '{}', filters pass unchecked: {}",
                    fields, siteName, e.getMessage());
        }
        return values;
    }

    private static Optional<TurSNNLFilters.Filter> termFilter(String field, List<String> proposed,
            Map<String, TurNLFacetField> schema, Map<String, Set<String>> siteValues, String text,
            List<TurSNNLFilters.Dropped> dropped) {
        if (!schema.containsKey(field)) {
            proposed.forEach(v -> dropped.add(new TurSNNLFilters.Dropped(field, v, UNKNOWN_FIELD)));
            return Optional.empty();
        }
        Set<String> held = siteValues.get(field);
        boolean grounded = held != null;
        List<String> values = new ArrayList<>();
        List<String> sourceWords = new ArrayList<>();
        double confidence = LITERAL;
        for (String value : proposed) {
            String canonical = grounded ? canonical(value, held) : value;
            if (canonical == null) {
                dropped.add(new TurSNNLFilters.Dropped(field, value, NOT_IN_SITE));
                continue;
            }
            if (!values.contains(canonical)) {
                values.add(canonical);
            }
            Optional<String> words = sourceWords(text, canonical).or(() -> sourceWords(text, value));
            words.ifPresent(sourceWords::add);
            confidence = Math.min(confidence, valueConfidence(grounded, words.isPresent()));
        }
        return values.isEmpty() ? Optional.empty()
                : Optional.of(new TurSNNLFilters.Filter(field, values, null, null, sourceWords, confidence,
                        grounded));
    }

    private static double valueConfidence(boolean grounded, boolean spelledInText) {
        if (!grounded) {
            return UNCHECKED;
        }
        return spelledInText ? LITERAL : INFERRED;
    }

    private static Optional<TurSNNLFilters.Filter> rangeFilter(String field, String[] range,
            Map<String, TurNLFacetField> schema, String text, List<TurSNNLFilters.Dropped> dropped) {
        if (!schema.containsKey(field)) {
            dropped.add(new TurSNNLFilters.Dropped(field, "[" + range[0] + " TO " + range[1] + "]",
                    UNKNOWN_FIELD));
            return Optional.empty();
        }
        if (range[0] == null && range[1] == null) {
            return Optional.empty();
        }
        List<String> sourceWords = new ArrayList<>();
        boolean literal = true;
        for (String bound : range) {
            if (bound != null) {
                Optional<String> words = sourceWords(text, bound);
                words.ifPresent(sourceWords::add);
                literal &= words.isPresent();
            }
        }
        return Optional.of(new TurSNNLFilters.Filter(field, List.of(), range[0], range[1], sourceWords,
                literal ? LITERAL : INFERRED, false));
    }

    /** The site's own spelling of a value, matched without case or accents; {@code null} when it holds none. */
    static String canonical(String value, Set<String> held) {
        if (held.contains(value)) {
            return value;
        }
        String wanted = normalize(value);
        return held.stream().filter(candidate -> normalize(candidate).equals(wanted)).findFirst().orElse(null);
    }

    // ── Source words and the leftover query ─────────────────────────────────

    /**
     * The words of {@code text} that spell {@code value}, compared without case or
     * accents and on word boundaries, as the text spells them.
     */
    static Optional<String> sourceWords(String text, String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        Folded folded = Folded.of(text);
        String wanted = normalize(value);
        int from = 0;
        while (from <= folded.text().length() - wanted.length()) {
            int at = folded.text().indexOf(wanted, from);
            if (at < 0) {
                return Optional.empty();
            }
            int end = at + wanted.length();
            if (boundary(folded.text(), at - 1) && boundary(folded.text(), end)) {
                return Optional.of(text.substring(folded.origin()[at], folded.origin()[end - 1] + 1));
            }
            from = at + 1;
        }
        return Optional.empty();
    }

    /** The text with every filter's source words taken out. */
    static String leftover(String text, List<TurSNNLFilters.Filter> filters) {
        String rest = text;
        for (TurSNNLFilters.Filter filter : filters) {
            for (String words : filter.sourceWords()) {
                rest = rest.replace(words, " ");
            }
        }
        return rest.replaceAll("\\s+", " ").trim();
    }

    private static boolean boundary(String text, int index) {
        return index < 0 || index >= text.length() || !Character.isLetterOrDigit(text.charAt(index));
    }

    static List<String> fq(List<TurSNNLFilters.Filter> filters) {
        List<String> fq = new ArrayList<>();
        for (TurSNNLFilters.Filter filter : filters) {
            if (filter.isRange()) {
                fq.add(filter.field() + ":[" + orStar(filter.rangeStart()) + " TO " + orStar(filter.rangeEnd())
                        + "]");
            } else {
                filter.values().forEach(value -> fq.add(filter.field() + ":" + value));
            }
        }
        return fq;
    }

    private static String orStar(String bound) {
        return bound == null ? "*" : bound;
    }

    static String normalize(String value) {
        return Folded.of(value).text().trim();
    }

    /** Lower case without accents, with each folded character's index in the original. */
    private static final class Folded {
        private final String text;
        private final int[] origin;

        private Folded(String text, int[] origin) {
            this.text = text;
            this.origin = origin;
        }

        String text() {
            return text;
        }

        int[] origin() {
            return origin;
        }

        static Folded of(String original) {
            StringBuilder folded = new StringBuilder();
            List<Integer> origin = new ArrayList<>();
            for (int i = 0; i < original.length(); i++) {
                String decomposed = Normalizer.normalize(String.valueOf(original.charAt(i)), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
                for (int j = 0; j < decomposed.length(); j++) {
                    folded.append(decomposed.charAt(j));
                    origin.add(i);
                }
            }
            return new Folded(folded.toString(), origin.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
