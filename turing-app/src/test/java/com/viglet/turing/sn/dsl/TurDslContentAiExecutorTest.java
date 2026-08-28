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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.sun.net.httpserver.HttpServer;
import com.viglet.testsupport.contentai.TurContentAiSpecValidator;
import com.viglet.turing.api.exception.TurUnsupportedEngineException;
import com.viglet.turing.contentai.TurContentAiClient;
import com.viglet.turing.contentai.TurContentAiQueryBuilder;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.properties.TurContentAiProperty;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T1047 / §XX.24 — the Content AI DSL executor, asserted through a stub of
 * Adobe's search endpoint so the request body is what is being tested.
 *
 * <p>The translation is the whole of the executor, and it crosses two structural
 * mismatches: Elasticsearch's {@code bool} mixes scoring and filtering while
 * Content AI separates them into different query types, and Content AI has no
 * offset at all. Both are pinned here, along with the refusals — the clause types
 * with no expression in Content AI's DSL are named rather than dropped, because a
 * dropped clause means answering confidently from the wrong document set.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurDslContentAiExecutorTest {

    private static final String SITE = "atlas";
    private static final String CORE = "atlas_en_us";
    private static final String SOURCE = "content-ai-articles-index";
    private static final Locale LOCALE = Locale.US;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    private HttpServer server;
    private final List<String> bodies = new ArrayList<>();
    private String response = """
            {"totalResults": 2, "results": [
              {"id": "doc_1", "score": 0.9,
               "data": {"title": "A", "price": 19.99, "tags": ["x", "y"]}},
              {"id": "doc_2", "score": 0.4, "data": {"title": "B"}}
            ]}
            """;

    private final TurContentAiClient client = new TurContentAiClient();
    private final TurContentAiQueryBuilder queryBuilder = new TurContentAiQueryBuilder();
    private final TurContentAiProperty property = new TurContentAiProperty();

    private TurDslContentAiExecutor executor;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TurContentAiClient.SEARCH_PATH, exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            // T1048 — every translated body is checked against Adobe's schema, so a
            // translation that is merely plausible fails here rather than in production.
            TurContentAiSpecValidator.assertValid(body, TurContentAiSpecValidator.SEARCH_REQUEST);
            bodies.add(body);
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();

        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);
        TurSNSite site = new TurSNSite();
        site.setName(SITE);
        site.setTurSEInstance(instance);
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setTurSNSite(site);
        siteLocale.setLanguage(LOCALE);
        siteLocale.setCore(CORE);

        when(turSNSiteRepository.findByNameIgnoreCase(SITE))
                .thenReturn(java.util.Optional.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(any(), eq(LOCALE)))
                .thenReturn(siteLocale);

        property.setEnabled(true);
        property.setToken("adobe-token");
        property.getSources().put(SITE, SOURCE);

        executor = new TurDslContentAiExecutor(client, queryBuilder, property,
                turSNSiteRepository, turSNSiteLocaleRepository);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---- helpers ---------------------------------------------------------

    private static TurDslQueryRequest request(TurDslQuery query, Integer from, Integer size,
            List<Object> sort, Map<String, TurDslAggregation> aggs) {
        return new TurDslQueryRequest(query, from, size, sort, null, null, aggs,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static TurDslQueryRequest request(TurDslQuery query) {
        return request(query, null, null, null, null);
    }

    private JsonNode lastQuery() {
        return MAPPER.readTree(bodies.getLast()).get("query");
    }

    private JsonNode component(JsonNode filterQuery) {
        return filterQuery.get("filter").get("filterComponent");
    }

    // ---- the engine's identity -------------------------------------------

    @Test
    @DisplayName("It registers as the contentai executor, which is what unblocks the copilot")
    void registersForContentAi() {
        assertThat(executor.getEngineType()).isEqualTo("contentai");
    }

    // ---- match_all -------------------------------------------------------

    @Test
    @DisplayName("match_all is NOT exists(sentinel), because an empty FilterGroup is invalid")
    void translatesMatchAll() {
        executor.execute(SITE, LOCALE, request(new TurDslQuery.MatchAll()));

        JsonNode query = lastQuery();
        assertThat(query.get("type").asString()).isEqualTo("filter");
        JsonNode group = component(query);
        // FilterGroup.filters carries minItems: 1, so "no constraints" cannot be
        // expressed as an empty group. Negating an impossibility can.
        assertThat(group.get("operator").asString()).isEqualTo("NOT");
        assertThat(group.get("filters")).hasSize(1);
        JsonNode exists = group.get("filters").get(0);
        assertThat(exists.get("type").asString()).isEqualTo("ExistsFilter");
        assertThat(exists.get("field").asString())
                .isEqualTo(TurContentAiQueryBuilder.MATCH_ALL_SENTINEL);
    }

    @Test
    @DisplayName("A null query is match_all, as it is in Elasticsearch")
    void nullQueryIsMatchAll() {
        executor.execute(SITE, LOCALE, request(null));

        assertThat(component(lastQuery()).get("operator").asString()).isEqualTo("NOT");
    }

    // ---- leaf clauses ----------------------------------------------------

    @Test
    @DisplayName("A term's filter type follows the value's type, since Content AI has one per type")
    void termPicksTheFilterTypeFromTheValue() {
        executor.execute(SITE, LOCALE, request(new TurDslQuery.Term("category", "news")));
        JsonNode stringFilter = component(lastQuery());
        assertThat(stringFilter.get("type").asString()).isEqualTo("StringFilter");
        assertThat(stringFilter.get("value").asString()).isEqualTo("news");
        assertThat(stringFilter.get("operator").asString()).isEqualTo("EQ");

        executor.execute(SITE, LOCALE, request(new TurDslQuery.Term("price", 19.99)));
        JsonNode numberFilter = component(lastQuery());
        // A number sent as a StringFilter would not match a numeric field at all.
        assertThat(numberFilter.get("type").asString()).isEqualTo("NumberFilter");
        assertThat(numberFilter.get("value").asDouble()).isEqualTo(19.99);

        executor.execute(SITE, LOCALE, request(new TurDslQuery.Term("inStock", true)));
        JsonNode booleanFilter = component(lastQuery());
        assertThat(booleanFilter.get("type").asString()).isEqualTo("BooleanFilter");
        assertThat(booleanFilter.get("value").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("terms becomes an InFilter, values keeping their JSON type")
    void translatesTerms() {
        executor.execute(SITE, LOCALE,
                request(new TurDslQuery.Terms("tags", List.of("electric", "autonomous"))));

        JsonNode filter = component(lastQuery());
        assertThat(filter.get("type").asString()).isEqualTo("InFilter");
        assertThat(filter.get("field").asString()).isEqualTo("tags");
        List<String> values = new ArrayList<>();
        filter.get("values").forEach(value -> values.add(value.asString()));
        assertThat(values).containsExactly("electric", "autonomous");
    }

    @Test
    @DisplayName("A two-sided range becomes an AND of one-sided filters")
    void translatesRange() {
        executor.execute(SITE, LOCALE,
                request(new TurDslQuery.Range("price", 10, null, 100, null)));

        JsonNode group = component(lastQuery());
        assertThat(group.get("type").asString()).isEqualTo("FilterGroup");
        assertThat(group.get("operator").asString()).isEqualTo("AND");
        assertThat(group.get("filters")).hasSize(2);
        assertThat(group.get("filters").get(0).get("operator").asString()).isEqualTo("GTE");
        assertThat(group.get("filters").get(1).get("operator").asString()).isEqualTo("LTE");
    }

    @Test
    @DisplayName("A one-sided range collapses to a single filter, not a group of one")
    void oneSidedRangeIsNotWrapped() {
        executor.execute(SITE, LOCALE,
                request(new TurDslQuery.Range("price", null, 10, null, null)));

        JsonNode filter = component(lastQuery());
        assertThat(filter.get("type").asString()).isEqualTo("NumberFilter");
        assertThat(filter.get("operator").asString()).isEqualTo("GT");
    }

    @Test
    @DisplayName("A date-only bound is widened to the instant DateFilter's format requires")
    void dateRangeBound() {
        executor.execute(SITE, LOCALE,
                request(new TurDslQuery.Range("createdAt", "2026-01-01", null, null, null)));

        JsonNode filter = component(lastQuery());
        assertThat(filter.get("type").asString()).isEqualTo("DateFilter");
        // `DateFilter.value` is format: date-time. An Elasticsearch range on a date
        // field is routinely written "2026-01-01" — the DSL API's own docs do — and
        // Adobe refuses it. Sending it verbatim was this executor's first attempt;
        // the T1048 schema check is what caught it.
        assertThat(filter.get("value").asString()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("A bound that already carries a time is passed through untouched")
    void instantRangeBoundIsNotRewritten() {
        executor.execute(SITE, LOCALE, request(
                new TurDslQuery.Range("createdAt", null, null, "2026-03-01T14:30:00Z", null)));

        assertThat(component(lastQuery()).get("value").asString())
                .isEqualTo("2026-03-01T14:30:00Z");
    }

    @Test
    @DisplayName("A range with no bound is refused rather than emitting an empty group")
    void unboundedRangeIsRefused() {
        assertThatThrownBy(() -> executor.execute(SITE, LOCALE,
                request(new TurDslQuery.Range("price", null, null, null, null))))
                        .isInstanceOf(TurUnsupportedEngineException.class)
                        .hasMessageContaining("no bound");
    }

    @Test
    @DisplayName("match becomes a scoring fulltext query, not a filter")
    void translatesMatch() {
        executor.execute(SITE, LOCALE,
                request(new TurDslQuery.Match("title", "electric cars", null)));

        JsonNode query = lastQuery();
        assertThat(query.get("type").asString()).isEqualTo("fulltext");
        assertThat(query.get("text").asString()).isEqualTo("electric cars");
    }

    // ---- bool: the scoring/filtering split -------------------------------

    @Test
    @DisplayName("A bool of pure filters collapses to one filter query, no composite")
    void boolOfFiltersIsAFilterQuery() {
        var bool = new TurDslQuery.Bool(
                List.of(new TurDslQuery.Term("category", "news")),
                null, null,
                List.of(new TurDslQuery.Range("price", 10, null, null, null)),
                null);

        executor.execute(SITE, LOCALE, request(bool));

        JsonNode query = lastQuery();
        assertThat(query.get("type").asString()).isEqualTo("filter");
        JsonNode group = component(query);
        assertThat(group.get("operator").asString()).isEqualTo("AND");
        assertThat(group.get("filters")).hasSize(2);
    }

    @Test
    @DisplayName("A bool's text clause becomes the scoring arm and the rest its pre-filter")
    void boolSplitsScoringFromFiltering() {
        var bool = new TurDslQuery.Bool(
                List.of(new TurDslQuery.Match("title", "electric", null)),
                null, null,
                List.of(new TurDslQuery.Term("category", "news")),
                null);

        executor.execute(SITE, LOCALE, request(bool));

        // Elasticsearch would score and filter in one bool; Content AI needs the
        // filter hung off the scoring query as its pre-filter.
        JsonNode query = lastQuery();
        assertThat(query.get("type").asString()).isEqualTo("fulltext");
        assertThat(query.get("text").asString()).isEqualTo("electric");
        JsonNode filter = query.get("filter").get("filterComponent");
        assertThat(filter.get("type").asString()).isEqualTo("StringFilter");
        assertThat(filter.get("field").asString()).isEqualTo("category");
    }

    @Test
    @DisplayName("Two text clauses compose over OR, each carrying the same pre-filter")
    void twoTextClausesBecomeAComposite() {
        var bool = new TurDslQuery.Bool(
                List.of(new TurDslQuery.Match("title", "electric", null),
                        new TurDslQuery.Match("body", "battery", null)),
                null, null,
                List.of(new TurDslQuery.Term("category", "news")),
                null);

        executor.execute(SITE, LOCALE, request(bool));

        JsonNode query = lastQuery();
        assertThat(query.get("type").asString()).isEqualTo("composite");
        assertThat(query.get("operator").asString()).isEqualTo("OR");
        assertThat(query.get("queries")).hasSize(2);
        query.get("queries").forEach(arm -> assertThat(
                arm.get("filter").get("filterComponent").get("field").asString())
                        .isEqualTo("category"));
    }

    @Test
    @DisplayName("should maps to OR and must_not to a NOT group")
    void translatesShouldAndMustNot() {
        var bool = new TurDslQuery.Bool(
                null,
                List.of(new TurDslQuery.Term("category", "news"),
                        new TurDslQuery.Term("category", "blog")),
                List.of(new TurDslQuery.Term("archived", true)),
                null, null);

        executor.execute(SITE, LOCALE, request(bool));

        JsonNode group = component(lastQuery());
        assertThat(group.get("operator").asString()).isEqualTo("AND");
        List<String> operators = new ArrayList<>();
        group.get("filters").forEach(filter -> operators.add(
                filter.has("operator") ? filter.get("operator").asString() : "?"));
        assertThat(operators).contains("NOT", "OR");
    }

    @Test
    @DisplayName("A match_all inside a bool is dropped: the other clauses already say it")
    void matchAllInsideABoolIsDropped() {
        var bool = new TurDslQuery.Bool(
                List.of(new TurDslQuery.MatchAll(),
                        new TurDslQuery.Term("category", "news")),
                null, null, null, null);

        executor.execute(SITE, LOCALE, request(bool));

        // One filter, not a NOT-exists group intersected with it.
        assertThat(component(lastQuery()).get("type").asString()).isEqualTo("StringFilter");
    }

    @Test
    @DisplayName("minimum_should_match above 1 is refused, since a FilterGroup has no threshold")
    void minimumShouldMatchIsRefused() {
        var bool = new TurDslQuery.Bool(null,
                List.of(new TurDslQuery.Term("a", "1"), new TurDslQuery.Term("b", "2")),
                null, null, 2);

        assertThatThrownBy(() -> executor.execute(SITE, LOCALE, request(bool)))
                .isInstanceOf(TurUnsupportedEngineException.class)
                .hasMessageContaining("minimum_should_match");
    }

    @Test
    @DisplayName("A negated text match is refused: there is no filter form of a text query")
    void mustNotOverTextIsRefused() {
        var bool = new TurDslQuery.Bool(null, null,
                List.of(new TurDslQuery.Match("title", "draft", null)), null, null);

        assertThatThrownBy(() -> executor.execute(SITE, LOCALE, request(bool)))
                .isInstanceOf(TurUnsupportedEngineException.class)
                .hasMessageContaining("must_not");
    }

    // ---- pagination and sort ---------------------------------------------

    @Test
    @DisplayName("size becomes limit, clamped to the API's 50")
    void sizeBecomesLimit() {
        executor.execute(SITE, LOCALE,
                request(new TurDslQuery.MatchAll(), null, 500, null, null));

        assertThat(MAPPER.readTree(bodies.getLast()).get("queryOptions").get("pagination")
                .get("limit").asInt()).isEqualTo(TurContentAiQueryBuilder.MAX_LIMIT);
    }

    @Test
    @DisplayName("A non-zero from is refused, because there is no offset to honour it with")
    void nonZeroFromIsRefused() {
        assertThatThrownBy(() -> executor.execute(SITE, LOCALE,
                request(new TurDslQuery.MatchAll(), 20, 10, null, null)))
                        .isInstanceOf(TurUnsupportedEngineException.class)
                        .hasMessageContaining("from");
        // Returning page one while the caller believes it asked for page three is
        // a wrong answer it cannot detect.
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("from=0 is the first page and is served")
    void zeroFromIsFine() {
        assertThat(executor.execute(SITE, LOCALE,
                request(new TurDslQuery.MatchAll(), 0, 10, null, null))).isPresent();
    }

    @Test
    @DisplayName("Sort maps to orderBy; _score is dropped rather than sent as a field")
    void translatesSort() {
        executor.execute(SITE, LOCALE, request(new TurDslQuery.MatchAll(), null, 10,
                List.of(Map.of("price", "desc"), "title"), null));

        JsonNode sort = MAPPER.readTree(bodies.getLast()).get("queryOptions").get("sort");
        assertThat(sort).hasSize(2);
        assertThat(sort.get(0).get("orderBy").asString()).isEqualTo("price desc");
        // A bare field name passes through unqualified, and means the same thing on
        // both sides: Elasticsearch's bare-string sort is ascending, and the spec
        // says an orderBy without asc/desc "will order by the specified property in
        // ascending order". Appending `asc` would add a token, not a meaning.
        assertThat(sort.get(1).get("orderBy").asString()).isEqualTo("title");

        executor.execute(SITE, LOCALE, request(new TurDslQuery.MatchAll(), null, 10,
                List.of("_score"), null));
        // Adobe already sorts by descending score; a field named _score does not exist.
        assertThat(MAPPER.readTree(bodies.getLast()).get("queryOptions").has("sort")).isFalse();
    }

    // ---- aggregations ----------------------------------------------------

    @Test
    @DisplayName("A terms agg becomes termBuckets and reads back as buckets")
    void translatesTermsAggregation() {
        response = """
                {"totalResults": 3, "results": [],
                 "aggregates": [{"type": "termBuckets", "id": "by_category",
                   "buckets": [{"label": "news", "count": 2}, {"label": "blog", "count": 1}]}]}
                """;

        var result = executor.execute(SITE, LOCALE,
                request(new TurDslQuery.MatchAll(), null, 10, null,
                        Map.of("by_category", new TurDslAggregation.TermsAgg("category", 5))))
                .orElseThrow();

        JsonNode aggregate = MAPPER.readTree(bodies.getLast()).get("aggregates").get(0);
        assertThat(aggregate.get("type").asString()).isEqualTo("termBuckets");
        assertThat(aggregate.get("id").asString()).isEqualTo("by_category");
        assertThat(aggregate.get("field").get("name").asString()).isEqualTo("category");
        assertThat(aggregate.get("size").asInt()).isEqualTo(5);

        var buckets = result.aggregations().get("by_category").buckets();
        assertThat(buckets).extracting(TurDslSearchResponse.Bucket::key)
                .containsExactly("news", "blog");
        assertThat(buckets.getFirst().docCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("A non-terms agg is refused, not answered with no buckets")
    void otherAggregationsAreRefused() {
        assertThatThrownBy(() -> executor.execute(SITE, LOCALE,
                request(new TurDslQuery.MatchAll(), null, 10, null,
                        Map.of("stats", new TurDslAggregation.AvgAgg("price")))))
                                .isInstanceOf(TurUnsupportedEngineException.class)
                                .hasMessageContaining("aggregation");
    }

    // ---- response --------------------------------------------------------

    @Test
    @DisplayName("The response is projected into the ES-compatible shape the DSL API returns")
    void buildsTheEsCompatibleResponse() {
        var result = executor.execute(SITE, LOCALE, request(new TurDslQuery.MatchAll()))
                .orElseThrow();

        assertThat(result.hits().total().value()).isEqualTo(2L);
        assertThat(result.hits().maxScore()).isEqualTo(0.9);
        assertThat(result.hits().hits()).hasSize(2);
        var first = result.hits().hits().getFirst();
        assertThat(first.id()).isEqualTo("doc_1");
        assertThat(first.score()).isEqualTo(0.9);
        // `data` becomes `_source`, with JSON types preserved.
        assertThat(first.source()).containsEntry("title", "A")
                .containsEntry("price", 19.99)
                .containsEntry("tags", List.of("x", "y"));
    }

    // ---- refusals --------------------------------------------------------

    @Test
    @DisplayName("A clause with no Content AI expression is named, never dropped")
    void unsupportedClausesAreNamed() {
        var knn = new TurDslQuery.Knn("embedding", List.of(0.1, 0.2), 5, 50, null, null);

        var thrown = org.assertj.core.api.Assertions.catchThrowableOfType(
                TurUnsupportedEngineException.class,
                () -> executor.execute(SITE, LOCALE, request(knn)));

        // Dropping it would answer confidently from the wrong document set.
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(thrown.getEngine()).isEqualTo("contentai");
        assertThat(thrown.getMessage()).contains("Knn").contains("contentai");
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("A disabled plugin serves nothing, and never calls Adobe")
    void disabledPluginServesNothing() {
        property.setEnabled(false);

        assertThat(executor.execute(SITE, LOCALE, request(new TurDslQuery.MatchAll()))).isEmpty();
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("An unknown locale on a known site is empty, not a refusal")
    void unknownLocaleIsEmpty() {
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(any(), eq(Locale.FRENCH)))
                .thenReturn(null);

        assertThat(executor.execute(SITE, Locale.FRENCH, request(new TurDslQuery.MatchAll())))
                .isEmpty();
    }
}
