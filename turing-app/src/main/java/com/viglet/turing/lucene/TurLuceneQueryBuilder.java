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

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

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

    public TurLuceneQueryBuilder(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    // -------------------------------------------------------------------------
    // Main query
    // -------------------------------------------------------------------------

    /**
     * Builds the primary Lucene {@link Query} from the search context.
     * Includes the user's query string and any active filter queries.
     */
    public Query buildQuery(TurSNSite turSNSite, TurSEParameters params) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Main user query
        Query mainQuery = buildMainQuery(params.getQuery(), turSNSite);
        builder.add(mainQuery, BooleanClause.Occur.MUST);

        // Filter queries (facet selections)
        List<String> filterQueries = params.getTurSNFilterParams().getDefaultValues();
        if (!CollectionUtils.isEmpty(filterQueries)) {
            filterQueries
                    .forEach(fq -> parseFilterQuery(fq).ifPresent(q -> builder.add(q, BooleanClause.Occur.FILTER)));
        }

        return builder.build();
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
        return turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(turSNSite, 1, 1);
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

    private Query buildMainQuery(String queryStr, TurSNSite turSNSite) {
        if (!StringUtils.hasText(queryStr) || queryStr.equals("*") || queryStr.equals("*:*")) {
            return new FieldExistsQuery(TurLuceneConstants.ID);
        }

        String[] textFields = resolveTextFields(turSNSite);
        try {
            if (textFields.length == 0) {
                // Fallback: search the id field
                return new QueryParser(TurLuceneConstants.ID, new StandardAnalyzer()).parse(
                        QueryParserBase.escape(queryStr));
            }
            MultiFieldQueryParser parser = new MultiFieldQueryParser(textFields, new StandardAnalyzer());
            parser.setDefaultOperator(QueryParser.Operator.AND);
            return parser.parse(queryStr);
        } catch (ParseException e) {
            log.warn("Failed to parse Lucene query '{}', falling back to MatchAllDocsQuery: {}", queryStr,
                    e.getMessage());
            return buildTermQueries(queryStr, textFields);
        }
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

    private Optional<Query> parseFilterQuery(String fq) {
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
        // Use TermQuery to preserve exact case (StringField values are not analyzed)
        return Optional.of(new TermQuery(new Term(field, value)));
    }
}
