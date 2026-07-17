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
package com.viglet.turing.lucene;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortOrderEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.lucene.TurLuceneSynonymAnalyzer;
import com.viglet.turing.plugins.se.lucene.TurLuceneSynonymRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds Lucene {@link Query} objects from {@link TurSNSiteSearchContext} and
 * {@link TurSEParameters}, including filter queries (for facet selections) and
 * returns the list of facet-enabled fields.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class TurLuceneQueryBuilder {

    private static final Set<TurSEFieldType> TEXT_FIELD_TYPES = EnumSet.of(TurSEFieldType.TEXT, TurSEFieldType.STRING,
            TurSEFieldType.ARRAY);

    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurLuceneSynonymRegistry synonymRegistry;

    public TurLuceneQueryBuilder(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurLuceneSynonymRegistry synonymRegistry) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteCustomSortRepository = turSNSiteCustomSortRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.synonymRegistry = synonymRegistry;
    }

    // -------------------------------------------------------------------------
    // Main query
    // -------------------------------------------------------------------------

    /**
     * Builds the primary Lucene {@link Query} from the search context.
     * Includes the user's query string and any active filter queries.
     */
    public Query buildQuery(TurSNSite turSNSite, TurSEParameters params) {
        return buildQuery(turSNSite, params, null);
    }

    /**
     * T665 — same as {@link #buildQuery(TurSNSite, TurSEParameters)} but with the
     * search {@code locale}, so the main query is analyzed with the site's
     * query-time synonym map (when one is registered for that locale's core).
     */
    public Query buildQuery(TurSNSite turSNSite, TurSEParameters params, Locale locale) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Main user query
        Query mainQuery = buildMainQuery(params.getQuery(), turSNSite, locale);
        builder.add(mainQuery, BooleanClause.Occur.MUST);

        // Filter queries (facet selections)
        List<String> filterQueries = params.getTurSNFilterParams().getDefaultValues();
        if (!CollectionUtils.isEmpty(filterQueries)) {
            addFilterQueries(builder, turSNSite, filterQueries);
        }

        return builder.build();
    }

    /**
     * Adds the active facet selections as filter clauses, following standard
     * faceted-navigation semantics:
     * <ul>
     *   <li>selections are grouped by field;</li>
     *   <li><b>within a field</b>, multiple values union (<b>OR</b>) when the
     *       field is multi-select ({@code facetItemType == OR}), otherwise they
     *       intersect (<b>AND</b>);</li>
     *   <li><b>across fields</b>, each field group intersects (<b>AND</b>) — a
     *       selection in one field narrows the result set of another.</li>
     * </ul>
     * The previous implementation added every value as its own {@code FILTER}
     * (AND) clause, so selecting two values of the same multi-select facet (e.g.
     * two mutually-exclusive categories) yielded zero hits even though the facet
     * counts promised a union. Cross-field union ({@code facetType == OR}) is
     * intentionally not honoured: it broadens instead of narrowing and breaks
     * the expected "pick another field to drill down" behaviour.
     */
    private void addFilterQueries(BooleanQuery.Builder builder, TurSNSite turSNSite,
            List<String> filterQueries) {
        Map<String, List<String>> valuesByField = new LinkedHashMap<>();
        filterQueries.forEach(fq -> parseFieldValue(fq).ifPresent(fv -> valuesByField
                .computeIfAbsent(fv.field(), k -> new ArrayList<>()).add(fv.value())));
        if (valuesByField.isEmpty()) {
            return;
        }

        Map<String, TurSNSiteFieldExt> fieldsByName = enabledFieldsByName(turSNSite);
        valuesByField.forEach((field, values) -> builder.add(
                buildFieldFilterQuery(field, values,
                        effectiveFacetItemType(fieldsByName.get(field), turSNSite)),
                BooleanClause.Occur.FILTER));
    }

    /**
     * Combines the selected values of a single field into one query: OR
     * (SHOULD, min-should-match 1) for a multi-select facet, AND (MUST)
     * otherwise. A single value collapses to a plain {@link TermQuery}.
     */
    private static Query buildFieldFilterQuery(String field, List<String> values,
            TurSNSiteFacetFieldEnum itemType) {
        if (values.size() == 1) {
            return new TermQuery(new Term(field, values.getFirst()));
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        BooleanClause.Occur occur = itemType == TurSNSiteFacetFieldEnum.OR
                ? BooleanClause.Occur.SHOULD
                : BooleanClause.Occur.MUST;
        values.forEach(value -> builder.add(new TermQuery(new Term(field, value)), occur));
        if (occur == BooleanClause.Occur.SHOULD) {
            builder.setMinimumNumberShouldMatch(1);
        }
        return builder.build();
    }

    /** Enabled fields keyed by name, for resolving a selection's facet type. */
    private Map<String, TurSNSiteFieldExt> enabledFieldsByName(TurSNSite turSNSite) {
        Map<String, TurSNSiteFieldExt> map = new LinkedHashMap<>();
        turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1)
                .forEach(ext -> {
                    if (ext.getName() != null) {
                        map.putIfAbsent(ext.getName(), ext);
                    }
                });
        return map;
    }

    /** Field-level {@code facetItemType} wins over the site default; DEFAULT/null → AND. */
    private static TurSNSiteFacetFieldEnum effectiveFacetItemType(TurSNSiteFieldExt ext,
            TurSNSite turSNSite) {
        TurSNSiteFacetFieldEnum fieldType = ext != null ? ext.getFacetItemType() : null;
        if (fieldType != null && fieldType != TurSNSiteFacetFieldEnum.DEFAULT) {
            return fieldType;
        }
        return normalize(turSNSite.getFacetItemType());
    }

    private static TurSNSiteFacetFieldEnum normalize(TurSNSiteFacetFieldEnum value) {
        return (value == null || value == TurSNSiteFacetFieldEnum.DEFAULT)
                ? TurSNSiteFacetFieldEnum.AND
                : value;
    }

    /**
     * Returns the list of enabled facet fields for the given site.
     */
    public List<TurSNSiteFieldExt> getFacetFields(TurSNSite turSNSite) {
        List<TurSNSiteFieldExt> all = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1);
        List<TurSNSiteFieldExt> facets = all.stream()
                .filter(ext -> ext.getFacet() == 1)
                .sorted(java.util.Comparator.comparingInt(
                        ext -> ext.getFacetPosition() != null ? ext.getFacetPosition() : Integer.MAX_VALUE))
                .toList();
        log.debug("[Lucene] site='{}' totalEnabledFields={} facetFields={} names={}",
                turSNSite.getName(), all.size(), facets.size(),
                facets.stream().map(TurSNSiteFieldExt::getName).toList());
        return facets;
    }

    /**
     * Returns facet fields limited to a single named facet (used by
     * {@code retrieveFacetResults}).
     */
    public List<TurSNSiteFieldExt> getFacetFields(TurSNSite turSNSite, String facetName) {
        return getFacetFields(turSNSite).stream()
                .filter(ext -> facetName.equals(ext.getName()))
                .toList();
    }

    /**
     * Returns the list of enabled highlight fields for the given site.
     */
    public List<TurSNSiteFieldExt> getHLFields(TurSNSite turSNSite) {
        // T707 — never highlight identifier/URL fields even if a field is
        // (mis)configured with hl=1: a <mark> injected into `id` corrupts the
        // value clients feed back to /search/similar, silently breaking "Related".
        return turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(turSNSite, 1, 1)
                .stream()
                .filter(field -> !com.viglet.turing.commons.sn.field.TurSNFieldName
                        .isNonHighlightable(field.getName()))
                .toList();
    }

    /**
     * Builds a Lucene {@link org.apache.lucene.search.Sort} from the search parameters,
     * mirroring the Solr sort logic (relevance, newest, oldest, field:asc/desc, custom sort).
     *
     * @return null for relevance (default score sort), or a Sort object
     */
    public org.apache.lucene.search.Sort buildSort(TurSNSite turSNSite, TurSEParameters params) {
        String sortParam = params.getSort();
        if (sortParam == null || sortParam.isBlank() || sortParam.equalsIgnoreCase("relevance")) {
            return null; // default score sort
        }

        if (sortParam.equalsIgnoreCase(TurLuceneConstants.NEWEST)) {
            String dateField = turSNSite.getDefaultDateField();
            if (dateField != null) {
                return new org.apache.lucene.search.Sort(
                        new org.apache.lucene.search.SortField(dateField,
                                org.apache.lucene.search.SortField.Type.LONG, true));
            }
            return null;
        }

        if (sortParam.equalsIgnoreCase(TurLuceneConstants.OLDEST)) {
            String dateField = turSNSite.getDefaultDateField();
            if (dateField != null) {
                return new org.apache.lucene.search.Sort(
                        new org.apache.lucene.search.SortField(dateField,
                                org.apache.lucene.search.SortField.Type.LONG, false));
            }
            return null;
        }

        // field:asc or field:desc
        var kv = TurCommonsUtils.getKeyValueFromColon(sortParam);
        if (kv.isPresent()) {
            boolean reverse = kv.get().getValue().equalsIgnoreCase("desc");
            return new org.apache.lucene.search.Sort(
                    buildSortField(kv.get().getKey(), reverse));
        }

        // Custom sort name
        return turSNSiteCustomSortRepository.findByTurSNSiteAndName(turSNSite, sortParam)
                .map(customSort -> {
                    List<TurSNSiteCustomSortItem> sortedItems = customSort.getItems().stream()
                            .sorted(java.util.Comparator.comparing(TurSNSiteCustomSortItem::getPosition,
                                    java.util.Comparator.nullsLast(Integer::compareTo)))
                            .toList();
                    if (sortedItems.isEmpty()) return null;
                    org.apache.lucene.search.SortField[] fields = sortedItems.stream()
                            .map(item -> buildSortField(item.getFieldName(),
                                    item.getSortOrder() == TurSNSiteCustomSortOrderEnum.DESC))
                            .toArray(org.apache.lucene.search.SortField[]::new);
                    return new org.apache.lucene.search.Sort(fields);
                })
                .orElse(null);
    }

    private org.apache.lucene.search.SortField buildSortField(String fieldName, boolean reverse) {
        // Determine the SortField type based on the field's configured type
        List<TurSNSiteFieldExt> allFields = turSNSiteFieldExtRepository.findAll();
        for (TurSNSiteFieldExt ext : allFields) {
            if (fieldName.equals(ext.getName()) && ext.getType() != null) {
                return switch (ext.getType()) {
                    case INT -> new org.apache.lucene.search.SortField(fieldName,
                            org.apache.lucene.search.SortField.Type.INT, reverse);
                    case LONG, DATE -> new org.apache.lucene.search.SortField(fieldName,
                            org.apache.lucene.search.SortField.Type.LONG, reverse);
                    case FLOAT -> new org.apache.lucene.search.SortField(fieldName,
                            org.apache.lucene.search.SortField.Type.FLOAT, reverse);
                    case DOUBLE, CURRENCY -> new org.apache.lucene.search.SortField(fieldName,
                            org.apache.lucene.search.SortField.Type.DOUBLE, reverse);
                    // TEXT, STRING, ARRAY, BOOL use SortedSetDocValues (multi-value safe)
                    default -> new org.apache.lucene.search.SortedSetSortField(fieldName, reverse);
                };
            }
        }
        // Unknown field — use SortedSetSortField (works with SortedSetDocValuesField from addFacetField)
        return new org.apache.lucene.search.SortedSetSortField(fieldName, reverse);
    }

    /**
     * Builds an autocomplete query that searches across edge-ngram indexed
     * {@code *_ac} fields. The query is analyzed with {@link StandardAnalyzer}
     * (lowercase, tokenize) and matched against the ngram tokens in the index.
     */
    public Query buildAutoCompleteQuery(TurSNSite turSNSite, String term) {
        String[] textFields = resolveTextFields(turSNSite);
        // Map to _ac suffixed fields for ngram matching
        String[] acFields;
        if (textFields.length == 0) {
            acFields = new String[]{TurLuceneConstants.TITLE + TurLuceneConstants.AC_SUFFIX};
        } else {
            acFields = java.util.Arrays.stream(textFields)
                    .map(f -> f + TurLuceneConstants.AC_SUFFIX)
                    .toArray(String[]::new);
        }
        try {
            // Use StandardAnalyzer for the query side (lowercase + tokenize, no ngrams)
            MultiFieldQueryParser parser = new MultiFieldQueryParser(acFields, new StandardAnalyzer());
            parser.setDefaultOperator(QueryParser.Operator.OR);
            return parser.parse(QueryParserBase.escape(term));
        } catch (ParseException e) {
            log.warn("Failed to parse autocomplete query '{}': {}", term, e.getMessage());
            return new TermQuery(new Term(
                    TurLuceneConstants.TITLE + TurLuceneConstants.AC_SUFFIX, term.toLowerCase()));
        }
    }

    /**
     * Builds a wildcard-appended version of the query (for no-results fallback).
     */
    public Query buildWildcardQuery(TurSNSite turSNSite, TurSEParameters params) {
        String wildcardQuery = params.getQuery().trim() + "*";
        params.setQuery(wildcardQuery);
        return buildQuery(turSNSite, params);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private Query buildMainQuery(String queryStr, TurSNSite turSNSite, Locale locale) {
        if (!StringUtils.hasText(queryStr) || queryStr.equals("*") || queryStr.equals("*:*")) {
            return new FieldExistsQuery(TurLuceneConstants.ID);
        }

        String[] textFields = resolveTextFields(turSNSite);
        Analyzer analyzer = queryAnalyzer(turSNSite, locale);
        try {
            if (textFields.length == 0) {
                // Fallback: search the id field
                return new QueryParser(TurLuceneConstants.ID, analyzer).parse(
                        QueryParserBase.escape(queryStr));
            }
            MultiFieldQueryParser parser = new MultiFieldQueryParser(textFields, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            return parser.parse(queryStr);
        } catch (ParseException e) {
            log.warn("Failed to parse Lucene query '{}', falling back to MatchAllDocsQuery: {}", queryStr,
                    e.getMessage());
            return buildTermQueries(queryStr, textFields);
        }
    }

    /**
     * T665 — the query-time analyzer: the site's synonym-aware analyzer when a
     * {@link SynonymMap} is registered for the search locale's core, otherwise
     * the plain {@link StandardAnalyzer} (identical to pre-T665 behaviour). Zero
     * overhead for sites without synonyms — the registry short-circuits and no
     * locale/core lookup runs.
     */
    private Analyzer queryAnalyzer(TurSNSite turSNSite, Locale locale) {
        if (locale == null || synonymRegistry == null || synonymRegistry.isEmpty()) {
            return new StandardAnalyzer();
        }
        String core = turSNSiteLocaleRepository.findByTurSNSite(turSNSite).stream()
                .filter(l -> locale.equals(l.getLanguage()))
                .map(TurSNSiteLocale::getCore)
                .findFirst()
                .orElse(null);
        SynonymMap map = core == null ? null : synonymRegistry.get(core);
        return map == null ? new StandardAnalyzer() : new TurLuceneSynonymAnalyzer(map);
    }

    /** Fallback: build OR of TermQuery across text fields. */
    private Query buildTermQueries(String queryStr, String[] fields) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String field : fields) {
            for (String token : queryStr.toLowerCase().split("\\s+")) {
                builder.add(new TermQuery(new Term(field, token)), BooleanClause.Occur.SHOULD);
            }
        }
        return builder.build();
    }

    private String[] resolveTextFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1)
                .stream()
                .filter(ext -> ext.getType() != null && TEXT_FIELD_TYPES.contains(ext.getType()))
                .map(TurSNSiteFieldExt::getName)
                .distinct()
                .toArray(String[]::new);
    }

    /** A parsed {@code field:value} filter-query selection. */
    private record FieldValue(String field, String value) {
    }

    /**
     * Parses a {@code field:value} filter query into its field and (unquoted)
     * value. Values keep their exact case — Lucene {@code StringField}s are not
     * analyzed, so facet term matching is exact.
     */
    private Optional<FieldValue> parseFieldValue(String fq) {
        if (!StringUtils.hasText(fq))
            return Optional.empty();
        int colonIndex = fq.indexOf(':');
        if (colonIndex <= 0 || colonIndex >= fq.length() - 1) {
            log.warn("Invalid filter query format '{}' — expected 'field:value'", fq);
            return Optional.empty();
        }
        String field = fq.substring(0, colonIndex);
        String value = fq.substring(colonIndex + 1);
        // Strip surrounding quotes if present
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return Optional.of(new FieldValue(field, value));
    }
}
