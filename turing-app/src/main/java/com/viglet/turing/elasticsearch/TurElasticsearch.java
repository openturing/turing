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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.UnknownNullability;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.DoubleTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
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

    // --- S1192: extracted duplicated literals ---
    private static final String INDEX_NOT_FOUND_EXCEPTION = "index_not_found_exception";

    private static final String FACET_AGG_PREFIX = "facet_";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurElasticsearch(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    public Optional<TurSEResults> retrieveElasticsearchFromSN(
            TurElasticsearchInstance elasticsearchInstance,
            TurSNSiteSearchContext context) {
        return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName())
                .flatMap(turSNSite -> {
                    try {
                        TurSEParameters turSEParameters = context.getTurSEParameters();

                        int rows = turSEParameters.getRows() > 0 ? turSEParameters.getRows() : 10;
                        int from = getStartPosition(turSEParameters);
                        Query query = buildQuery(turSEParameters);

                        List<TurSNSiteFieldExt> facetFields = turSNSiteFieldExtRepository
                                .findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(turSNSite, 1, 1);
                        log.debug("ES search index={} query={} from={} size={} facetFields={}",
                                elasticsearchInstance.getIndex(), query, from, rows,
                                facetFields.stream().map(TurSNSiteFieldExt::getName).toList());

                        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                                .index(elasticsearchInstance.getIndex())
                                .query(query)
                                .from(from)
                                .size(rows);

                        addFacetAggregations(searchBuilder, facetFields);
                        addOptionalSorting(searchBuilder, turSEParameters);

                        SearchRequest searchRequest = searchBuilder.build();
                        SearchResponse<Map<String, Object>> response = elasticsearchInstance.getClient()
                                .search(searchRequest, (Type) Map.class);

                        return Optional.of(buildTurSEResults(response, turSEParameters, facetFields));
                    } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
                        handleElasticsearchException(e, elasticsearchInstance.getIndex());
                        return Optional.empty();
                    } catch (IOException e) {
                        log.error("Error executing Elasticsearch search: {}", e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    public Optional<TurSEResults> retrieveFacetElasticsearchFromSN(
            TurElasticsearchInstance elasticsearchInstance,
            TurSNSiteSearchContext context,
            String facetName) {
        return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName())
                .flatMap(turSNSite -> {
                    try {
                        TurSEParameters turSEParameters = context.getTurSEParameters();
                        Query query = buildQuery(turSEParameters);
                        String aggName = FACET_AGG_PREFIX + facetName;

                        // Resolve the correct ES field name (text fields need ".keyword" sub-field)
                        String aggField = turSNSiteFieldExtRepository
                                .findByTurSNSiteAndNameAndFacetAndEnabled(turSNSite, facetName, 1, 1)
                                .stream().findFirst()
                                .map(TurElasticsearch::getFacetAggField)
                                .orElse(facetName);

                        SearchRequest searchRequest = SearchRequest.of(r -> r
                                .index(elasticsearchInstance.getIndex())
                                .query(query)
                                .size(0)
                                .aggregations(aggName, a -> a
                                        .terms(t -> t.field(aggField).size(100))));

                        SearchResponse<Map<String, Object>> response = elasticsearchInstance.getClient()
                                .search(searchRequest, (Type) Map.class);

                        TurSEFacetResult facetResult = new TurSEFacetResult();
                        facetResult.setFacet(facetName);
                        facetResult.setFacetPosition(1);

                        var agg = response.aggregations().get(aggName);
                        if (agg != null && agg.isSterms()) {
                            List<StringTermsBucket> buckets = agg.sterms().buckets().array();
                            for (StringTermsBucket bucket : buckets) {
                                String key = bucket.key().stringValue();
                                int count = (int) bucket.docCount();
                                facetResult.add(key, new TurSEFacetResultAttr(key, count));
                            }
                        }

                        TurSEResults results = TurSEResults.builder()
                                .numFound(0)
                                .start(0)
                                .limit(0)
                                .pageCount(0)
                                .currentPage(1)
                                .results(Collections.emptyList())
                                .qTime((int) response.took())
                                .elapsedTime(response.took())
                                .queryString(turSEParameters.getQuery())
                                .sort(turSEParameters.getSort())
                                .spellCheck(null)
                                .similarResults(Collections.emptyList())
                                .facetResults(List.of(facetResult))
                                .groups(Collections.emptyList())
                                .build();
                        return Optional.of(results);
                    } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
                        log.error("ES facet query failed for field={}: type={} reason={}",
                                facetName, e.error().type(), e.error().reason());
                        return Optional.empty();
                    } catch (IOException e) {
                        log.error("IO error executing ES facet query for field={}: {}", facetName, e.getMessage(), e);
                        return Optional.empty();
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Indexing
    // -------------------------------------------------------------------------

    public void indexing(TurElasticsearchInstance instance, TurSNSite turSNSite,
            Map<String, Object> attributes) {
        log.debug("Elasticsearch indexing site={} index={}", turSNSite.getName(), instance.getIndex());
        String docId = Optional.ofNullable(attributes.get(TurSNFieldName.ID))
                .map(Object::toString)
                .orElse(null);
        if (docId == null) {
            log.warn("Skipping Elasticsearch document with no id for site={}", turSNSite.getName());
            return;
        }
        try {
            IndexRequest<Map<String, Object>> request = IndexRequest.of(r -> r
                    .index(instance.getIndex())
                    .id(docId)
                    .document(attributes));
            var response = instance.getClient().index(request);
            log.debug("Elasticsearch indexed doc id={} index={} result={}", docId,
                    instance.getIndex(), response.result());
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            log.error("Elasticsearch server error indexing doc id={} index={}: {}",
                    docId, instance.getIndex(), e.getMessage(), e);
        } catch (IOException e) {
            log.error("IO error indexing doc id={} index={}: {}",
                    docId, instance.getIndex(), e.getMessage(), e);
        }
    }

    public long getDocumentTotal(TurElasticsearchInstance instance) {
        try {
            return instance.getClient().count(c -> c.index(instance.getIndex())).count();
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (INDEX_NOT_FOUND_EXCEPTION.equals(e.error().type())) {
                log.debug("Index '{}' not found, returning 0 documents", instance.getIndex());
            } else {
                log.error("Error getting document count from Elasticsearch index '{}'", instance.getIndex(), e);
            }
            return 0L;
        } catch (IOException e) {
            log.error("Error getting document count from Elasticsearch index '{}'", instance.getIndex(), e);
            return 0L;
        }
    }

    /**
     * T388 — counts documents that populate {@code fieldName} via an
     * Elasticsearch {@code exists} query. Returns {@code -1} on error so
     * coverage callers can treat it as "unknown".
     */
    public long getDocumentTotalWithField(TurElasticsearchInstance instance, String fieldName) {
        try {
            return instance.getClient().count(c -> c.index(instance.getIndex())
                    .query(q -> q.exists(e -> e.field(fieldName)))).count();
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (INDEX_NOT_FOUND_EXCEPTION.equals(e.error().type())) {
                log.debug("Index '{}' not found, returning -1 for field '{}'",
                        instance.getIndex(), fieldName);
            } else {
                log.error("Error counting field '{}' in Elasticsearch index '{}'",
                        fieldName, instance.getIndex(), e);
            }
            return -1L;
        } catch (IOException e) {
            log.error("Error counting field '{}' in Elasticsearch index '{}'",
                    fieldName, instance.getIndex(), e);
            return -1L;
        }
    }

    /**
     * T472 — counts documents whose numeric {@code fieldName} value is strictly
     * below {@code threshold} via an Elasticsearch {@code range lt} query.
     * Returns {@code -1} on error so coverage callers can treat it as "unknown".
     */
    public long getDocumentTotalWithFieldBelow(TurElasticsearchInstance instance, String fieldName,
            int threshold) {
        try {
            return instance.getClient().count(c -> c.index(instance.getIndex())
                    .query(q -> q.range(r -> r.untyped(u -> u.field(fieldName)
                            .lt(co.elastic.clients.json.JsonData.of(threshold)))))).count();
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            if (INDEX_NOT_FOUND_EXCEPTION.equals(e.error().type())) {
                log.debug("Index '{}' not found, returning -1 for field '{}' range",
                        instance.getIndex(), fieldName);
            } else {
                log.error("Error range-counting field '{}' in Elasticsearch index '{}'",
                        fieldName, instance.getIndex(), e);
            }
            return -1L;
        } catch (IOException e) {
            log.error("Error range-counting field '{}' in Elasticsearch index '{}'",
                    fieldName, instance.getIndex(), e);
            return -1L;
        }
    }

    public void deIndexing(TurElasticsearchInstance instance, String id) {
        log.debug("Elasticsearch deIndexing id={}", id);
        try {
            instance.getClient().delete(d -> d.index(instance.getIndex()).id(id));
        } catch (IOException e) {
            log.error("Error deIndexing id={} from Elasticsearch", id, e);
        }
    }

    public void deIndexingByType(TurElasticsearchInstance instance, String type) {
        log.debug("Elasticsearch deIndexing type={}", type);
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(r -> r
                    .index(instance.getIndex())
                    .query(q -> q.term(t -> t.field(TurSNFieldName.TYPE).value(type))));
            instance.getClient().deleteByQuery(request);
        } catch (IOException e) {
            log.error("Error deIndexing type={} from Elasticsearch", type, e);
        }
    }

    private Query buildQuery(TurSEParameters turSEParameters) {
        String queryString = turSEParameters.getQuery();
        if (!StringUtils.hasText(queryString) || "*".equals(queryString.trim())) {
            // Match all query
            return Query.of(q -> q.matchAll(m -> m));
        }

        // simple_query_string with lenient=true skips fields whose type cannot
        // parse the query text (e.g. float, double, date fields)
        return Query.of(q -> q.simpleQueryString(qs -> qs
                .query(queryString)
                .defaultOperator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                .lenient(true)));
    }

    private int getStartPosition(TurSEParameters turSEParameters) {
        int currentPage = turSEParameters.getCurrentPage() > 0 ? turSEParameters.getCurrentPage() : 1;
        int rows = turSEParameters.getRows() > 0 ? turSEParameters.getRows() : 10;
        return (currentPage - 1) * rows;
    }

    private void addFacetAggregations(SearchRequest.Builder searchBuilder,
            List<TurSNSiteFieldExt> facetFields) {
        for (TurSNSiteFieldExt facetField : facetFields) {
            String fieldName = facetField.getName();
            String aggField = getFacetAggField(facetField);
            searchBuilder.aggregations(FACET_AGG_PREFIX + fieldName,
                    a -> a.terms(t -> t.field(aggField).size(100)));
        }
    }

    private void addOptionalSorting(SearchRequest.Builder searchBuilder, TurSEParameters turSEParameters) {
        String sort = turSEParameters.getSort();
        if (StringUtils.hasText(sort) && !"relevance".equalsIgnoreCase(sort)) {
            addSorting(searchBuilder, sort);
        }
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

    private void handleElasticsearchException(co.elastic.clients.elasticsearch._types.ElasticsearchException e,
            String index) {
        if (INDEX_NOT_FOUND_EXCEPTION.equals(e.error().type())) {
            log.debug("Index '{}' not found, returning empty results", index);
        } else {
            var err = e.error();
            log.error("ES search failed: type={} reason={} causedBy={}",
                    err.type(), err.reason(),
                    Optional.ofNullable(err.causedBy()).map(c -> c.reason()).orElse("none"));
            if (err.rootCause() != null && !err.rootCause().isEmpty()) {
                err.rootCause().forEach(
                        rc -> log.error("  root cause: type={} reason={}", rc.type(), rc.reason()));
            }
        }
    }

    private TurSEResults buildTurSEResults(@UnknownNullability SearchResponse<Map<String, Object>> response,
            TurSEParameters turSEParameters, List<TurSNSiteFieldExt> facetFields) {
        var total = response.hits().total();
        long numFound = total != null ? total.value() : 0L;
        int rows = turSEParameters.getRows() > 0 ? turSEParameters.getRows() : 10;
        int pageCount = (int) Math.ceil((double) numFound / rows);

        List<TurSEResult> results = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(source -> TurSEResult.builder().fields(source).build())
                .toList();

        List<TurSEFacetResult> facetResults = buildFacetResults(response, facetFields);
        log.debug("ES search returned numFound={} facetResults={}", numFound, facetResults.size());

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
                .facetResults(facetResults)
                .groups(Collections.emptyList())
                .build();
    }

    /**
     * Returns the ES field name to use for a terms aggregation.
     * String-like types (TEXT, STRING, ARRAY, BOOL) are dynamically mapped by ES as
     * "text"
     * with a ".keyword" sub-field. Terms aggregations must use the ".keyword"
     * sub-field.
     * Numeric types (INT, LONG, FLOAT, DOUBLE, CURRENCY) and DATE are mapped
     * natively
     * and can be aggregated directly.
     */
    private static String getFacetAggField(TurSNSiteFieldExt facetField) {
        // TEXT, STRING and ARRAY are all stored as "text" in ES (either via dynamic
        // mapping
        // on existing indexes, or explicit mapping with "keyword" analyzer for
        // STRING/ARRAY).
        // Both have a ".keyword" sub-field that must be used for terms aggregations.
        TurSEFieldType type = facetField.getType();
        if (type == TurSEFieldType.TEXT || type == TurSEFieldType.STRING
                || type == TurSEFieldType.ARRAY || type == TurSEFieldType.CURRENCY) {
            return facetField.getName() + ".keyword";
        }
        return facetField.getName();
    }

    private List<TurSEFacetResult> buildFacetResults(SearchResponse<Map<String, Object>> response,
            List<TurSNSiteFieldExt> facetFields) {
        List<TurSEFacetResult> facetResults = new ArrayList<>();
        Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregate> aggregations = response
                .aggregations();
        if (aggregations == null || facetFields.isEmpty()) {
            return facetResults;
        }
        for (TurSNSiteFieldExt facetField : facetFields) {
            String fieldName = facetField.getName();
            String aggName = FACET_AGG_PREFIX + fieldName;
            var agg = aggregations.get(aggName);
            if (agg == null) {
                log.debug("ES facet agg '{}' not found in response", aggName);
                continue;
            }
            TurSEFacetResult facetResult = createFacetResult(facetField, fieldName, agg);
            facetResults.add(facetResult);
        }
        return facetResults;
    }

    private TurSEFacetResult createFacetResult(TurSNSiteFieldExt facetField, String fieldName,
            co.elastic.clients.elasticsearch._types.aggregations.Aggregate agg) {
        TurSEFacetResult facetResult = new TurSEFacetResult();
        facetResult.setFacet(fieldName);
        facetResult.setFacetPosition(facetField.getFacetPosition() != null
                ? facetField.getFacetPosition()
                : 1);

        if (agg.isSterms()) {
            populateStringTermsBuckets(facetResult, fieldName, agg);
        } else if (agg.isDterms()) {
            populateDoubleTermsBuckets(facetResult, fieldName, agg);
        } else if (agg.isLterms()) {
            populateLongTermsBuckets(facetResult, fieldName, agg);
        } else {
            log.debug("ES facet field={} unhandled agg kind={}", fieldName, agg._kind());
        }
        return facetResult;
    }

    private void populateStringTermsBuckets(TurSEFacetResult facetResult, String fieldName,
            co.elastic.clients.elasticsearch._types.aggregations.Aggregate agg) {
        List<StringTermsBucket> buckets = agg.sterms().buckets().array();
        log.debug("ES facet field={} buckets={}", fieldName, buckets.size());
        for (StringTermsBucket bucket : buckets) {
            String key = bucket.key().stringValue();
            facetResult.add(key, new TurSEFacetResultAttr(key, (int) bucket.docCount()));
        }
    }

    private void populateDoubleTermsBuckets(TurSEFacetResult facetResult, String fieldName,
            co.elastic.clients.elasticsearch._types.aggregations.Aggregate agg) {
        List<DoubleTermsBucket> buckets = agg.dterms().buckets().array();
        log.debug("ES facet field={} dterms buckets={}", fieldName, buckets.size());
        for (DoubleTermsBucket bucket : buckets) {
            String key = bucket.keyAsString() != null
                    ? bucket.keyAsString()
                    : String.valueOf(bucket.key());
            facetResult.add(key, new TurSEFacetResultAttr(key, (int) bucket.docCount()));
        }
    }

    private void populateLongTermsBuckets(TurSEFacetResult facetResult, String fieldName,
            co.elastic.clients.elasticsearch._types.aggregations.Aggregate agg) {
        List<LongTermsBucket> buckets = agg.lterms().buckets().array();
        log.debug("ES facet field={} lterms buckets={}", fieldName, buckets.size());
        for (LongTermsBucket bucket : buckets) {
            String key = bucket.keyAsString() != null
                    ? bucket.keyAsString()
                    : String.valueOf(bucket.key());
            facetResult.add(key, new TurSEFacetResultAttr(key, (int) bucket.docCount()));
        }
    }
}
