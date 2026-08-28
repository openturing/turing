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
package com.viglet.turing.sn.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.viglet.turing.api.exception.TurUnsupportedEngineException;
import com.viglet.turing.contentai.TurContentAiClient;
import com.viglet.turing.contentai.TurContentAiQueryBuilder;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.contentai.TurContentAiSearchEnginePlugin;
import com.viglet.turing.properties.TurContentAiProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * T1047 / §XX.24 — the DSL executor for an Adobe AEM Content AI index, which is
 * what makes the vectorless copilot reach a federated site.
 *
 * <p>Why this exists at all: the copilot
 * ({@code TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED}) needs no embeddings —
 * prose becomes a grounded DSL query over the site's declared field schema, the
 * engine executes it, and the LLM answers strictly from the cited rows. That is
 * exactly the shape a federated index supports, and it was the one answer surface
 * a Content AI site could not use, because everything the copilot does goes
 * through this layer.
 *
 * <p><b>It implements the DSL that is actually generated, and refuses the rest by
 * name.</b> The copilot emits six clause types — {@code match_all}, {@code match},
 * {@code term}, {@code terms}, {@code range}, {@code bool} — plus {@code terms}
 * aggregations, and this translates all of them. The other thirty-odd
 * ({@code knn}, {@code nested}, {@code geo_distance}, {@code function_score},
 * span queries, joins) have no expression in Content AI's query DSL, and a
 * translator that quietly dropped a clause would answer confidently from the wrong
 * document set. So each is refused with the clause named, which is the same
 * bargain the plugin's write paths take.
 *
 * <p>Two structural mismatches shape the translation and are worth stating before
 * the code:
 *
 * <ul>
 *   <li><b>Scoring and filtering are separate query types.</b> Elasticsearch's
 *       {@code bool} mixes them freely: a {@code must} clause both filters and
 *       scores. Content AI has a {@code filter} query (no scoring) and
 *       {@code vector}/{@code fulltext} queries (scoring), composed by
 *       {@code composite}. So a {@code bool} is split: its text-matching clauses
 *       become the composite's arms, and everything else becomes one
 *       {@code FilterGroup} attached as their pre-filter. A {@code bool} of pure
 *       filters collapses to a single {@code filter} query.</li>
 *   <li><b>There is no offset.</b> Pagination is an opaque cursor, so a non-zero
 *       {@code from} cannot be honoured in one request. Rather than return the
 *       first page while the caller believes it asked for the third, it is
 *       refused. The copilot always asks for one page, so this costs it nothing.</li>
 * </ul>
 *
 * <p>T1049 — the spec's array bounds are enforced here rather than discovered as a
 * 400. Three of them are reachable from a DSL query: {@code FilterGroup.filters}
 * (25), {@code composite.queries} (10) and the {@code aggregates} array (50). All
 * three are refused by name, because each counts something the query asked for and
 * answering with a subset would be a plausible answer to a different question. A
 * term bucket's {@code size} is the exception and is clamped: it is a ceiling on
 * what comes back, so asking for more buckets than exist is not an error.
 *
 * <p>T1051 — the total bucket count across every aggregate is refused the same way.
 * That one is prose in the spec rather than a schema keyword, so no validator can
 * reach it and Adobe fails the whole request over it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurDslContentAiExecutor implements TurDslSearchEngineExecutor<ObjectNode, JsonNode> {

    private static final String ENGINE = TurContentAiSearchEnginePlugin.PLUGIN_TYPE;

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_FIELD = "field";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_OPERATOR = "operator";
    private static final String FIELD_FILTERS = "filters";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_OPTIONS = "options";
    private static final String TYPE_FILTER_GROUP = "FilterGroup";
    private static final String TYPE_STRING_FILTER = "StringFilter";
    private static final String TYPE_NUMBER_FILTER = "NumberFilter";
    private static final String TYPE_BOOLEAN_FILTER = "BooleanFilter";
    private static final String OP_EQ = "EQ";
    private static final String OP_AND = "AND";
    private static final String OP_OR = "OR";
    private static final String OP_NOT = "NOT";

    /** {@code FilterGroup.filters} carries {@code maxItems: 25}. */
    private static final int MAX_GROUP_FILTERS = 25;

    /** {@code CompositeQuery.queries} carries {@code maxItems: 10}. */
    private static final int MAX_COMPOSITE_ARMS = 10;

    /** The {@code aggregates} array carries {@code maxItems: 50}. */
    private static final int MAX_AGGREGATES = TurContentAiQueryBuilder.MAX_AGGREGATES;

    /** A bound carrying no time, which {@code DateFilter}'s {@code date-time} refuses. */
    private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final JsonNodeFactory nodes = JsonNodeFactory.instance;

    private final TurContentAiClient client;
    private final TurContentAiQueryBuilder queryBuilder;
    private final TurContentAiProperty property;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    public TurDslContentAiExecutor(TurContentAiClient client,
            TurContentAiQueryBuilder queryBuilder, TurContentAiProperty property,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository) {
        this.client = client;
        this.queryBuilder = queryBuilder;
        this.property = property;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
    }

    @Override
    public String getEngineType() {
        return ENGINE;
    }

    // ---- execute ---------------------------------------------------------

    @Override
    public Optional<TurDslSearchResponse> execute(String siteName, Locale locale,
            TurDslQueryRequest request) {
        if (!property.isEnabled()) {
            log.warn("A DSL query reached the '{}' executor but turing.content-ai.enabled is "
                    + "false", ENGINE);
            return Optional.empty();
        }
        return turSNSiteRepository.findByNameIgnoreCase(siteName).flatMap(site -> {
            TurSNSiteLocale siteLocale =
                    turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
            if (siteLocale == null) {
                log.warn("Site '{}' has no '{}' locale", siteName, locale);
                return Optional.empty();
            }
            long startedAt = System.nanoTime();
            try {
                JsonNode response = client.search(endpointUrl(site),
                        buildBody(site, siteLocale, request), property);
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                return Optional.of(buildResponse(response, request, elapsedMs));
            } catch (TurContentAiClient.TurContentAiException e) {
                log.error("Content AI DSL query failed for site '{}'", siteName, e);
                return Optional.empty();
            }
        });
    }

    /**
     * The full search body: content source, the translated query, pagination and
     * any {@code terms} aggregations.
     */
    private ObjectNode buildBody(TurSNSite site, TurSNSiteLocale siteLocale,
            TurDslQueryRequest request) {
        ObjectNode body = nodes.objectNode();
        body.set("contentSource", contentSource(site, siteLocale));
        body.set("query", translateQuery(request.query()));
        body.set("queryOptions", queryOptions(request));
        ArrayNode aggregates = translateAggregations(request.aggs());
        if (aggregates != null) {
            body.set("aggregates", aggregates);
        }
        return body;
    }

    private ObjectNode contentSource(TurSNSite site, TurSNSiteLocale siteLocale) {
        String configured = property.getSources().get(site.getName());
        ObjectNode source = nodes.objectNode();
        source.put("name", StringUtils.isNotBlank(configured) ? configured : siteLocale.getCore());
        if (StringUtils.isNotBlank(property.getSourceType())) {
            source.put(FIELD_TYPE, property.getSourceType());
        }
        return source;
    }

    private ObjectNode queryOptions(TurDslQueryRequest request) {
        Integer from = request.from();
        if (from != null && from > 0) {
            // Honouring this would mean walking the cursor `from/size` times inside
            // one DSL call; returning page one instead would be a wrong answer the
            // caller could not detect.
            throw refuse("The DSL 'from' offset (Content AI paginates by cursor only)");
        }
        ObjectNode options = nodes.objectNode();
        ObjectNode pagination = nodes.objectNode();
        Integer size = request.size();
        pagination.put("limit", queryBuilder.clampLimit(size != null && size >= 0 ? size : 10));
        options.set("pagination", pagination);
        sort(request.sort()).ifPresent(entries -> options.set("sort", entries));
        return options;
    }

    /**
     * Elasticsearch sorts as {@code [{"field":"asc"}]} or {@code ["field"]}; Adobe
     * takes {@code orderBy: "field asc"}. {@code _score} is dropped rather than
     * translated: Adobe's default sort <em>is</em> descending score, so sending it
     * as a field name would ask to sort by a field called {@code _score}.
     */
    private Optional<ArrayNode> sort(List<Object> sortEntries) {
        if (sortEntries == null || sortEntries.isEmpty()) {
            return Optional.empty();
        }
        ArrayNode array = nodes.arrayNode();
        for (Object entry : sortEntries) {
            String orderBy = orderBy(entry);
            if (orderBy != null) {
                ObjectNode node = nodes.objectNode();
                node.put("orderBy", orderBy);
                array.add(node);
            }
        }
        return array.isEmpty() ? Optional.empty() : Optional.of(array);
    }

    private String orderBy(Object entry) {
        if (entry instanceof String field) {
            return "_score".equals(field) ? null : field;
        }
        if (entry instanceof Map<?, ?> map && !map.isEmpty()) {
            Map.Entry<?, ?> first = map.entrySet().iterator().next();
            String field = String.valueOf(first.getKey());
            if ("_score".equals(field)) {
                return null;
            }
            Object order = first.getValue();
            String direction = order instanceof Map<?, ?> nested
                    ? String.valueOf(nested.get("order"))
                    : String.valueOf(order);
            return "desc".equalsIgnoreCase(direction) ? field + " desc" : field + " asc";
        }
        return null;
    }

    // ---- query translation ------------------------------------------------

    /**
     * Translates one DSL clause into a Content AI query.
     *
     * <p>Only the six clause types the copilot generates are translated. Everything
     * else is refused with the clause named — see the class javadoc for why a
     * silent drop would be worse.
     */
    @Override
    public ObjectNode translateQuery(TurDslQuery query) {
        if (query == null || query instanceof TurDslQuery.MatchAll) {
            return filterQuery(matchAllComponent());
        }
        if (query instanceof TurDslQuery.Match match) {
            return fulltextQuery(match.query(), null);
        }
        if (query instanceof TurDslQuery.Bool bool) {
            return translateBool(bool);
        }
        ObjectNode component = translateFilterComponent(query);
        return filterQuery(component);
    }

    /**
     * A {@code bool} split along Content AI's scoring/filtering seam: text clauses
     * become the arms of a {@code composite}, everything else becomes one
     * {@code FilterGroup} used as their pre-filter. With no text clause the whole
     * thing is a single {@code filter} query.
     *
     * <p>{@code should} maps to {@code OR} and {@code must}/{@code filter} to
     * {@code AND}; {@code must_not} becomes a {@code NOT} group. {@code
     * minimumShouldMatch} above 1 is refused rather than approximated — Content
     * AI's {@code FilterGroup} has no threshold, so "at least 2 of these 5" cannot
     * be expressed and pretending otherwise changes the result set.
     */
    private ObjectNode translateBool(TurDslQuery.Bool bool) {
        if (bool.minimumShouldMatch() != null && bool.minimumShouldMatch() > 1) {
            throw refuse("The DSL 'minimum_should_match' above 1");
        }
        List<ObjectNode> textArms = new ArrayList<>();
        List<ObjectNode> andFilters = new ArrayList<>();
        List<ObjectNode> orFilters = new ArrayList<>();

        collectClauses(bool.must(), textArms, andFilters);
        collectClauses(bool.filter(), textArms, andFilters);
        collectClauses(bool.should(), textArms, orFilters);

        if (bool.mustNot() != null && !bool.mustNot().isEmpty()) {
            List<ObjectNode> notFilters = new ArrayList<>();
            List<ObjectNode> discarded = new ArrayList<>();
            collectClauses(bool.mustNot(), discarded, notFilters);
            if (!discarded.isEmpty()) {
                // A negated text match is a scoring clause with no filter form.
                throw refuse("The DSL 'must_not' over a text query");
            }
            andFilters.add(group(OP_NOT, notFilters));
        }

        List<ObjectNode> combined = new ArrayList<>(andFilters);
        if (!orFilters.isEmpty()) {
            combined.add(orFilters.size() == 1 ? orFilters.getFirst() : group(OP_OR, orFilters));
        }
        ObjectNode filter = combined.isEmpty() ? null
                : wrapComponent(combined.size() == 1 ? combined.getFirst()
                        : group(OP_AND, combined));

        if (textArms.isEmpty()) {
            return filterQuery(filter != null ? filter : wrapComponent(matchAllComponent()));
        }
        if (textArms.size() == 1) {
            ObjectNode arm = textArms.getFirst();
            if (filter != null) {
                arm.set("filter", filter);
            }
            return arm;
        }
        // T1049 — `composite.queries` carries maxItems: 10, and one arm is written
        // per text-matching clause, so a `bool` with eleven of them cannot be
        // expressed. Refusing is right where dropping would not be: the arms are
        // what the caller asked to match on, and silently searching ten of eleven
        // returns a plausible answer to a question nobody asked.
        if (textArms.size() > MAX_COMPOSITE_ARMS) {
            throw refuse("A DSL 'bool' with more than %d text clauses"
                    .formatted(MAX_COMPOSITE_ARMS));
        }
        ObjectNode composite = nodes.objectNode();
        composite.put(FIELD_TYPE, "composite");
        composite.put(FIELD_OPERATOR, OP_OR);
        ArrayNode queries = nodes.arrayNode();
        for (ObjectNode arm : textArms) {
            if (filter != null) {
                arm.set("filter", filter);
            }
            queries.add(arm);
        }
        composite.set("queries", queries);
        return composite;
    }

    /** Sorts one clause list into text (scoring) arms and filter components. */
    private void collectClauses(List<TurDslQuery> clauses, List<ObjectNode> textArms,
            List<ObjectNode> filters) {
        if (clauses == null) {
            return;
        }
        for (TurDslQuery clause : clauses) {
            if (clause instanceof TurDslQuery.Match match) {
                textArms.add(fulltextQuery(match.query(), null));
            } else if (clause instanceof TurDslQuery.MatchAll) {
                // Adds nothing to an intersection and everything to a union; in
                // both cases the other clauses already say it.
                continue;
            } else if (clause instanceof TurDslQuery.Bool nested) {
                ObjectNode translated = translateBool(nested);
                if (!"filter".equals(translated.get(FIELD_TYPE).asString())) {
                    throw refuse("A nested DSL 'bool' mixing text queries");
                }
                filters.add((ObjectNode) translated.get("filter")
                        .get("filterComponent").deepCopy());
            } else {
                filters.add(translateFilterComponent(clause));
            }
        }
        if (filters.size() > MAX_GROUP_FILTERS) {
            throw refuse("A DSL 'bool' with more than %d filter clauses"
                    .formatted(MAX_GROUP_FILTERS));
        }
    }

    /** One non-text clause as a {@code FilterComponent}. */
    private ObjectNode translateFilterComponent(TurDslQuery query) {
        if (query instanceof TurDslQuery.Term term) {
            return typedFilter(term.field(), term.value());
        }
        if (query instanceof TurDslQuery.Terms terms) {
            return inFilter(terms.field(), terms.values());
        }
        if (query instanceof TurDslQuery.Range range) {
            return rangeGroup(range);
        }
        if (query instanceof TurDslQuery.Exists exists) {
            ObjectNode filter = nodes.objectNode();
            filter.put(FIELD_TYPE, "ExistsFilter");
            filter.put(FIELD_FIELD, exists.field());
            return filter;
        }
        if (query instanceof TurDslQuery.Prefix prefix) {
            ObjectNode filter = nodes.objectNode();
            filter.put(FIELD_TYPE, "PrefixFilter");
            filter.put(FIELD_FIELD, prefix.field());
            filter.put(FIELD_VALUE, prefix.value());
            return filter;
        }
        throw refuse("The DSL '%s' query".formatted(clauseName(query)));
    }

    /**
     * Picks the filter type from the value's Java type, because Content AI has one
     * filter per type rather than a single polymorphic one: a number sent as a
     * {@code StringFilter} would not match a numeric field.
     */
    private ObjectNode typedFilter(String field, Object value) {
        ObjectNode filter = nodes.objectNode();
        filter.put(FIELD_FIELD, field);
        filter.put(FIELD_OPERATOR, OP_EQ);
        if (value instanceof Boolean bool) {
            filter.put(FIELD_TYPE, TYPE_BOOLEAN_FILTER);
            filter.put(FIELD_VALUE, bool);
        } else if (value instanceof Number number) {
            filter.put(FIELD_TYPE, TYPE_NUMBER_FILTER);
            filter.put(FIELD_VALUE, number.doubleValue());
        } else {
            filter.put(FIELD_TYPE, TYPE_STRING_FILTER);
            filter.put(FIELD_VALUE, String.valueOf(value));
        }
        return filter;
    }

    private ObjectNode inFilter(String field, List<Object> values) {
        ObjectNode filter = nodes.objectNode();
        filter.put(FIELD_TYPE, "InFilter");
        filter.put(FIELD_FIELD, field);
        ArrayNode array = nodes.arrayNode();
        if (values != null) {
            for (Object value : values) {
                if (value instanceof Boolean bool) {
                    array.add(bool);
                } else if (value instanceof Number number) {
                    array.add(number.doubleValue());
                } else {
                    array.add(String.valueOf(value));
                }
            }
        }
        filter.set("values", array);
        return filter;
    }

    /**
     * A {@code range} becomes an {@code AND} of one-sided {@code NumberFilter}s,
     * since Content AI's filters carry a single value and operator. A range with no
     * bound at all is refused: it would otherwise emit an empty group, which the
     * spec rejects ({@code minItems: 1}) and which means nothing anyway.
     */
    private ObjectNode rangeGroup(TurDslQuery.Range range) {
        List<ObjectNode> bounds = new ArrayList<>();
        addBound(bounds, range.field(), range.gte(), "GTE");
        addBound(bounds, range.field(), range.gt(), "GT");
        addBound(bounds, range.field(), range.lte(), "LTE");
        addBound(bounds, range.field(), range.lt(), "LT");
        if (bounds.isEmpty()) {
            throw refuse("A DSL 'range' query with no bound");
        }
        return bounds.size() == 1 ? bounds.getFirst() : group(OP_AND, bounds);
    }

    private void addBound(List<ObjectNode> bounds, String field, Object value, String operator) {
        if (value == null) {
            return;
        }
        ObjectNode filter = nodes.objectNode();
        filter.put(FIELD_FIELD, field);
        filter.put(FIELD_OPERATOR, operator);
        if (value instanceof Number number) {
            filter.put(FIELD_TYPE, TYPE_NUMBER_FILTER);
            filter.put(FIELD_VALUE, number.doubleValue());
        } else {
            filter.put(FIELD_TYPE, "DateFilter");
            filter.put(FIELD_VALUE, dateTime(String.valueOf(value)));
        }
        bounds.add(filter);
    }

    /**
     * Widens a date-only bound to the instant Content AI accepts.
     *
     * <p>{@code DateFilter.value} is {@code format: date-time}, and the spec spells
     * the two forms it takes: {@code YYYY-MM-DDTHH:mm:ssZ} and
     * {@code YYYY-MM-DDTHH:mm:ss.SSSZ}. An Elasticsearch {@code range} on a date
     * field is routinely written {@code "gte": "2025-01-01"} — the DSL API's own
     * documentation does — which Adobe would refuse. Sending it verbatim was this
     * executor's first translation, and the schema check T1048 added is what
     * caught it.
     *
     * <p>Midnight UTC is the right widening for both ends: {@code gte} then includes
     * the whole named day, and {@code lt} excludes it, which is what the same bound
     * means to Elasticsearch. Anything already carrying a time is passed through —
     * Adobe validates the exact shape, and second-guessing a caller's instant would
     * be worse than letting it say so.
     */
    private static String dateTime(String value) {
        return DATE_ONLY.matcher(value).matches() ? value + "T00:00:00Z" : value;
    }

    // ---- building blocks --------------------------------------------------

    private ObjectNode group(String operator, List<ObjectNode> filters) {
        ObjectNode node = nodes.objectNode();
        node.put(FIELD_TYPE, TYPE_FILTER_GROUP);
        node.put(FIELD_OPERATOR, operator);
        ArrayNode array = nodes.arrayNode();
        filters.forEach(array::add);
        node.set(FIELD_FILTERS, array);
        return node;
    }

    /**
     * "Every document", as {@code NOT exists(sentinel)}. See
     * {@link TurContentAiQueryBuilder} — an empty {@code FilterGroup} would be the
     * obvious shape and the spec forbids it.
     */
    private ObjectNode matchAllComponent() {
        ObjectNode exists = nodes.objectNode();
        exists.put(FIELD_TYPE, "ExistsFilter");
        exists.put(FIELD_FIELD, TurContentAiQueryBuilder.MATCH_ALL_SENTINEL);
        return group(OP_NOT, List.of(exists));
    }

    private ObjectNode wrapComponent(ObjectNode component) {
        ObjectNode wrapper = nodes.objectNode();
        wrapper.set("filterComponent", component);
        return wrapper;
    }

    private ObjectNode filterQuery(ObjectNode componentOrWrapper) {
        ObjectNode wrapper = componentOrWrapper.has("filterComponent")
                ? componentOrWrapper
                : wrapComponent(componentOrWrapper);
        ObjectNode query = nodes.objectNode();
        query.put(FIELD_TYPE, "filter");
        query.set("filter", wrapper);
        return query;
    }

    private ObjectNode fulltextQuery(String text, ObjectNode filter) {
        ObjectNode query = nodes.objectNode();
        query.put(FIELD_TYPE, "fulltext");
        query.put(FIELD_TEXT, StringUtils.isNotBlank(text) ? text : "*");
        ObjectNode options = nodes.objectNode();
        options.put("boost", property.getFulltextBoost());
        if (StringUtils.isNotBlank(property.getLexicalSpace())) {
            ObjectNode space = nodes.objectNode();
            space.put("space", property.getLexicalSpace());
            options.set("lexicalSpaceSelection", space);
        }
        query.set(FIELD_OPTIONS, options);
        if (filter != null) {
            query.set("filter", filter);
        }
        return query;
    }

    private static String clauseName(TurDslQuery query) {
        return query.getClass().getSimpleName();
    }

    private static TurUnsupportedEngineException refuse(String what) {
        return new TurUnsupportedEngineException(what, ENGINE);
    }

    // ---- response ---------------------------------------------------------

    @Override
    public TurDslSearchResponse buildResponse(JsonNode response, TurDslQueryRequest request,
            long elapsedMs) {
        List<TurDslSearchResponse.Hit> hits = new ArrayList<>();
        Double maxScore = null;
        JsonNode results = response != null ? response.get("results") : null;
        if (results != null && results.isArray()) {
            for (JsonNode hit : results) {
                Double score = hit.has("score") ? hit.get("score").asDouble() : null;
                if (score != null && (maxScore == null || score > maxScore)) {
                    maxScore = score;
                }
                hits.add(new TurDslSearchResponse.Hit(
                        hit.has("id") ? hit.get("id").asString() : null,
                        score, source(hit.get("data")), null));
            }
        }
        long total = response != null && response.has("totalResults")
                ? response.get("totalResults").asLong(0L)
                : 0L;

        Map<String, TurDslSearchResponse.AggregationResult> aggregations =
                request.aggs() != null && !request.aggs().isEmpty()
                        ? buildAggregations(response, request.aggs())
                        : null;

        return new TurDslSearchResponse(elapsedMs, false,
                new TurDslSearchResponse.Hits(
                        new TurDslSearchResponse.Total(total, "eq"), maxScore, hits),
                aggregations);
    }

    private Map<String, Object> source(JsonNode data) {
        Map<String, Object> source = new LinkedHashMap<>();
        if (data != null && data.isObject()) {
            for (Map.Entry<String, JsonNode> entry : data.properties()) {
                source.put(entry.getKey(), plain(entry.getValue()));
            }
        }
        return source;
    }

    private Object plain(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(plain(item)));
            return values;
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node.toString();
    }

    /**
     * {@code terms} aggregations only, which is the one kind Content AI's
     * {@code termBuckets} expresses. Anything else is refused rather than returned
     * empty, so a caller does not read "no buckets" as "no values".
     */
    @Override
    public Map<String, TurDslSearchResponse.AggregationResult> buildAggregations(
            JsonNode response, Map<String, TurDslAggregation> requestedAggs) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        JsonNode aggregates = response != null ? response.get("aggregates") : null;
        if (aggregates != null && aggregates.isArray()) {
            for (JsonNode aggregate : aggregates) {
                JsonNode id = aggregate.get("id");
                if (id != null && !id.isNull()) {
                    byId.putIfAbsent(id.asString(), aggregate);
                }
            }
        }

        Map<String, TurDslSearchResponse.AggregationResult> result = new LinkedHashMap<>();
        for (Map.Entry<String, TurDslAggregation> entry : requestedAggs.entrySet()) {
            JsonNode aggregate = byId.get(entry.getKey());
            List<TurDslSearchResponse.Bucket> buckets = new ArrayList<>();
            JsonNode array = bucketArray(aggregate);
            if (array != null) {
                for (JsonNode bucket : array) {
                    JsonNode label = bucket.get("label");
                    if (label == null || label.isNull()) {
                        continue;
                    }
                    buckets.add(new TurDslSearchResponse.Bucket(label.asString(),
                            bucket.has("count") ? bucket.get("count").asLong(0L) : 0L));
                }
            }
            result.put(entry.getKey(), new TurDslSearchResponse.AggregationResult(buckets));
        }
        return result;
    }

    /** Adobe's schema names {@code values} required while defining {@code buckets}. */
    private JsonNode bucketArray(JsonNode aggregate) {
        if (aggregate == null) {
            return null;
        }
        JsonNode buckets = aggregate.get("buckets");
        if (buckets != null && buckets.isArray()) {
            return buckets;
        }
        JsonNode values = aggregate.get("values");
        return values != null && values.isArray() ? values : null;
    }

    private ArrayNode translateAggregations(Map<String, TurDslAggregation> aggs) {
        if (aggs == null || aggs.isEmpty()) {
            return null;
        }
        // T1049 — the array carries maxItems: 50. Unlike the plugin path, where the
        // aggregates come from site configuration and the surplus is dropped, these
        // are aggregations the query asked for by name: returning 50 of 51 would
        // answer with a facet map missing an entry the caller is about to read.
        if (aggs.size() > MAX_AGGREGATES) {
            throw refuse("A DSL request with more than %d aggregations"
                    .formatted(MAX_AGGREGATES));
        }
        ArrayNode array = nodes.arrayNode();
        long totalBuckets = 0;
        for (Map.Entry<String, TurDslAggregation> entry : aggs.entrySet()) {
            if (!(entry.getValue() instanceof TurDslAggregation.TermsAgg terms)) {
                throw refuse("The DSL '%s' aggregation"
                        .formatted(entry.getValue().getClass().getSimpleName()));
            }
            ObjectNode aggregate = nodes.objectNode();
            aggregate.put(FIELD_TYPE, "termBuckets");
            aggregate.put("id", entry.getKey());
            ObjectNode field = nodes.objectNode();
            field.put("name", terms.field());
            aggregate.set(FIELD_FIELD, field);
            // A bucket count is the clamping kind: `size` is a ceiling on what comes
            // back, so asking for more buckets than exist is not an error.
            int size = queryBuilder.clampBucketSize(
                    terms.size() != null && terms.size() > 0 ? terms.size()
                            : property.getFacetSize());
            aggregate.put("size", size);
            totalBuckets += size;
            array.add(aggregate);
        }
        // T1051 — Adobe fails the whole request over the total, which is prose in the
        // spec rather than a schema keyword and so is invisible to the T1048 check.
        // The plugin path scales its facets down to fit; here every size was either
        // named by the caller or is the configured default, and quietly returning
        // fewer values than asked for is a wrong answer the caller cannot detect.
        if (totalBuckets > TurContentAiQueryBuilder.MAX_TOTAL_BUCKETS) {
            throw refuse("A DSL request asking for %d term buckets, over the API's %d"
                    .formatted(totalBuckets, TurContentAiQueryBuilder.MAX_TOTAL_BUCKETS));
        }
        return array;
    }

    @Override
    public Object translateSort(List<Object> sortEntries) {
        return sort(sortEntries).orElse(null);
    }

    private String endpointUrl(TurSNSite site) {
        return Optional.ofNullable(site.getTurSEInstance())
                .map(TurSEInstance::getEndpointUrl)
                .orElse("");
    }
}
