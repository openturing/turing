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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortMissingEnum;
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
    private final TurLuceneRankingExpressions rankingExpressions;
    private final TurLuceneCustomFacets customFacets;

    public TurLuceneQueryBuilder(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurLuceneSynonymRegistry synonymRegistry,
            TurLuceneRankingExpressions rankingExpressions) {
        this(turSNSiteFieldExtRepository, turSNSiteCustomSortRepository, turSNSiteLocaleRepository,
                synonymRegistry, rankingExpressions, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TurLuceneQueryBuilder(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurLuceneSynonymRegistry synonymRegistry,
            TurLuceneRankingExpressions rankingExpressions,
            TurLuceneCustomFacets customFacets) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteCustomSortRepository = turSNSiteCustomSortRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.synonymRegistry = synonymRegistry;
        this.rankingExpressions = rankingExpressions;
        this.customFacets = customFacets;
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
     * T665 â€” same as {@link #buildQuery(TurSNSite, TurSEParameters)} but with the
     * search {@code locale}, so the main query is analyzed with the site's
     * query-time synonym map (when one is registered for that locale's core).
     */
    public Query buildQuery(TurSNSite turSNSite, TurSEParameters params, Locale locale) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Main user query
        Query mainQuery = buildMainQuery(params.getQuery(), turSNSite, locale);
        builder.add(mainQuery, BooleanClause.Occur.MUST);

        // Filter queries (facet selections, fqAnd/fqOr and their operators)
        TurSNFilterParams filterParams = params.getTurSNFilterParams();
        if (filterParams != null && hasFilterQueries(filterParams)) {
            addFilterQueries(builder, turSNSite, filterParams);
        }

        // T1103 — semantic recall's id restriction, apart from the facet filters.
        if (params.getRestrictIds() != null && !params.getRestrictIds().isEmpty()) {
            builder.add(new TermInSetQuery(com.viglet.turing.commons.sn.field.TurSNFieldName.ID,
                    params.getRestrictIds().stream().map(BytesRef::new).toList()),
                    BooleanClause.Occur.FILTER);
        }

        // T1064 â€” ranking expressions raise the score of the documents they
        // describe (SHOULD beside the MUST main query), as Solr's bq does.
        if (rankingExpressions != null) {
            rankingExpressions.boostQueries(turSNSite)
                    .forEach(boost -> builder.add(boost, BooleanClause.Occur.SHOULD));
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
     *   <li><b>across fields</b>, each field group intersects (<b>AND</b>) â€” a
     *       selection in one field narrows the result set of another.</li>
     * </ul>
     * The previous implementation added every value as its own {@code FILTER}
     * (AND) clause, so selecting two values of the same multi-select facet (e.g.
     * two mutually-exclusive categories) yielded zero hits even though the facet
     * counts promised a union. Cross-field union from a field's or the site's
     * configured {@code facetType == OR} is intentionally not honoured: it
     * broadens instead of narrowing and breaks the expected "pick another field
     * to drill down" behaviour.
     *
     * <p>T1077 â€” what the <em>request</em> asks for is honoured, as Solr does:
     * {@code fqItemOperator} sets how the values of one field combine,
     * {@code fqOperator} sets how the plain {@code fq} fields combine, and
     * {@code fqAnd}/{@code fqOr} are fields that intersect/union whatever the
     * operator. The intersected groups are then OR'd with the union groups,
     * which is the shape {@code TurSolrQueryBuilder#setFilterQueryString} builds.
     */
    private void addFilterQueries(BooleanQuery.Builder builder, TurSNSite turSNSite,
            TurSNFilterParams filterParams) {
        TurSNSiteFacetFieldEnum defaultFacetType = filterParams.getOperator() == TurSNFilterQueryOperator.OR ? TurSNSiteFacetFieldEnum.OR : TurSNSiteFacetFieldEnum.AND;
        Map<String, FilterGroup> groups = new LinkedHashMap<>();
        collectFilterGroups(groups, filterParams.getDefaultValues(), defaultFacetType);
        collectFilterGroups(groups, filterParams.getAnd(), TurSNSiteFacetFieldEnum.AND);
        collectFilterGroups(groups, filterParams.getOr(), TurSNSiteFacetFieldEnum.OR);
        if (groups.isEmpty()) {
            return;
        }

        Map<String, TurSNSiteFieldExt> fieldsByName = enabledFieldsByName(turSNSite);
        Instant now = Instant.now();
        List<Query> intersected = new ArrayList<>();
        List<Query> united = new ArrayList<>();
        groups.values().forEach(group -> {
            TurSNSiteFieldExt ext = fieldsByName.get(group.field());
            TurSNSiteFacetFieldEnum itemType = requestedItemType(filterParams.getItemOperator())
                    .orElseGet(() -> effectiveFacetItemType(ext, turSNSite));
            TurSEFieldType type = ext != null ? ext.getType() : null;
            Query query = buildFieldFilterQuery(group.values(),
                    value -> valueQuery(turSNSite, group.field(), ext, value, type, now), itemType);
            (group.facetType() == TurSNSiteFacetFieldEnum.OR ? united : intersected).add(query);
        });

        if (united.isEmpty()) {
            intersected.forEach(query -> builder.add(query, BooleanClause.Occur.FILTER));
            return;
        }
        BooleanQuery.Builder disjunction = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
        if (!intersected.isEmpty()) {
            BooleanQuery.Builder conjunction = new BooleanQuery.Builder();
            intersected.forEach(query -> conjunction.add(query, BooleanClause.Occur.MUST));
            disjunction.add(conjunction.build(), BooleanClause.Occur.SHOULD);
        }
        united.forEach(query -> disjunction.add(query, BooleanClause.Occur.SHOULD));
        builder.add(disjunction.build(), BooleanClause.Occur.FILTER);
    }

    /**
     * T1083 — a selection on a field the site has no field for may be a custom
     * facet's {@code <facet>:<label>}: it stands for the item's range on the field
     * the custom facet belongs to, as on Solr.
     */
    private Query valueQuery(TurSNSite turSNSite, String field, TurSNSiteFieldExt ext, String value,
            TurSEFieldType type, Instant now) {
        if (ext == null && customFacets != null) {
            Optional<TurLuceneCustomFacets.ItemFilter> item = customFacets.resolveFilter(turSNSite, field, value);
            if (item.isPresent()) {
                return TurLuceneFilterQueries.build(item.get().field(), item.get().value(), item.get().type(),
                        now);
            }
        }
        return TurLuceneFilterQueries.build(field, value, type, now);
    }

    /** One field's selected values and how the field combines with the others. */
    private record FilterGroup(String field, TurSNSiteFacetFieldEnum facetType, List<String> values) {
    }

    /** Groups by field; a field already grouped keeps the facet type it was first seen with. */
    private void collectFilterGroups(Map<String, FilterGroup> groups, List<String> filterQueries,
            TurSNSiteFacetFieldEnum facetType) {
        if (filterQueries == null) {
            return;
        }
        filterQueries.forEach(fq -> parseFieldValue(fq).ifPresent(fv -> groups
                .computeIfAbsent(fv.field(), field -> new FilterGroup(field, facetType, new ArrayList<>()))
                .values().add(fv.value())));
    }

    private static boolean hasFilterQueries(TurSNFilterParams filterParams) {
        return !CollectionUtils.isEmpty(filterParams.getDefaultValues())
                || !CollectionUtils.isEmpty(filterParams.getAnd())
                || !CollectionUtils.isEmpty(filterParams.getOr());
    }

    private static Optional<TurSNSiteFacetFieldEnum> requestedItemType(TurSNFilterQueryOperator operator) {
        if (operator == TurSNFilterQueryOperator.OR) {
            return Optional.of(TurSNSiteFacetFieldEnum.OR);
        }
        if (operator == TurSNFilterQueryOperator.AND) {
            return Optional.of(TurSNSiteFacetFieldEnum.AND);
        }
        return Optional.empty();
    }

    /**
     * Combines the selected values of a single field into one query: OR
     * (SHOULD, min-should-match 1) for a multi-select facet, AND (MUST)
     * otherwise. Each value is read by the field's type (T1075), so a range or
     * a numeric/date value matches the points the field was indexed as.
     */
    private static Query buildFieldFilterQuery(List<String> values,
            java.util.function.Function<String, Query> valueQuery, TurSNSiteFacetFieldEnum itemType) {
        if (values.size() == 1) {
            return valueQuery.apply(values.getFirst());
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        BooleanClause.Occur occur = itemType == TurSNSiteFacetFieldEnum.OR
                ? BooleanClause.Occur.SHOULD
                : BooleanClause.Occur.MUST;
        values.forEach(value -> builder.add(valueQuery.apply(value), occur));
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

    /** Field-level {@code facetItemType} wins over the site default; DEFAULT/null â†’ AND. */
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
        // T707 â€” never highlight identifier/URL fields even if a field is
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
                                    item.getSortOrder() == TurSNSiteCustomSortOrderEnum.DESC,
                                    item.getMissing()))
                            .toArray(org.apache.lucene.search.SortField[]::new);
                    return new org.apache.lucene.search.Sort(fields);
                })
                .orElse(null);
    }

    private org.apache.lucene.search.SortField buildSortField(String fieldName, boolean reverse) {
        return buildSortField(fieldName, reverse, null);
    }

    private org.apache.lucene.search.SortField buildSortField(String fieldName, boolean reverse,
            TurSNSiteCustomSortMissingEnum missing) {
        // Determine the SortField type based on the field's configured type
        List<TurSNSiteFieldExt> allFields = turSNSiteFieldExtRepository.findAll();
        for (TurSNSiteFieldExt ext : allFields) {
            if (fieldName.equals(ext.getName()) && ext.getType() != null) {
                return sortField(fieldName, ext.getType(), reverse, missing);
            }
        }
        // Unknown field â€” use SortedSetSortField (works with SortedSetDocValuesField from addFacetField)
        return sortField(fieldName, null, reverse, missing);
    }

    /**
     * The sort field for a field of {@code type}. T1104 — {@code missing} places
     * documents with no value first or last. Lucene compares the missing value
     * before applying {@code reverse}, so "last" on a descending level is the
     * smallest value, not the largest; {@code null} keeps Lucene's own placement.
     */
    static org.apache.lucene.search.SortField sortField(String fieldName, TurSEFieldType type,
            boolean reverse, TurSNSiteCustomSortMissingEnum missing) {
        boolean naturalLast = (missing == TurSNSiteCustomSortMissingEnum.LAST) != reverse;
        org.apache.lucene.search.SortField.Type sortType = type == null ? null : switch (type) {
            case INT -> org.apache.lucene.search.SortField.Type.INT;
            case LONG, DATE -> org.apache.lucene.search.SortField.Type.LONG;
            case FLOAT -> org.apache.lucene.search.SortField.Type.FLOAT;
            case DOUBLE, CURRENCY -> org.apache.lucene.search.SortField.Type.DOUBLE;
            // TEXT, STRING, ARRAY, BOOL use SortedSetDocValues (multi-value safe)
            default -> null;
        };
        if (sortType == null) {
            return missing == null
                    ? new org.apache.lucene.search.SortedSetSortField(fieldName, reverse)
                    : new org.apache.lucene.search.SortedSetSortField(fieldName, reverse,
                            org.apache.lucene.search.SortedSetSelector.Type.MIN,
                            naturalLast ? org.apache.lucene.search.SortField.STRING_LAST
                                    : org.apache.lucene.search.SortField.STRING_FIRST);
        }
        if (missing == null) {
            return new org.apache.lucene.search.SortField(fieldName, sortType, reverse);
        }
        Object missingValue = switch (sortType) {
            case INT -> naturalLast ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            case LONG -> naturalLast ? Long.MAX_VALUE : Long.MIN_VALUE;
            case FLOAT -> naturalLast ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            default -> naturalLast ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        };
        return new org.apache.lucene.search.SortField(fieldName, sortType, reverse, missingValue);
    }

    /**
     * Builds an autocomplete query that searches across edge-ngram indexed
     * {@code *_ac} fields. The query is analyzed with {@link StandardAnalyzer}
     * (lowercase, tokenize) and matched against the ngram tokens in the index.
     */
    public Query buildAutoCompleteQuery(TurSNSite turSNSite, String term) {
        // Map to _ac suffixed fields for ngram matching
        String[] acFields = java.util.Arrays.stream(resolveAutoCompleteFields(turSNSite))
                .map(f -> f + TurLuceneConstants.AC_SUFFIX)
                .toArray(String[]::new);
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
                return phrasesOnPositionalFieldsOnly(new QueryParser(TurLuceneConstants.ID, analyzer).parse(
                        QueryParserBase.escape(queryStr)), Set.of());
            }
            // T1065 â€” a field's search weight multiplies its clause, so a title
            // match can outweigh the same word in body text.
            Map<String, Float> weights = searchWeights(turSNSite);
            MultiFieldQueryParser parser = weights.isEmpty()
                    ? new MultiFieldQueryParser(textFields, analyzer)
                    : new MultiFieldQueryParser(textFields, analyzer, weights);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            // T1066 â€” a multi-word expansion is a phrase, not loose words.
            parser.setAutoGenerateMultiTermSynonymsPhraseQuery(true);
            Query termCentric = parser.parse(queryStr);
            Query main = analyzer instanceof TurLuceneSynonymAnalyzer
                    ? withMultiWordSynonyms(termCentric, queryStr, textFields, analyzer, weights)
                    : termCentric;
            return phrasesOnPositionalFieldsOnly(main, positionalFields(turSNSite));
        } catch (ParseException e) {
            log.warn("Failed to parse Lucene query '{}', falling back to MatchAllDocsQuery: {}", queryStr,
                    e.getMessage());
            return buildTermQueries(queryStr, textFields);
        }
    }

    /**
     * T665 â€” the query-time analyzer: the site's synonym-aware analyzer when a
     * {@link SynonymMap} is registered for the search locale's core, otherwise
     * the plain {@link StandardAnalyzer} (identical to pre-T665 behaviour). Zero
     * overhead for sites without synonyms â€” the registry short-circuits and no
     * locale/core lookup runs.
     */
    private Analyzer queryAnalyzer(TurSNSite turSNSite, Locale locale) {
        // T1080 — both analyzers add the accent-folded form of each token, as the
        // index side does, so "saude" and "saúde" find each other.
        if (locale == null || synonymRegistry == null || synonymRegistry.isEmpty()) {
            return new TurLuceneFoldingAnalyzer();
        }
        String core = turSNSiteLocaleRepository.findByTurSNSite(turSNSite).stream()
                .filter(l -> locale.equals(l.getLanguage()))
                .map(TurSNSiteLocale::getCore)
                .findFirst()
                .orElse(null);
        SynonymMap map = core == null ? null : synonymRegistry.get(core);
        return map == null ? new TurLuceneFoldingAnalyzer() : new TurLuceneSynonymAnalyzer(map);
    }

    /**
     * T1066 â€” the classic parser splits the query on whitespace before analysis,
     * so the synonym filter only ever saw {@code home} and {@code office} apart
     * and a multi-word term such as "home office" never expanded.
     *
     * <p>The term-centric parse is kept as it is (each word may match in any
     * field) and ORed with a per-field parse that hands the filter the whole query
     * string, so a multi-word synonym expands within a field and a multi-word
     * expansion is searched as a phrase, not as loose words. Not splitting on the
     * multi-field parser instead would demand the phrase in every field at once.
     * Only queries analysed with a site's synonyms take this path.
     */
    private static Query withMultiWordSynonyms(Query termCentric, String queryStr, String[] fields,
            Analyzer analyzer, Map<String, Float> weights) {
        BooleanQuery.Builder perField = new BooleanQuery.Builder();
        for (String field : fields) {
            QueryParser parser = new QueryParser(field, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            parser.setSplitOnWhitespace(false);
            parser.setAutoGenerateMultiTermSynonymsPhraseQuery(true);
            try {
                Query query = parser.parse(queryStr);
                Float weight = weights.get(field);
                perField.add(weight != null ? new BoostQuery(query, weight) : query, BooleanClause.Occur.SHOULD);
            } catch (ParseException e) {
                return termCentric;
            }
        }
        return new BooleanQuery.Builder()
                .add(termCentric, BooleanClause.Occur.SHOULD)
                .add(perField.build(), BooleanClause.Occur.SHOULD)
                .build();
    }

    /**
     * T1091 — the fields indexed with positions, which are the only ones a phrase
     * can run on: TEXT fields are a {@code TextField}, while STRING/ARRAY fields
     * and the {@code id} (always, see {@code TurLuceneDocumentHandler}) are a
     * {@code StringField} with no position data.
     */
    private Set<String> positionalFields(TurSNSite turSNSite) {
        Set<String> fields = new HashSet<>();
        turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1).forEach(ext -> {
            if (ext.getType() == TurSEFieldType.TEXT && ext.getName() != null
                    && !TurLuceneConstants.ID.equals(ext.getName())) {
                fields.add(ext.getName());
            }
        });
        return fields;
    }

    /**
     * T1091 — a phrase (a multi-word synonym expansion, or a quoted query) on a
     * field without positions throws at search time and fails the whole query.
     * On such a field the phrase becomes an exact term on the whole phrase, which
     * is how a keyword field is matched; every other clause is kept as parsed.
     */
    static Query phrasesOnPositionalFieldsOnly(Query query, Set<String> positional) {
        return switch (query) {
            case BooleanQuery bq -> {
                BooleanQuery.Builder builder = new BooleanQuery.Builder()
                        .setMinimumNumberShouldMatch(bq.getMinimumNumberShouldMatch());
                for (BooleanClause clause : bq.clauses()) {
                    builder.add(phrasesOnPositionalFieldsOnly(clause.query(), positional), clause.occur());
                }
                yield builder.build();
            }
            case BoostQuery boost -> new BoostQuery(
                    phrasesOnPositionalFieldsOnly(boost.getQuery(), positional), boost.getBoost());
            case DisjunctionMaxQuery dismax -> new DisjunctionMaxQuery(dismax.getDisjuncts().stream()
                    .map(q -> phrasesOnPositionalFieldsOnly(q, positional)).toList(),
                    dismax.getTieBreakerMultiplier());
            case PhraseQuery phrase when !positional.contains(phrase.getField()) -> new TermQuery(
                    new Term(phrase.getField(), Arrays.stream(phrase.getTerms())
                            .map(Term::text).collect(Collectors.joining(" "))));
            case MultiPhraseQuery multi when !positional.contains(field(multi)) -> new MatchNoDocsQuery(
                    "T1091: no positions on " + field(multi));
            default -> query;
        };
    }

    private static String field(MultiPhraseQuery multi) {
        Term[][] terms = multi.getTermArrays();
        return terms.length == 0 || terms[0].length == 0 ? null : terms[0][0].field();
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

    /** Enabled fields whose search weight differs from 1, keyed by name. */
    private Map<String, Float> searchWeights(TurSNSite turSNSite) {
        Map<String, Float> weights = new LinkedHashMap<>();
        turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1).forEach(ext -> {
            Float weight = ext.getSearchWeight();
            if (ext.getName() != null && weight != null && weight > 0 && weight != 1f) {
                weights.putIfAbsent(ext.getName(), weight);
            }
        });
        return weights;
    }

    /**
     * T1076 â€” the fields autocomplete matches and draws its suggestions from: the
     * site's enabled free-text ({@link TurSEFieldType#TEXT}) fields, never the
     * keyword ones (STRING/ARRAY hold slugs, codes and URLs nobody types) nor
     * {@code id}/{@code url}. Falls back to {@code title} when the site has none.
     */
    public String[] resolveAutoCompleteFields(TurSNSite turSNSite) {
        String[] fields = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1)
                .stream()
                .filter(ext -> ext.getType() == TurSEFieldType.TEXT && ext.getName() != null)
                .map(TurSNSiteFieldExt::getName)
                .filter(name -> !TurLuceneConstants.ID.equals(name)
                        && !TurLuceneConstants.URL.equals(name))
                .distinct()
                .toArray(String[]::new);
        return fields.length > 0 ? fields : new String[] { TurLuceneConstants.TITLE };
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
     * value. Values keep their exact case â€” Lucene {@code StringField}s are not
     * analyzed, so facet term matching is exact.
     */
    private Optional<FieldValue> parseFieldValue(String fq) {
        if (!StringUtils.hasText(fq))
            return Optional.empty();
        int colonIndex = fq.indexOf(':');
        if (colonIndex <= 0 || colonIndex >= fq.length() - 1) {
            log.warn("Invalid filter query format '{}' â€” expected 'field:value'", fq);
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
