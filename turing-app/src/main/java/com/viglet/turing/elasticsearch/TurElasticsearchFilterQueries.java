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
package com.viglet.turing.elasticsearch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.lucene.TurLuceneCustomFacets;
import com.viglet.turing.lucene.TurLuceneFilterQueries;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.UntypedRangeQuery;
import co.elastic.clients.json.JsonData;

/**
 * T1106 — the request's facet filters ({@code fq}, {@code fqAnd}, {@code fqOr}
 * and their operators) as an Elasticsearch filter clause, with the semantics the
 * Lucene engine applies (T1077) so a site answers the same request the same way
 * on either:
 * <ul>
 *   <li>selections are grouped by field;</li>
 *   <li>the values of one field union or intersect by {@code fqItemOperator},
 *       else by the field's facet item type, else the site's;</li>
 *   <li>plain {@code fq} fields combine by {@code fqOperator}, {@code fqAnd}
 *       fields always intersect and {@code fqOr} fields always union, and the
 *       intersected groups are OR'd with the union groups.</li>
 * </ul>
 *
 * <p>A value is read by its field's type: a string-like field matches its
 * {@code .keyword} sub-field (the one the facet aggregation counts), a number or
 * date its own field; {@code [from TO to]} is a range, and a date bound may be
 * Solr date math ({@code NOW-7DAYS}), resolved by the same parser Lucene uses. A
 * selection on a name the site has no field for is a custom facet's
 * {@code <facet>:<label>} and stands for its item's range (T1083).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
final class TurElasticsearchFilterQueries {

    private static final Pattern RANGE = Pattern.compile("^([\\[{])\\s*(\\S+)\\s+TO\\s+(\\S+)\\s*([]}])$");
    private static final String KEYWORD = ".keyword";

    private TurElasticsearchFilterQueries() {
    }

    /** One field's selected values and how the field combines with the others. */
    private record Group(String field, TurSNSiteFacetFieldEnum facetType, List<String> values) {
    }

    /**
     * The filter clause for {@code params}, or empty when they select nothing.
     *
     * @param fieldsByName the site's enabled fields by name
     * @param customFacet  resolves a {@code facet, label} pair to its item's
     *                     filter; empty when the name is no custom facet
     */
    static Optional<Query> build(TurSNSite site, TurSNFilterParams params,
            Map<String, TurSNSiteFieldExt> fieldsByName,
            BiFunction<String, String, Optional<TurLuceneCustomFacets.ItemFilter>> customFacet, Instant now) {
        if (params == null) {
            return Optional.empty();
        }
        TurSNSiteFacetFieldEnum defaultType = params.getOperator() == TurSNFilterQueryOperator.OR
                ? TurSNSiteFacetFieldEnum.OR
                : TurSNSiteFacetFieldEnum.AND;
        Map<String, Group> groups = new LinkedHashMap<>();
        collect(groups, params.getDefaultValues(), defaultType);
        collect(groups, params.getAnd(), TurSNSiteFacetFieldEnum.AND);
        collect(groups, params.getOr(), TurSNSiteFacetFieldEnum.OR);
        if (groups.isEmpty()) {
            return Optional.empty();
        }
        List<Query> intersected = new ArrayList<>();
        List<Query> united = new ArrayList<>();
        for (Group group : groups.values()) {
            TurSNSiteFieldExt ext = fieldsByName.get(group.field());
            TurSNSiteFacetFieldEnum itemType = requestedItemType(params.getItemOperator())
                    .orElseGet(() -> effectiveItemType(ext, site));
            List<Query> values = group.values().stream()
                    .map(value -> valueQuery(group.field(), ext, value, customFacet, now))
                    .toList();
            Query fieldQuery = values.size() == 1 ? values.getFirst()
                    : itemType == TurSNSiteFacetFieldEnum.OR
                            ? Query.of(q -> q.bool(b -> b.should(values).minimumShouldMatch("1")))
                            : Query.of(q -> q.bool(b -> b.filter(values)));
            (group.facetType() == TurSNSiteFacetFieldEnum.OR ? united : intersected).add(fieldQuery);
        }
        if (united.isEmpty()) {
            return Optional.of(Query.of(q -> q.bool(b -> b.filter(intersected))));
        }
        List<Query> branches = new ArrayList<>(united);
        if (!intersected.isEmpty()) {
            branches.addFirst(Query.of(q -> q.bool(b -> b.filter(intersected))));
        }
        return Optional.of(Query.of(q -> q.bool(b -> b.should(branches).minimumShouldMatch("1"))));
    }

    private static void collect(Map<String, Group> groups, List<String> filterQueries,
            TurSNSiteFacetFieldEnum facetType) {
        if (filterQueries == null) {
            return;
        }
        for (String fq : filterQueries) {
            int colon = fq == null ? -1 : fq.indexOf(':');
            if (colon <= 0 || colon >= fq.length() - 1) {
                continue;
            }
            String value = fq.substring(colon + 1);
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            groups.computeIfAbsent(fq.substring(0, colon), field -> new Group(field, facetType, new ArrayList<>()))
                    .values().add(value);
        }
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

    /** Field-level {@code facetItemType} wins over the site default; DEFAULT/null is AND. */
    private static TurSNSiteFacetFieldEnum effectiveItemType(TurSNSiteFieldExt ext, TurSNSite site) {
        TurSNSiteFacetFieldEnum fieldType = ext != null ? ext.getFacetItemType() : null;
        if (fieldType != null && fieldType != TurSNSiteFacetFieldEnum.DEFAULT) {
            return fieldType;
        }
        TurSNSiteFacetFieldEnum siteType = site == null ? null : site.getFacetItemType();
        return siteType == null || siteType == TurSNSiteFacetFieldEnum.DEFAULT ? TurSNSiteFacetFieldEnum.AND
                : siteType;
    }

    private static Query valueQuery(String field, TurSNSiteFieldExt ext, String value,
            BiFunction<String, String, Optional<TurLuceneCustomFacets.ItemFilter>> customFacet, Instant now) {
        if (ext == null) {
            Optional<TurLuceneCustomFacets.ItemFilter> item = customFacet.apply(field, value);
            if (item.isPresent()) {
                return valueQuery(item.get().field(), item.get().type(), item.get().value(), now);
            }
        }
        return valueQuery(field, ext == null ? null : ext.getType(), value, now);
    }

    static Query valueQuery(String field, TurSEFieldType type, String value, Instant now) {
        String target = isKeyword(type) ? field + KEYWORD : field;
        Matcher range = RANGE.matcher(value.trim());
        if (range.matches()) {
            return rangeQuery(target, type, range, now);
        }
        String term = type == TurSEFieldType.DATE ? TurLuceneFilterQueries.parseDate(value.trim(), now).toString()
                : value;
        return Query.of(q -> q.term(t -> t.field(target).value(FieldValue.of(term))));
    }

    private static Query rangeQuery(String field, TurSEFieldType type, Matcher range, Instant now) {
        boolean fromInclusive = "[".equals(range.group(1));
        boolean toInclusive = "]".equals(range.group(4));
        String from = bound(range.group(2), type, now);
        String to = bound(range.group(3), type, now);
        UntypedRangeQuery.Builder builder = new UntypedRangeQuery.Builder().field(field);
        if (from != null) {
            if (fromInclusive) {
                builder.gte(JsonData.of(from));
            } else {
                builder.gt(JsonData.of(from));
            }
        }
        if (to != null) {
            if (toInclusive) {
                builder.lte(JsonData.of(to));
            } else {
                builder.lt(JsonData.of(to));
            }
        }
        UntypedRangeQuery untyped = builder.build();
        return Query.of(q -> q.range(r -> r.untyped(untyped)));
    }

    /** A range bound as Elasticsearch reads it: {@code *} is open, a date is an ISO instant. */
    private static String bound(String value, TurSEFieldType type, Instant now) {
        if (!StringUtils.hasText(value) || "*".equals(value)) {
            return null;
        }
        return type == TurSEFieldType.DATE ? TurLuceneFilterQueries.parseDate(value, now).toString() : value;
    }

    /** String-like fields are mapped as text with a {@code .keyword} sub-field, as the facet aggregation reads them. */
    static boolean isKeyword(TurSEFieldType type) {
        return type == null || type == TurSEFieldType.TEXT || type == TurSEFieldType.STRING
                || type == TurSEFieldType.ARRAY || type == TurSEFieldType.CURRENCY;
    }
}
