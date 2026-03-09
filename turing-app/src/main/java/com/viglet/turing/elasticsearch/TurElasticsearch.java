/*
 * Copyright (C) 2016-2022 the original author or authors.
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
package com.viglet.turing.elasticsearch;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.UnknownNullability;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;

/**
 * Elasticsearch search operations
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurElasticsearch {
    private final TurSNSiteRepository turSNSiteRepository;

    public TurElasticsearch(TurSNSiteRepository turSNSiteRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
    }

    public Optional<TurSEResults> retrieveElasticsearchFromSN(
            TurElasticsearchInstance elasticsearchInstance,
            TurSNSiteSearchContext context) {
        return turSNSiteRepository.findByName(context.getSiteName())
                .flatMap(turSNSite -> {
                    try {
                        TurSEParameters turSEParameters = context.getTurSEParameters();

                        // Build Elasticsearch query
                        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                                .index(elasticsearchInstance.getIndex())
                                .query(buildQuery(turSEParameters))
                                .from(getStartPosition(turSEParameters))
                                .size(turSEParameters.getRows());

                        // Add sorting if specified
                        if (StringUtils.hasText(turSEParameters.getSort())) {
                            addSorting(searchBuilder, turSEParameters.getSort());
                        }

                        SearchRequest searchRequest = searchBuilder.build();
                        SearchResponse<Map<String, Object>> response = elasticsearchInstance.getClient()
                                .search(searchRequest, (Type) Map.class);

                        return Optional.of(buildTurSEResults(response, turSEParameters));
                    } catch (IOException e) {
                        log.error("Error executing Elasticsearch search: {}", e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    public Optional<TurSEResults> retrieveFacetElasticsearchFromSN(
            TurElasticsearchInstance elasticsearchInstance,
            TurSNSiteSearchContext context) {
        // Simplified facet implementation - can be enhanced later
        return retrieveElasticsearchFromSN(elasticsearchInstance, context);
    }

    private Query buildQuery(TurSEParameters turSEParameters) {
        String queryString = turSEParameters.getQuery();
        if (!StringUtils.hasText(queryString) || "*".equals(queryString.trim())) {
            // Match all query
            return Query.of(q -> q.matchAll(m -> m));
        }

        // Simple query string query
        return Query.of(q -> q.queryString(qs -> qs
                .query(queryString)
                .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)));
    }

    private int getStartPosition(TurSEParameters turSEParameters) {
        int currentPage = turSEParameters.getCurrentPage();
        int rows = turSEParameters.getRows();
        return (currentPage - 1) * rows;
    }

    private void addSorting(SearchRequest.Builder searchBuilder, String sortString) {
        String[] sortParts = sortString.split("\\s+");
        if (sortParts.length > 0) {
            String field = sortParts[0];
            SortOrder order = sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1])
                    ? SortOrder.Desc
                    : SortOrder.Asc;

            searchBuilder.sort(s -> s.field(f -> f.field(field).order(order)));
        }
    }

    private TurSEResults buildTurSEResults(@UnknownNullability SearchResponse<Map<String, Object>> response,
            TurSEParameters turSEParameters) {
        long numFound = response.hits().total() != null ? response.hits().total().value() : 0L;
        int rows = turSEParameters.getRows();
        int pageCount = (int) Math.ceil((double) numFound / rows);

        List<TurSEResult> results = response.hits()
                .hits()
                .stream().map(Hit::source)
                .filter(Objects::nonNull).map(source -> TurSEResult.builder().fields(source).build()).toList();

        return TurSEResults.builder()
                .numFound(numFound)
                .start(getStartPosition(turSEParameters))
                .limit(rows)
                .pageCount(pageCount)
                .currentPage(turSEParameters.getCurrentPage())
                .results(results)
                .qTime((int) response.took())
                .elapsedTime(response.took())
                .queryString(turSEParameters.getQuery())
                .sort(turSEParameters.getSort())
                .spellCheck(null)
                .similarResults(Collections.emptyList())
                .facetResults(Collections.emptyList())
                .groups(Collections.emptyList())
                .build();
    }
}
