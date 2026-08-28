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
package com.viglet.turing.contentai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.properties.TurContentAiProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * T1031 / §XX.18 — translates a Turing search into the body Adobe's
 * {@code POST /content-sources/search} expects.
 *
 * <p>The mapping is the whole of the federation, so it is worth naming what each
 * side calls things. A Turing {@code q} becomes Adobe's hybrid shape: a
 * {@code composite} query over {@code OR} holding a {@code vector} arm and a
 * {@code fulltext} arm, which is the composition Adobe's own example uses. A
 * facet selection ({@code fq=field:value}) becomes a {@code FilterGroup}: values
 * of one field union, fields intersect, exactly as
 * {@code TurLuceneQueryBuilder.addFilterQueries} does — and where the site
 * declares a facet as single-select, an {@code AND} of {@code StringFilter EQ}
 * rather than an {@code InFilter}, since {@code InFilter} is a union by
 * definition. A facet field becomes a {@code termBuckets} aggregate keyed by the
 * field name, so the response can be read back without a side table.
 *
 * <p>Two things do not map and are clamped rather than silently wrong.
 * {@code limit} is capped at 50 by the API, so a Turing page asking for more
 * gets 50 and a debug line. And there is no offset at all — pagination is an
 * opaque cursor — so the caller walks cursors and this class only accepts the
 * one it was handed.
 *
 * <p>T1049 — two more of the spec's bounds are enforced here, both on values a
 * site's own configuration supplies and nothing else limits: the
 * {@code aggregates} array takes at most {@link #MAX_AGGREGATES} entries, and a
 * term bucket's {@code size} at most {@link #MAX_BUCKET_SIZE}. Both clamp for
 * {@code limit}'s reason — a site with 51 facet fields asking for one facet too
 * many should lose that facet, not every search it runs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurContentAiQueryBuilder {

    /** {@code PaginationOptions.limit} carries {@code maximum: 50} in the spec. */
    public static final int MAX_LIMIT = 50;

    /** The {@code aggregates} array carries {@code maxItems: 50}. */
    public static final int MAX_AGGREGATES = 50;

    /** {@code TermBucketAggregatesConfig.size} carries {@code maximum: 2000}. */
    public static final int MAX_BUCKET_SIZE = 2000;

    private static final int DEFAULT_LIMIT = 10;

    /** {@code TermBucketAggregatesConfig.size} declares {@code default: 10}. */
    private static final int DEFAULT_BUCKET_SIZE = 10;

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_FIELD = "field";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_OPTIONS = "options";
    private static final String FIELD_BOOST = "boost";
    private static final String FIELD_FILTERS = "filters";
    private static final String FIELD_OPERATOR = "operator";
    private static final String FIELD_SPACE = "space";
    private static final String TYPE_FILTER_GROUP = "FilterGroup";
    private static final String OPERATOR_AND = "AND";
    private static final String OPERATOR_OR = "OR";
    private static final String OPERATOR_NOT = "NOT";

    /**
     * T1047 — the field name whose <em>absence</em> stands for "every document".
     * Chosen to be one no source could declare, so {@code NOT exists(sentinel)}
     * cannot stop being universally true.
     */
    public static final String MATCH_ALL_SENTINEL = "__turing_match_all__";

    private final JsonNodeFactory nodes = JsonNodeFactory.instance;

    /**
     * The full search body: content source, hybrid or lexical query, active facet
     * filters, pagination and one term-bucket aggregate per facet field.
     *
     * @param sourceName  Adobe content source to search
     * @param params      the Turing search parameters
     * @param facetFields the site's enabled facet fields, in facet-position order
     * @param cursor      the cursor from the previous page, or {@code null} for the first
     * @param limit       rows to ask for; clamped to {@link #MAX_LIMIT}
     */
    public ObjectNode buildSearchRequest(String sourceName, TurSNSite site, TurSEParameters params,
            List<TurSNSiteFieldExt> facetFields, String cursor, int limit,
            TurContentAiProperty property) {
        ObjectNode body = nodes.objectNode();
        body.set("contentSource", contentSource(sourceName, property));
        body.set("query", buildQuery(site, params, byName(facetFields), property));
        body.set("queryOptions", queryOptions(params, cursor, limit));
        if (!facetFields.isEmpty()) {
            body.set("aggregates", aggregates(facetFields, property.getFacetSize()));
        }
        return body;
    }

    private static Map<String, TurSNSiteFieldExt> byName(List<TurSNSiteFieldExt> fields) {
        Map<String, TurSNSiteFieldExt> byName = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach(field -> byName.putIfAbsent(field.getName(), field));
        }
        return byName;
    }

    /**
     * The aggregate-only body behind {@code retrieveFacetResults}: the same query
     * and filters, {@code limit: 0} so Adobe computes buckets without returning
     * documents, and a single aggregate for the named field.
     */
    public ObjectNode buildFacetRequest(String sourceName, TurSNSite site, TurSEParameters params,
            String facetName, TurSNSiteFieldExt facetField, TurContentAiProperty property) {
        ObjectNode body = nodes.objectNode();
        body.set("contentSource", contentSource(sourceName, property));
        body.set("query", buildQuery(site, params,
                byName(facetField != null ? List.of(facetField) : List.of()), property));
        body.set("queryOptions", queryOptions(params, null, 0));
        ArrayNode aggregates = nodes.arrayNode();
        aggregates.add(termBucketAggregate(facetName, property.getFacetSize()));
        body.set("aggregates", aggregates);
        if (facetField == null) {
            log.debug("Content AI facet '{}' is not a declared field on site '{}'; "
                    + "asking anyway — Adobe returns an empty bucket list for an "
                    + "unindexed field", facetName, site != null ? site.getName() : "?");
        }
        return body;
    }

    /**
     * The counting body behind {@code getDocumentTotal}: match everything,
     * return nothing, read {@code totalResults}. {@code limit: 0} with no
     * aggregates is the cheapest request the API accepts that still carries a
     * total.
     */
    public ObjectNode buildCountRequest(String sourceName, TurContentAiProperty property) {
        ObjectNode body = nodes.objectNode();
        body.set("contentSource", contentSource(sourceName, property));
        body.set("query", matchAllQuery(property));
        ObjectNode options = nodes.objectNode();
        ObjectNode pagination = nodes.objectNode();
        pagination.put("limit", 0);
        options.set("pagination", pagination);
        body.set("queryOptions", options);
        return body;
    }

    // ---- query ----------------------------------------------------------

    /**
     * {@code contentSource}: the name plus the type, which the spec defaults to
     * {@code ACQUISITION} and which an AEM-backed source must override — a
     * request naming an {@code AEM_PUBLISH} source without saying so resolves
     * against the wrong index rather than failing.
     */
    private ObjectNode contentSource(String sourceName, TurContentAiProperty property) {
        ObjectNode source = nodes.objectNode();
        source.put(FIELD_NAME, sourceName);
        if (StringUtils.isNotBlank(property.getSourceType())) {
            source.put(FIELD_TYPE, property.getSourceType());
        }
        return source;
    }

    private ObjectNode buildQuery(TurSNSite site, TurSEParameters params,
            Map<String, TurSNSiteFieldExt> facetFieldsByName, TurContentAiProperty property) {
        String raw = params != null ? params.getQuery() : null;
        String text = raw != null ? raw.trim() : "";
        ObjectNode filter = buildFilter(site, params, facetFieldsByName);

        if (text.isEmpty() || "*".equals(text) || "*:*".equals(text)) {
            return filter != null ? filterQuery(filter, property) : matchAllQuery(property);
        }

        List<ObjectNode> arms = new ArrayList<>();
        if (property.isHybrid()) {
            arms.add(vectorArm(text, filter, property));
        }
        arms.add(fulltextArm(text, filter, property));

        if (arms.size() == 1) {
            return arms.getFirst();
        }
        ObjectNode composite = nodes.objectNode();
        composite.put(FIELD_TYPE, "composite");
        composite.put(FIELD_OPERATOR, OPERATOR_OR);
        ArrayNode queries = nodes.arrayNode();
        arms.forEach(queries::add);
        composite.set("queries", queries);
        return composite;
    }

    private ObjectNode vectorArm(String text, ObjectNode filter, TurContentAiProperty property) {
        ObjectNode query = nodes.objectNode();
        query.put(FIELD_TYPE, "vector");
        query.put(FIELD_TEXT, text);
        ObjectNode options = nodes.objectNode();
        options.put(FIELD_BOOST, property.getVectorBoost());
        space(property.getVectorSpace()).ifPresent(s -> options.set("vectorSpaceSelection", s));
        if (property.isIncludeChunks()) {
            ObjectNode chunks = nodes.objectNode();
            chunks.put("includeInResponse", true);
            chunks.put("scoreMode", "MAX");
            options.set("chunks", chunks);
        }
        query.set(FIELD_OPTIONS, options);
        if (filter != null) {
            query.set("filter", filter);
        }
        return query;
    }

    private ObjectNode fulltextArm(String text, ObjectNode filter, TurContentAiProperty property) {
        ObjectNode query = nodes.objectNode();
        query.put(FIELD_TYPE, "fulltext");
        query.put(FIELD_TEXT, text);
        ObjectNode options = nodes.objectNode();
        options.put(FIELD_BOOST, property.getFulltextBoost());
        space(property.getLexicalSpace()).ifPresent(s -> options.set("lexicalSpaceSelection", s));
        query.set(FIELD_OPTIONS, options);
        if (filter != null) {
            query.set("filter", filter);
        }
        return query;
    }

    /**
     * A "match everything" stand-in. The API has no match-all query type, so this
     * is built out of the filter DSL.
     *
     * <p>T1047 — it used to be an empty {@code FilterGroup} over {@code AND}, on the
     * reasoning that no constraints means no exclusions, the way SQL's
     * {@code WHERE 1=1} works. That is <b>invalid</b>: {@code FilterGroup.filters}
     * carries {@code minItems: 1}, so Adobe refuses the request. Nothing caught it
     * because the only callers were {@code getDocumentTotal} and a blank query, and
     * the stub in the tests accepts any body.
     *
     * <p>What works is the negation of an impossibility: {@code NOT} over
     * "documents that have a field called {@value #MATCH_ALL_SENTINEL}". No
     * document has that field, so its negation is every document — one filter, and
     * the group is legal. The sentinel is deliberately unusable as a real field
     * name so the predicate cannot become accidentally true.
     */
    private ObjectNode matchAllQuery(TurContentAiProperty property) {
        ObjectNode exists = nodes.objectNode();
        exists.put(FIELD_TYPE, "ExistsFilter");
        exists.put(FIELD_FIELD, MATCH_ALL_SENTINEL);

        ObjectNode group = nodes.objectNode();
        group.put(FIELD_TYPE, TYPE_FILTER_GROUP);
        group.put(FIELD_OPERATOR, OPERATOR_NOT);
        ArrayNode filters = nodes.arrayNode();
        filters.add(exists);
        group.set(FIELD_FILTERS, filters);

        ObjectNode wrapper = nodes.objectNode();
        wrapper.set("filterComponent", group);
        return filterQuery(wrapper, property);
    }

    private ObjectNode filterQuery(ObjectNode filter, TurContentAiProperty property) {
        ObjectNode query = nodes.objectNode();
        query.put(FIELD_TYPE, "filter");
        query.set("filter", filter);
        ObjectNode options = nodes.objectNode();
        space(property.getLexicalSpace()).ifPresent(s -> options.set("lexicalSpaceSelection", s));
        if (!options.isEmpty()) {
            query.set(FIELD_OPTIONS, options);
        }
        return query;
    }

    private Optional<ObjectNode> space(String name) {
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        ObjectNode selection = nodes.objectNode();
        selection.put(FIELD_SPACE, name);
        return Optional.of(selection);
    }

    // ---- filters --------------------------------------------------------

    /**
     * The active facet selections as a {@code ContentAIQueryFilter}, or
     * {@code null} when nothing is selected. Values of one field union; fields
     * intersect — a selection in one field narrows another, which is what
     * faceted navigation promises and what the counts on screen claim.
     */
    private ObjectNode buildFilter(TurSNSite site, TurSEParameters params,
            Map<String, TurSNSiteFieldExt> facetFieldsByName) {
        if (params == null || params.getTurSNFilterParams() == null) {
            return null;
        }
        List<String> selections = params.getTurSNFilterParams().getDefaultValues();
        if (selections == null || selections.isEmpty()) {
            return null;
        }
        Map<String, List<String>> valuesByField = new LinkedHashMap<>();
        for (String selection : selections) {
            parseFieldValue(selection).ifPresent(fv -> valuesByField
                    .computeIfAbsent(fv.field(), k -> new ArrayList<>()).add(fv.value()));
        }
        if (valuesByField.isEmpty()) {
            return null;
        }

        ArrayNode fieldGroups = nodes.arrayNode();
        valuesByField.forEach((field, values) -> fieldGroups.add(fieldFilter(field, values,
                itemTypeIsUnion(site, facetFieldsByName.get(field)))));

        ObjectNode group = nodes.objectNode();
        group.put(FIELD_TYPE, TYPE_FILTER_GROUP);
        group.put(FIELD_OPERATOR, OPERATOR_AND);
        group.set(FIELD_FILTERS, fieldGroups);

        ObjectNode wrapper = nodes.objectNode();
        wrapper.set("filterComponent", group);
        return wrapper;
    }

    private ObjectNode fieldFilter(String field, List<String> values, boolean union) {
        if (values.size() == 1) {
            return stringEquals(field, values.getFirst());
        }
        if (union) {
            ObjectNode in = nodes.objectNode();
            in.put(FIELD_TYPE, "InFilter");
            in.put(FIELD_FIELD, field);
            ArrayNode array = nodes.arrayNode();
            values.forEach(array::add);
            in.set("values", array);
            return in;
        }
        ObjectNode group = nodes.objectNode();
        group.put(FIELD_TYPE, TYPE_FILTER_GROUP);
        group.put(FIELD_OPERATOR, OPERATOR_AND);
        ArrayNode filters = nodes.arrayNode();
        values.forEach(value -> filters.add(stringEquals(field, value)));
        group.set(FIELD_FILTERS, filters);
        return group;
    }

    private ObjectNode stringEquals(String field, String value) {
        ObjectNode filter = nodes.objectNode();
        filter.put(FIELD_TYPE, "StringFilter");
        filter.put(FIELD_FIELD, field);
        filter.put("value", value);
        filter.put(FIELD_OPERATOR, "EQ");
        return filter;
    }

    /**
     * Whether multiple selected values of {@code field} union. Mirrors
     * {@code TurLuceneQueryBuilder.effectiveFacetItemType}: the field's own
     * setting wins, {@code DEFAULT} falls back to the site's, and a site that
     * declares nothing intersects.
     */
    private boolean itemTypeIsUnion(TurSNSite site, TurSNSiteFieldExt facetField) {
        TurSNSiteFacetFieldEnum fieldType =
                facetField != null ? facetField.getFacetItemType() : null;
        if (fieldType != null && fieldType != TurSNSiteFacetFieldEnum.DEFAULT) {
            return fieldType == TurSNSiteFacetFieldEnum.OR;
        }
        TurSNSiteFacetFieldEnum siteType = site != null ? site.getFacetItemType() : null;
        return siteType == TurSNSiteFacetFieldEnum.OR;
    }

    private Optional<FieldValue> parseFieldValue(String fq) {
        if (StringUtils.isBlank(fq)) {
            return Optional.empty();
        }
        int colon = fq.indexOf(':');
        if (colon <= 0 || colon >= fq.length() - 1) {
            log.warn("Invalid Content AI filter '{}' — expected 'field:value'", fq);
            return Optional.empty();
        }
        String field = fq.substring(0, colon);
        String value = fq.substring(colon + 1);
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return Optional.of(new FieldValue(field, value));
    }

    private record FieldValue(String field, String value) {
    }

    // ---- options and aggregates ------------------------------------------

    private ObjectNode queryOptions(TurSEParameters params, String cursor, int limit) {
        ObjectNode options = nodes.objectNode();
        ObjectNode pagination = nodes.objectNode();
        pagination.put("limit", clampLimit(limit));
        if (StringUtils.isNotBlank(cursor)) {
            pagination.put("cursor", cursor);
        }
        options.set("pagination", pagination);
        sort(params).ifPresent(sort -> options.set("sort", sort));
        return options;
    }

    /**
     * Turing sorts as {@code "field asc"}, which is byte-for-byte what Adobe's
     * {@code orderBy} takes, so this passes the string through. {@code relevance}
     * is the one value that does not: Adobe's default sort <em>is</em> descending
     * score, so the honest translation is to send no sort at all.
     */
    private Optional<ArrayNode> sort(TurSEParameters params) {
        String raw = params != null ? params.getSort() : null;
        String sort = raw != null ? raw.trim() : "";
        if (sort.isEmpty() || "relevance".equalsIgnoreCase(sort)) {
            return Optional.empty();
        }
        ObjectNode entry = nodes.objectNode();
        entry.put("orderBy", sort);
        ArrayNode array = nodes.arrayNode();
        array.add(entry);
        return Optional.of(array);
    }

    /**
     * One {@code termBuckets} aggregate per facet field, up to what the API takes.
     *
     * <p>The array carries {@code maxItems: 50}, and a site's facet count is an
     * admin decision nothing bounds — so a site declaring 51 facet fields would
     * emit a body Adobe refuses, losing the whole search rather than one facet.
     * Dropping the surplus is the same trade {@link #clampLimit} makes: the fields
     * are in facet-position order, so what survives is what the site put first.
     */
    private ArrayNode aggregates(List<TurSNSiteFieldExt> facetFields, int size) {
        ArrayNode array = nodes.arrayNode();
        if (facetFields.size() > MAX_AGGREGATES) {
            log.warn("Content AI takes at most {} aggregates; site declares {} facet fields, "
                    + "so the last {} are not requested", MAX_AGGREGATES, facetFields.size(),
                    facetFields.size() - MAX_AGGREGATES);
        }
        facetFields.stream().limit(MAX_AGGREGATES)
                .forEach(field -> array.add(termBucketAggregate(field.getName(), size)));
        return array;
    }

    /**
     * One {@code termBuckets} aggregate. The {@code id} is the field name so the
     * response can be read back by field without carrying a side map — the spec
     * only requires the id to be unique within the request.
     */
    private ObjectNode termBucketAggregate(String fieldName, int size) {
        ObjectNode aggregate = nodes.objectNode();
        aggregate.put(FIELD_TYPE, "termBuckets");
        aggregate.put("id", fieldName);
        ObjectNode field = nodes.objectNode();
        field.put(FIELD_NAME, fieldName);
        aggregate.set(FIELD_FIELD, field);
        aggregate.put("size", clampBucketSize(size));
        return aggregate;
    }

    /**
     * Clamps {@code size} to what a term-bucket aggregate accepts.
     *
     * <p>The value is {@code turing.contentai.facet-size} on the plugin path and
     * the DSL request's own {@code terms.size} on the other, so both ends are
     * caller input. A non-positive one means "the API default", which the spec
     * spells as 10 — sending {@code 0} would ask for no buckets at all, which is
     * not what an unset facet size means.
     */
    public int clampBucketSize(int size) {
        if (size <= 0) {
            return DEFAULT_BUCKET_SIZE;
        }
        if (size > MAX_BUCKET_SIZE) {
            log.debug("Content AI caps a term bucket size at {}; {} was requested",
                    MAX_BUCKET_SIZE, size);
            return MAX_BUCKET_SIZE;
        }
        return size;
    }

    /**
     * Clamps to what the API accepts. A blank or non-positive row count means the
     * caller wants the API default; anything over 50 is refused by Adobe, so
     * asking for it wastes a round trip.
     */
    public int clampLimit(int limit) {
        if (limit < 0) {
            return DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            log.debug("Content AI caps pagination.limit at {}; {} rows were requested", MAX_LIMIT,
                    limit);
            return MAX_LIMIT;
        }
        return limit;
    }
}
