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
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.field.TurSNSiteFieldService;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.Query;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public TurLucene(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldService turSNSiteFieldService,
            TurLuceneQueryBuilder turLuceneQueryBuilder,
            TurLuceneStorageSync turLuceneStorageSync) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turLuceneQueryBuilder = turLuceneQueryBuilder;
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

            // Wildcard fallback when no results and site allows it
            if (results.getResults().isEmpty()
                    && shouldApplyWildcard(turSNSite, params.getQuery())) {
                Query wildcardQuery = turLuceneQueryBuilder.buildWildcardQuery(turSNSite, params);
                results = turLuceneResultProcessor.getResults(
                        new TurLuceneSearch(instance, turSNSite, wildcardQuery, params),
                        turLuceneQueryBuilder.getFacetFields(turSNSite),
                        hlFields, sort, startTime);
            }
            return results;
        });
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
            return turLuceneResultProcessor.getResults(
                    new TurLuceneSearch(instance, turSNSite, query, params),
                    turLuceneQueryBuilder.getFacetFields(turSNSite, facetName),
                    List.of(), null, startTime);
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
