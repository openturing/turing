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

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.field.TurSNSiteFieldService;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main Lucene service — orchestrates query building, execution, and result
 * processing. Mirrors {@code TurSolr} for the Lucene search engine plugin.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
@Transactional
public class TurLucene {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurLuceneQueryBuilder turLuceneQueryBuilder;
    private final TurLuceneResultProcessor turLuceneResultProcessor;
    private final TurLuceneDocumentHandler turLuceneDocumentHandler;
    private final TurLuceneCustomFacets turLuceneCustomFacets;

    public TurLucene(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldService turSNSiteFieldService,
            TurLuceneQueryBuilder turLuceneQueryBuilder,
            TurLuceneStorageSync turLuceneStorageSync,
            TurLuceneCustomFacets turLuceneCustomFacets) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turLuceneQueryBuilder = turLuceneQueryBuilder;
        this.turLuceneCustomFacets = turLuceneCustomFacets;
        this.turLuceneResultProcessor = new TurLuceneResultProcessor();
        this.turLuceneDocumentHandler = new TurLuceneDocumentHandler(turSNSiteFieldService,
                turLuceneStorageSync);
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------

    /**
     * Executes a full search for the given site/locale context and returns
     * populated {@link TurSEResults} including facets.
     */
    public Optional<TurSEResults> retrieveLuceneFromSN(TurLuceneInstance instance,
            TurSNSiteSearchContext context) {
        return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName()).map(turSNSite -> {
            long startTime = System.currentTimeMillis();
            TurSEParameters params = context.getTurSEParameters();

            Query query = turLuceneQueryBuilder.buildQuery(turSNSite, params, context.getLocale());
            List<TurSNSiteFieldExt> hlFields = turLuceneQueryBuilder.getHLFields(turSNSite);
            var sort = turLuceneQueryBuilder.buildSort(turSNSite, params);

            TurSEResults results = turLuceneResultProcessor.getResults(
                    new TurLuceneSearch(instance, turSNSite, query, params),
                    turLuceneQueryBuilder.getFacetFields(turSNSite),
                    hlFields, sort, startTime);

            // Wildcard fallback when the query matched nothing and the site allows it. Decided by
            // the total hits, not by the page being empty: a page past the end of the pagination
            // is empty too, and must not be retried with a wildcard.
            if (results.getNumFound() == 0
                    && shouldApplyWildcard(turSNSite, params.getQuery())) {
                Query wildcardQuery = turLuceneQueryBuilder.buildWildcardQuery(turSNSite, params);
                results = turLuceneResultProcessor.getResults(
                        new TurLuceneSearch(instance, turSNSite, wildcardQuery, params),
                        turLuceneQueryBuilder.getFacetFields(turSNSite),
                        hlFields, sort, startTime);
                query = wildcardQuery;
            }
            return withCustomFacets(results, instance, query, turSNSite, context, null);
        });
    }

    /**
     * T1083 — adds the site's custom facets (labelled range items) to the facet
     * results, counted over the documents {@code query} matches, as Solr answers
     * them from its facet queries. Only when the site has facets on.
     */
    private TurSEResults withCustomFacets(TurSEResults results, TurLuceneInstance instance, Query query,
            TurSNSite turSNSite, TurSNSiteSearchContext context, String onlyFacet) {
        if (turLuceneCustomFacets == null || !TurSNUtils.isTrue(turSNSite.getFacet())) {
            return results;
        }
        try {
            List<TurSEFacetResult> custom = turLuceneCustomFacets.count(instance.getSearcher(), query,
                    turSNSite, context.getLocale(), onlyFacet);
            if (!custom.isEmpty()) {
                List<TurSEFacetResult> facets = new ArrayList<>(
                        results.getFacetResults() == null ? List.of() : results.getFacetResults());
                facets.addAll(custom);
                results.setFacetResults(facets);
            }
        } catch (IOException e) {
            log.warn("[Lucene] custom facets for site '{}' not counted: {}", turSNSite.getName(),
                    e.getMessage());
        }
        return results;
    }

    /**
     * Executes a search scoped to a single facet dimension (used by the facet
     * drill-down API).
     */
    public Optional<TurSEResults> retrieveFacetLuceneFromSN(TurLuceneInstance instance,
            TurSNSiteSearchContext context, String facetName) {
        return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName()).map(turSNSite -> {
            long startTime = System.currentTimeMillis();
            TurSEParameters params = context.getTurSEParameters();
            Query query = turLuceneQueryBuilder.buildQuery(turSNSite, params, context.getLocale());
            TurSEResults results = turLuceneResultProcessor.getResults(
                    new TurLuceneSearch(instance, turSNSite, query, params),
                    turLuceneQueryBuilder.getFacetFields(turSNSite, facetName),
                    List.of(), null, startTime);
            return withCustomFacets(results, instance, query, turSNSite, context, facetName);
        });
    }

    // -------------------------------------------------------------------------
    // Indexing
    // -------------------------------------------------------------------------

    public void indexing(TurLuceneInstance instance, TurSNSite turSNSite, Map<String, Object> attributes) {
        turLuceneDocumentHandler.indexing(instance, turSNSite, attributes);
    }

    /**
     * T804 / §LV.2 — bulk indexing: one writer session + one commit across all
     * documents. Delegates to {@link TurLuceneDocumentHandler#indexingBatch}.
     */
    public int indexingBatch(TurLuceneInstance instance, TurSNSite turSNSite,
            List<Map<String, Object>> documents) {
        return turLuceneDocumentHandler.indexingBatch(instance, turSNSite, documents);
    }

    public void deIndexing(TurLuceneInstance instance, String id) {
        turLuceneDocumentHandler.deIndexing(instance, id);
    }

    public void deIndexingByType(TurLuceneInstance instance, String type) {
        turLuceneDocumentHandler.deIndexingByType(instance, type);
    }

    // -------------------------------------------------------------------------
    // Wildcard helpers
    // -------------------------------------------------------------------------

    private static boolean shouldApplyWildcard(TurSNSite turSNSite, String query) {
        return turSNSite.getWildcardNoResults() != null
                && turSNSite.getWildcardNoResults() == 1
                && !isQueryExpression(query);
    }

    /** Returns true when the query already ends with a wildcard or expression boundary. */
    public static boolean isQueryExpression(String query) {
        if (query == null || query.isBlank()) return false;
        String trimmed = query.trim();
        return trimmed.endsWith("*") || trimmed.endsWith("\"")
                || trimmed.endsWith("]") || trimmed.endsWith(")");
    }
}
