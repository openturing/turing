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
package com.viglet.turing.plugins.se.contentai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.sun.net.httpserver.HttpServer;
import com.viglet.testsupport.contentai.TurContentAiSpecValidator;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.passage.TurSEPassage;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.contentai.TurContentAiClient;
import com.viglet.turing.contentai.TurContentAiQueryBuilder;
import com.viglet.turing.contentai.TurContentAiResponseMapper;
import com.viglet.turing.contentai.TurContentAiSourceCatalog;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.properties.TurContentAiProperty;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.bean.TurSECoreInfo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T1031 / §XX.18 — stands Adobe's Content AI search endpoint in miniature and
 * asserts through it, rather than through a mock of the client.
 *
 * <p>The point of the exercise is that the request body has to be right, and a
 * mocked client proves nothing about it: the plugin's whole job is translating a
 * Turing search into the shape Adobe's published OpenAPI declares, and that
 * translation is exactly what a stub can check and a unit test of the mapper
 * alone cannot. So the stub records every body it is handed and replies with the
 * response shapes Adobe's own spec publishes as examples
 * ({@code search-response-001} / {@code -002}).
 *
 * <p>What each test pins:
 * <ul>
 *   <li>the hybrid composition — a {@code composite OR} of a {@code vector} and
 *       a {@code fulltext} arm, which is Adobe's documented hybrid shape;</li>
 *   <li>the credential reaching the wire as {@code Authorization: Bearer};</li>
 *   <li>facet selections becoming a {@code FilterGroup} whose fields intersect
 *       and whose values union only where the site says a facet is multi-select;</li>
 *   <li>{@code termBuckets} aggregates read back as Turing facet results,
 *       including from the {@code values} key Adobe's schema names as required
 *       while defining {@code buckets};</li>
 *   <li>cursor paging costing one request per page, and the walk stopping at
 *       {@code max-page-walk} with the true total intact;</li>
 *   <li>{@code limit} clamped to the 50 the spec allows;</li>
 *   <li>every write path refusing by name rather than silently succeeding.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurContentAiSearchEnginePluginTest {

    private static final String SITE = "atlas";
    private static final String CORE = "atlas_en_us";
    private static final String SOURCE = "content-ai-articles-index";
    private static final Locale LOCALE = Locale.US;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Adobe's published example response for a search query. */
    private static final String RESPONSE_ONE_HIT = """
            {
              "totalResults": 5,
              "results": [
                {
                  "id": "doc_1",
                  "score": 0.7514963709,
                  "data": {
                    "title": "The Benefits of Electric Cars",
                    "description": "This document talks about the advantages of electric cars.",
                    "tags": ["pollution", "electric"]
                  },
                  "chunks": []
                }
              ],
              "cursor": "Y3Vyc29yLTE="
            }
            """;

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    private HttpServer server;
    private final List<String> bodies = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();
    private final Deque<String> responses = new ArrayDeque<>();

    /** What GET /content-sources answers; a test overrides it before calling. */
    private String sourceListing = """
            {"items": [
              {"name": "content-ai-articles-index", "type": "ACQUISITION",
               "search": {
                 "vectorSpaces": {"semantic": {"default": true}},
                 "lexicalSpaces": {"fulltext": {"default": true, "fields": [
                   {"field": "category", "type": "STRING", "filter": {"enabled": true}},
                   {"field": "description", "type": "STRING", "filter": {"enabled": false},
                    "fulltext": {"enabled": true}}
                 ]}}
               }},
              {"name": "aem-assets", "type": "AEM_PUBLISH", "search": {}}
            ]}
            """;
    private int sourceListingStatus = 200;
    private final AtomicInteger sourceListingCalls = new AtomicInteger();

    private final TurContentAiClient client = new TurContentAiClient();
    private final TurContentAiQueryBuilder queryBuilder = new TurContentAiQueryBuilder();
    private final TurContentAiResponseMapper responseMapper = new TurContentAiResponseMapper();
    private final TurContentAiSourceCatalog sourceCatalog = new TurContentAiSourceCatalog(client);
    private final TurContentAiProperty property = new TurContentAiProperty();

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ---- fixture ---------------------------------------------------------

    /**
     * Starts the stub. Each queued body is served in order; the last one is
     * repeated once the queue drains, so a test that only cares about the request
     * does not have to queue a reply per page.
     */
    private String startStub(String... queuedResponses) throws IOException {
        responses.clear();
        for (String response : queuedResponses) {
            responses.addLast(response);
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TurContentAiClient.SEARCH_PATH, exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            // T1048 — every body this suite produces is validated against Adobe's
            // published schema, not only the parts a test remembered to assert. This
            // is what makes the stub a contract rather than a yes-man.
            TurContentAiSpecValidator.assertValid(requestBody,
                    TurContentAiSpecValidator.SEARCH_REQUEST);
            bodies.add(requestBody);
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = responses.size() > 1 ? responses.removeFirst() : responses.peekFirst();
            // T1050 — the reply half of the contract. A fixture a test author
            // invented proves the mapper agrees with itself; this makes it prove
            // the mapper agrees with Adobe.
            if (body != null) {
                TurContentAiSpecValidator.assertValid(body,
                        TurContentAiSpecValidator.SEARCH_RESPONSE);
            }
            byte[] payload = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.createContext(TurContentAiClient.SOURCES_PATH, exchange -> {
            sourceListingCalls.incrementAndGet();
            byte[] payload = sourceListing.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(sourceListingStatus, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private TurContentAiSearchEnginePlugin plugin(String endpoint,
            TurSNSiteFacetFieldEnum siteItemType, TurSNSiteFieldExt... facetFields) {
        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);

        TurSNSite site = new TurSNSite();
        site.setName(SITE);
        site.setTurSEInstance(instance);
        site.setFacetItemType(siteItemType);

        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setTurSNSite(site);
        siteLocale.setLanguage(LOCALE);
        siteLocale.setCore(CORE);

        when(turSNSiteRepository.findByNameIgnoreCase(SITE)).thenReturn(Optional.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(any(), eq(LOCALE)))
                .thenReturn(siteLocale);
        when(turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(any(), anyInt(), anyInt()))
                .thenReturn(List.of(facetFields));

        property.setEnabled(true);
        property.setToken("adobe-token");
        property.getSources().put(SITE, SOURCE);

        return new TurContentAiSearchEnginePlugin(client, queryBuilder, responseMapper,
                sourceCatalog, property, turSNSiteRepository, turSNSiteLocaleRepository,
                turSNSiteFieldExtRepository);
    }

    private static TurSNSiteFieldExt facetField(String name, int position,
            TurSNSiteFacetFieldEnum itemType) {
        TurSNSiteFieldExt field = new TurSNSiteFieldExt();
        field.setName(name);
        field.setFacet(1);
        field.setEnabled(1);
        field.setFacetPosition(position);
        field.setFacetItemType(itemType);
        return field;
    }

    private static TurSNSiteSearchContext context(String query, Integer page, Integer rows,
            List<String> filterQueries) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setQ(query);
        searchParams.setP(page);
        searchParams.setRows(rows);
        searchParams.setSort(null);
        searchParams.setFq(filterQueries == null ? List.of() : filterQueries);
        TurSEParameters params = new TurSEParameters(searchParams);
        params.setTurSNFilterParams(TurSNFilterParams.builder()
                .defaultValues(filterQueries == null ? List.of() : filterQueries)
                .build());
        return new TurSNSiteSearchContext(SITE, null, params, LOCALE,
                URI.create("http://localhost/api/sn/" + SITE));
    }

    private JsonNode lastBody() {
        return MAPPER.readTree(bodies.getLast());
    }

    // ---- request composition --------------------------------------------

    @Test
    @DisplayName("A user query becomes Adobe's hybrid shape: composite OR of vector and fulltext")
    void composesHybridQuery() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("electric cars", 1, 10, null));

        JsonNode body = lastBody();
        assertThat(body.get("contentSource").get("name").asString()).isEqualTo(SOURCE);
        assertThat(body.get("contentSource").get("type").asString()).isEqualTo("ACQUISITION");

        JsonNode query = body.get("query");
        assertThat(query.get("type").asString()).isEqualTo("composite");
        assertThat(query.get("operator").asString()).isEqualTo("OR");

        List<String> armTypes = new ArrayList<>();
        query.get("queries").forEach(arm -> armTypes.add(arm.get("type").asString()));
        assertThat(armTypes).containsExactly("vector", "fulltext");
        query.get("queries").forEach(
                arm -> assertThat(arm.get("text").asString()).isEqualTo("electric cars"));

        assertThat(authHeaders.getLast()).isEqualTo("Bearer adobe-token");
    }

    @Test
    @DisplayName("hybrid=false drops the vector arm and sends fulltext alone")
    void lexicalOnlyWhenHybridDisabled() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        property.setHybrid(false);

        plugin.retrieveSearchResults(context("electric cars", 1, 10, null));

        assertThat(lastBody().get("query").get("type").asString()).isEqualTo("fulltext");
    }

    @Test
    @DisplayName("An empty query selects everything by negating an impossibility")
    void blankQueryMatchesAll() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("  ", 1, 10, null));

        JsonNode query = lastBody().get("query");
        assertThat(query.get("type").asString()).isEqualTo("filter");
        JsonNode component = query.get("filter").get("filterComponent");
        assertThat(component.get("type").asString()).isEqualTo("FilterGroup");

        // T1047 — this asserted an empty `AND` group, on the reasoning that no
        // constraints means no exclusions. Adobe would have refused it:
        // FilterGroup.filters carries minItems: 1. The stub accepts any body, so
        // nothing here caught it. What is valid is negating an impossibility.
        assertThat(component.get("operator").asString()).isEqualTo("NOT");
        assertThat(component.get("filters")).hasSize(1);
        assertThat(component.get("filters").get(0).get("field").asString())
                .isEqualTo(TurContentAiQueryBuilder.MATCH_ALL_SENTINEL);
    }

    @Test
    @DisplayName("Rows above the API's 50-row ceiling are clamped, not sent and refused")
    void clampsLimitToFifty() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 1, 500, null));

        assertThat(lastBody().get("queryOptions").get("pagination").get("limit").asInt())
                .isEqualTo(TurContentAiQueryBuilder.MAX_LIMIT);
    }

    @Test
    @DisplayName("A sort passes through as orderBy, and 'relevance' sends none at all")
    void mapsSort() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);

        TurSNSiteSearchContext sorted = context("cars", 1, 10, null);
        sorted.getTurSEParameters().setSort("modificationDate desc");
        plugin.retrieveSearchResults(sorted);
        assertThat(lastBody().get("queryOptions").get("sort").get(0).get("orderBy").asString())
                .isEqualTo("modificationDate desc");

        TurSNSiteSearchContext byRelevance = context("cars", 1, 10, null);
        byRelevance.getTurSEParameters().setSort("relevance");
        plugin.retrieveSearchResults(byRelevance);
        assertThat(lastBody().get("queryOptions").has("sort")).isFalse();
    }

    // ---- filters ---------------------------------------------------------

    @Test
    @DisplayName("Facet selections intersect across fields; a single value is a StringFilter EQ")
    void filtersIntersectAcrossFields() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND,
                facetField("category", 1, TurSNSiteFacetFieldEnum.DEFAULT),
                facetField("author", 2, TurSNSiteFacetFieldEnum.DEFAULT))
                        .retrieveSearchResults(context("cars", 1, 10,
                                List.of("category:news", "author:alice")));

        JsonNode component = lastBody().get("query").get("queries").get(0).get("filter")
                .get("filterComponent");
        assertThat(component.get("operator").asString()).isEqualTo("AND");
        assertThat(component.get("filters")).hasSize(2);
        JsonNode first = component.get("filters").get(0);
        assertThat(first.get("type").asString()).isEqualTo("StringFilter");
        assertThat(first.get("field").asString()).isEqualTo("category");
        assertThat(first.get("value").asString()).isEqualTo("news");
        assertThat(first.get("operator").asString()).isEqualTo("EQ");
    }

    @Test
    @DisplayName("Two values of a multi-select facet union as an InFilter")
    void multiSelectFacetValuesUnion() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND,
                facetField("tags", 1, TurSNSiteFacetFieldEnum.OR))
                        .retrieveSearchResults(context("cars", 1, 10,
                                List.of("tags:electric", "tags:autonomous")));

        JsonNode filter = lastBody().get("query").get("queries").get(0).get("filter")
                .get("filterComponent").get("filters").get(0);
        assertThat(filter.get("type").asString()).isEqualTo("InFilter");
        assertThat(filter.get("field").asString()).isEqualTo("tags");
        List<String> values = new ArrayList<>();
        filter.get("values").forEach(value -> values.add(value.asString()));
        assertThat(values).containsExactly("electric", "autonomous");
    }

    @Test
    @DisplayName("Two values of a single-select facet intersect as an AND of StringFilter EQ")
    void singleSelectFacetValuesIntersect() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND,
                facetField("tags", 1, TurSNSiteFacetFieldEnum.AND))
                        .retrieveSearchResults(context("cars", 1, 10,
                                List.of("tags:electric", "tags:autonomous")));

        JsonNode group = lastBody().get("query").get("queries").get(0).get("filter")
                .get("filterComponent").get("filters").get(0);
        assertThat(group.get("type").asString()).isEqualTo("FilterGroup");
        assertThat(group.get("operator").asString()).isEqualTo("AND");
        assertThat(group.get("filters")).hasSize(2);
    }

    // ---- response mapping -------------------------------------------------

    @Test
    @DisplayName("A hit maps to data plus id and score, with the Content AI id winning")
    void mapsHitFields() throws IOException {
        String endpoint = startStub("""
                {
                  "totalResults": 1,
                  "results": [
                    {"id": "doc_1", "score": 0.75,
                     "data": {"title": "T", "id": "the-body-id", "tags": ["a", "b"]}}
                  ]
                }
                """);

        Optional<TurSEResults> results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 1, 10, null));

        assertThat(results).isPresent();
        Map<String, Object> fields = results.get().getResults().getFirst().getFields();
        assertThat(fields.get("id")).isEqualTo("doc_1");
        assertThat(fields.get("title")).isEqualTo("T");
        assertThat(fields.get("score")).isEqualTo(0.75);
        assertThat(fields.get("tags")).isEqualTo(List.of("a", "b"));
    }

    // ---- passages (T1044) -------------------------------------------------

    @Test
    @DisplayName("Matched chunks become passages, best score first whatever order Adobe sent")
    void mapsChunksToPassages() throws IOException {
        // Adobe's search-response-002, with the chunks deliberately ascending:
        // the spec documents no ordering guarantee, so the mapper must not
        // inherit whichever order the example happens to use.
        String endpoint = startStub("""
                {
                  "totalResults": 5,
                  "results": [
                    {
                      "id": "doc_1", "score": 0.75,
                      "data": {"title": "The Benefits of Electric Cars"},
                      "chunks": [
                        {"id": "chunk_2", "score": 0.5514963709,
                         "chunkData": {"text": "It also mentions some challenges."}},
                        {"id": "chunk_1", "score": 0.7514963709,
                         "chunkData": {"text": "They reduce pollution and save money on fuel."}}
                      ]
                    }
                  ]
                }
                """);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        property.setIncludeChunks(true);

        var results = plugin.retrieveSearchResults(context("electric cars", 1, 10, null))
                .orElseThrow();

        var passages = results.getResults().getFirst().getPassages();
        assertThat(passages).extracting(TurSEPassage::id).containsExactly("chunk_1", "chunk_2");
        assertThat(passages.getFirst().text())
                .isEqualTo("They reduce pollution and save money on fuel.");
        assertThat(passages.getFirst().hasText()).isTrue();
        // The passage is not merged into the field map — a caller must never
        // mistake it for a field the source declared.
        assertThat(results.getResults().getFirst().getFields()).doesNotContainKey("chunks");
    }

    @Test
    @DisplayName("A request that asked for chunks distinguishes none-matched from not-requested")
    void distinguishesEmptyChunksFromAbsentChunks() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 2, "results": [
                  {"id": "a", "score": 0.9, "data": {"title": "A"}, "chunks": []},
                  {"id": "b", "score": 0.8, "data": {"title": "B"}}
                ]}
                """);

        var results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 1, 10, null)).orElseThrow();

        // "chunks": [] is Adobe saying this document matched on a field, not a
        // passage. An absent key is Adobe never being asked.
        assertThat(results.getResults().get(0).getPassages()).isEmpty();
        assertThat(results.getResults().get(1).getPassages()).isNull();
    }

    @Test
    @DisplayName("include-chunks reaches the wire, and gates the passage capability")
    void includeChunksIsRequestedAndAdvertised() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);

        assertThat(plugin.supportsPassages()).isFalse();
        plugin.retrieveSearchResults(context("cars", 1, 10, null));
        JsonNode vectorArm = lastBody().get("query").get("queries").get(0);
        assertThat(vectorArm.get("options").has("chunks")).isFalse();

        property.setIncludeChunks(true);
        assertThat(plugin.supportsPassages()).isTrue();
        plugin.retrieveSearchResults(context("cars", 1, 10, null));
        JsonNode chunks = lastBody().get("query").get("queries").get(0).get("options")
                .get("chunks");
        assertThat(chunks.get("includeInResponse").asBoolean()).isTrue();
        assertThat(chunks.get("scoreMode").asString()).isEqualTo("MAX");

        // A disabled plugin advertises nothing, whatever the chunk flag says.
        property.setEnabled(false);
        assertThat(plugin.supportsPassages()).isFalse();
    }

    @Test
    @DisplayName("A chunk with no text still becomes a scored passage")
    void mapsChunkWithoutText() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 1, "results": [
                  {"id": "a", "score": 0.9, "data": {"title": "A"},
                   "chunks": [{"id": "chunk_1", "score": 0.4}]}
                ]}
                """);

        var results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 1, 10, null)).orElseThrow();

        var passage = results.getResults().getFirst().getPassages().getFirst();
        assertThat(passage.id()).isEqualTo("chunk_1");
        assertThat(passage.score()).isEqualTo(0.4);
        assertThat(passage.hasText()).isFalse();
    }

    @Test
    @DisplayName("The interface default is no passage support")
    void otherEnginesDoNotSupportPassages() {
        // What keeps the field null on an index Turing filled: there the caller
        // can re-chunk the document body, so there is nothing to ask the engine.
        TurSearchEnginePlugin anyEngine = org.mockito.Mockito.mock(TurSearchEnginePlugin.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);

        assertThat(anyEngine.supportsPassages()).isFalse();
    }

    @Test
    @DisplayName("totalResults drives numFound and pageCount")
    void mapsTotals() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        TurSEResults results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 1, 2, null)).orElseThrow();

        assertThat(results.getNumFound()).isEqualTo(5L);
        assertThat(results.getLimit()).isEqualTo(2);
        assertThat(results.getPageCount()).isEqualTo(3);
        assertThat(results.getCurrentPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("termBuckets aggregates become facet results, from 'buckets' or from 'values'")
    void mapsAggregatesFromEitherKey() throws IOException {
        String endpoint = startStub("""
                {
                  "totalResults": 3,
                  "results": [],
                  "aggregates": [
                    {"type": "termBuckets", "id": "category", "field": "category",
                     "buckets": [{"label": "news", "count": 2}, {"label": "blog", "count": 1}]},
                    {"type": "termBuckets", "id": "tags", "field": "tags",
                     "values": [{"label": "electric", "count": 3}]}
                  ]
                }
                """);

        TurSEResults results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND,
                facetField("category", 1, TurSNSiteFacetFieldEnum.DEFAULT),
                facetField("tags", 2, TurSNSiteFacetFieldEnum.OR))
                        .retrieveSearchResults(context("cars", 1, 10, null)).orElseThrow();

        assertThat(results.getFacetResults()).hasSize(2);
        var category = results.getFacetResults().getFirst();
        assertThat(category.getFacet()).isEqualTo("category");
        assertThat(category.getFacetPosition()).isEqualTo(1);
        assertThat(category.getTurSEFacetResultAttr().get("news").getCount()).isEqualTo(2);
        // The 'values' key is what Adobe's schema declares required while defining
        // 'buckets'; reading only one of the two loses this row silently.
        var tags = results.getFacetResults().get(1);
        assertThat(tags.getTurSEFacetResultAttr().get("electric").getCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("A requested facet asks for limit 0 and one aggregate keyed by the field")
    void facetOnlyRequestReturnsNoDocuments() throws IOException {
        String endpoint = startStub("""
                {
                  "totalResults": 9,
                  "results": [],
                  "aggregates": [
                    {"type": "termBuckets", "id": "category", "field": "category",
                     "buckets": [{"label": "news", "count": 9}]}
                  ]
                }
                """);

        TurSEResults results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND,
                facetField("category", 1, TurSNSiteFacetFieldEnum.DEFAULT))
                        .retrieveFacetResults(context("cars", 1, 10, null), "category")
                        .orElseThrow();

        JsonNode body = lastBody();
        assertThat(body.get("queryOptions").get("pagination").get("limit").asInt()).isZero();
        assertThat(body.get("aggregates")).hasSize(1);
        assertThat(body.get("aggregates").get(0).get("id").asString()).isEqualTo("category");
        assertThat(body.get("aggregates").get(0).get("field").get("name").asString())
                .isEqualTo("category");

        assertThat(results.getResults()).isEmpty();
        assertThat(results.getFacetResults()).hasSize(1);
        assertThat(results.getFacetResults().getFirst().getTurSEFacetResultAttr().get("news")
                .getCount()).isEqualTo(9);
    }

    @Test
    @DisplayName("A site with more facet fields than the API's 50 aggregates loses the surplus, "
            + "not the search")
    void surplusFacetFieldsAreDropped() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 0, "results": []}
                """);
        TurSNSiteFieldExt[] fields =
                new TurSNSiteFieldExt[TurContentAiQueryBuilder.MAX_AGGREGATES + 5];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = facetField("field_" + i, i + 1, TurSNSiteFacetFieldEnum.DEFAULT);
        }

        // A facet count is an admin decision, so refusing would take every search
        // on the site with it — the fields arrive in facet-position order, so what
        // survives is what the site put first.
        plugin(endpoint, TurSNSiteFacetFieldEnum.AND, fields)
                .retrieveSearchResults(context("cars", 1, 10, null));

        assertThat(lastBody().get("aggregates"))
                .hasSize(TurContentAiQueryBuilder.MAX_AGGREGATES);
    }

    @Test
    @DisplayName("A configured facet size over the API's 2000 is clamped before it is sent")
    void surplusFacetSizeIsClamped() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 0, "results": []}
                """);
        property.setFacetSize(50_000);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND,
                facetField("category", 1, TurSNSiteFacetFieldEnum.DEFAULT))
                        .retrieveSearchResults(context("cars", 1, 10, null));

        assertThat(lastBody().get("aggregates").get(0).get("size").asInt())
                .isEqualTo(TurContentAiQueryBuilder.MAX_BUCKET_SIZE);
    }

    // ---- cursor paging ---------------------------------------------------

    @Test
    @DisplayName("Page 3 costs three requests, each carrying the previous page's cursor")
    void walksCursorsToReachADeepPage() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 30, "results": [], "cursor": "c1"}
                """, """
                {"totalResults": 30, "results": [], "cursor": "c2"}
                """, """
                {"totalResults": 30,
                 "results": [{"id": "doc_21", "score": 0.1, "data": {"title": "Third page"}}]}
                """);

        TurSEResults results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 3, 10, null)).orElseThrow();

        assertThat(bodies).hasSize(3);
        assertThat(MAPPER.readTree(bodies.get(0)).get("queryOptions").get("pagination")
                .has("cursor")).isFalse();
        assertThat(MAPPER.readTree(bodies.get(1)).get("queryOptions").get("pagination")
                .get("cursor").asString()).isEqualTo("c1");
        assertThat(MAPPER.readTree(bodies.get(2)).get("queryOptions").get("pagination")
                .get("cursor").asString()).isEqualTo("c2");

        assertThat(results.getCurrentPage()).isEqualTo(3);
        assertThat(results.getStart()).isEqualTo(20L);
        assertThat(results.getResults()).hasSize(1);
    }

    @Test
    @DisplayName("A page past the end returns no rows but keeps the honest total")
    void runningOutOfCursorsYieldsAnEmptyPage() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 4,
                 "results": [{"id": "doc_1", "score": 0.9, "data": {"title": "Only page"}}]}
                """);

        TurSEResults results = plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 5, 10, null)).orElseThrow();

        assertThat(results.getResults()).isEmpty();
        assertThat(results.getNumFound()).isEqualTo(4L);
    }

    @Test
    @DisplayName("A page beyond max-page-walk costs one request, not that many")
    void refusesToWalkPastTheConfiguredCeiling() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 5000, "results": [], "cursor": "c1"}
                """);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        property.setMaxPageWalk(3);

        TurSEResults results = plugin.retrieveSearchResults(context("cars", 40, 10, null))
                .orElseThrow();

        assertThat(bodies).hasSize(1);
        assertThat(results.getResults()).isEmpty();
        assertThat(results.getNumFound()).isEqualTo(5000L);
    }

    // ---- configuration diagnostics (T1045) -------------------------------

    @Test
    @DisplayName("indexExists asks Adobe rather than answering from the config file")
    void indexExistsResolvesAgainstTheSourceListing() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);

        assertThat(plugin.indexExists(instance, SOURCE)).isTrue();
        assertThat(plugin.indexExists(instance, "aem-assets")).isTrue();
        assertThat(plugin.indexExists(instance, "a-source-nobody-created")).isFalse();
        // One listing served all three: it is cached per endpoint.
        assertThat(sourceListingCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("An unreadable listing reports the source present, never missing")
    void unreadableListingDoesNotReportTheSourceMissing() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        sourceListingStatus = 503;
        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);

        // "Adobe could not be reached" is not "no such source"; a caller reading
        // false would conclude the configuration is wrong when it may be right.
        assertThat(plugin.indexExists(instance, SOURCE)).isTrue();
    }

    @Test
    @DisplayName("listIndexes names the real sources, with an unknown count rather than zero")
    void listIndexesNamesTheTenantsSources() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);

        var cores = plugin.listIndexes(instance);

        assertThat(cores).extracting(TurSECoreInfo::name)
                .containsExactly("content-ai-articles-index", "aem-assets");
        // -1, not 0: a 0 would read as "this source is empty", which is the
        // confusion this task exists to remove.
        assertThat(cores).allSatisfy(core -> assertThat(core.numDocs()).isEqualTo(-1L));
        assertThat(cores).allSatisfy(core -> assertThat(core.usedBySites()).isEmpty());
    }

    @Test
    @DisplayName("An empty result on a misnamed source reads the listing once, then stops")
    void emptyResultDiagnosesTheSourceNameOnce() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 0, "results": []}
                """);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        property.getSources().put(SITE, "content-ai-articels-index"); // transposed

        plugin.retrieveSearchResults(context("cars", 1, 10, null));
        plugin.retrieveSearchResults(context("bikes", 1, 10, null));
        plugin.retrieveSearchResults(context("trains", 1, 10, null));

        // Three empty searches, one listing: a misconfiguration is permanent until
        // somebody changes it, so repeating the diagnostic would only bury it.
        assertThat(sourceListingCalls.get()).isEqualTo(1);
        assertThat(bodies).hasSize(3);
    }

    @Test
    @DisplayName("A source that exists and matched nothing costs the same one listing")
    void emptyResultOnAValidSourceStillChecksTheRestOfTheConfig() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 0, "results": []}
                """);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND,
                facetField("category", 1, TurSNSiteFacetFieldEnum.DEFAULT),
                facetField("author", 2, TurSNSiteFacetFieldEnum.DEFAULT));
        // The listing says this source filters on `category` but not `author`, and
        // declares no vector space called `nonexistent`.
        property.setVectorSpace("nonexistent");

        var results = plugin.retrieveSearchResults(context("cars", 1, 10, null));

        assertThat(results).isPresent();
        assertThat(results.get().getResults()).isEmpty();
        assertThat(sourceListingCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("A failed diagnostic is retried, since it says nothing about the source")
    void failedDiagnosticIsNotRemembered() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 0, "results": []}
                """);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        sourceListingStatus = 503;

        plugin.retrieveSearchResults(context("cars", 1, 10, null));
        plugin.retrieveSearchResults(context("bikes", 1, 10, null));

        // Marking it diagnosed on a transport failure would suppress the real
        // answer for the rest of the process's life.
        assertThat(sourceListingCalls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("A search that finds something never pays for the listing")
    void nonEmptyResultSkipsTheDiagnostic() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);

        plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 1, 10, null));

        assertThat(sourceListingCalls.get()).isZero();
    }

    @Test
    @DisplayName("System info reports the real sources beside the configured mapping")
    void systemInfoReportsSourceResolution() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);

        assertThat(plugin.getSystemInfo(instance))
                .containsEntry("sourceCount", "2")
                .containsEntry("sources", "content-ai-articles-index, aem-assets")
                .containsEntry("source." + SITE, SOURCE)
                .containsEntry("sourceType", "ACQUISITION");

        property.getSources().put(SITE, "typo-index");
        sourceCatalog.invalidate(endpoint);
        assertThat(plugin.getSystemInfo(instance))
                .containsEntry("source." + SITE, "typo-index (NOT FOUND)");
    }

    @Test
    @DisplayName("The listing's filterable fields honour filter.enabled, not mere presence")
    void filterableFieldsRespectTheEnabledFlag() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        plugin(endpoint, TurSNSiteFacetFieldEnum.AND);

        var source = sourceCatalog.find(endpoint, SOURCE, property).orElseThrow();

        // Adobe's own example carries a `description` field with
        // filter.enabled=false; counting it as filterable would report a facet as
        // available that then renders with no values.
        assertThat(source.filterFields()).containsExactly("category");
        assertThat(source.fulltextFields()).containsExactly("description");
        assertThat(source.vectorSpaces()).containsExactly("semantic");
        assertThat(source.lexicalSpaces()).containsExactly("fulltext");
        assertThat(source.type()).isEqualTo("ACQUISITION");
    }

    @Test
    @DisplayName("A source with no search block yields empty spaces, not a failure")
    void sourceWithoutSearchMetadataIsTolerated() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        plugin(endpoint, TurSNSiteFacetFieldEnum.AND);

        var source = sourceCatalog.find(endpoint, "aem-assets", property).orElseThrow();

        assertThat(source.vectorSpaces()).isEmpty();
        assertThat(source.filterFields()).isEmpty();
        assertThat(source.type()).isEqualTo("AEM_PUBLISH");
    }

    @Test
    @DisplayName("The listing is walked by cursor until it runs out")
    void listingIsPagedByCursor() throws IOException {
        // Two pages: the first carries a cursor, the second does not.
        String endpoint = startStub(RESPONSE_ONE_HIT);
        AtomicInteger page = new AtomicInteger();
        server.removeContext(TurContentAiClient.SOURCES_PATH);
        server.createContext(TurContentAiClient.SOURCES_PATH, exchange -> {
            sourceListingCalls.incrementAndGet();
            String body = page.getAndIncrement() == 0
                    ? "{\"items\":[{\"name\":\"first\",\"type\":\"CUSTOM\"}],\"cursor\":\"c1\"}"
                    : "{\"items\":[{\"name\":\"second\",\"type\":\"CUSTOM\"}]}";
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        plugin(endpoint, TurSNSiteFacetFieldEnum.AND);

        assertThat(sourceCatalog.sources(endpoint, property))
                .extracting(com.viglet.turing.contentai.TurContentAiSource::name)
                .containsExactly("first", "second");
        assertThat(sourceListingCalls.get()).isEqualTo(2);
    }

    // ---- honest refusals -------------------------------------------------

    @Test
    @DisplayName("A disabled plugin never calls Adobe")
    void disabledPluginDoesNotCallAdobe() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        property.setEnabled(false);

        assertThat(plugin.retrieveSearchResults(context("cars", 1, 10, null))).isEmpty();
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("A missing credential is a refusal, not an anonymous request")
    void missingCredentialRefuses() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        property.setToken(null);
        property.setApiKey(null);

        // The client throws; the plugin turns that into an empty Optional so a
        // federated site degrades to "no results" rather than a 500.
        assertThat(plugin.retrieveSearchResults(context("cars", 1, 10, null))).isEmpty();
        assertThat(bodies).isEmpty();
    }

    @Test
    @DisplayName("An HTTP error degrades to no results rather than propagating")
    void httpErrorDegradesToEmpty() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TurContentAiClient.SEARCH_PATH, exchange -> {
            byte[] payload = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort();

        assertThat(plugin(endpoint, TurSNSiteFacetFieldEnum.AND)
                .retrieveSearchResults(context("cars", 1, 10, null))).isEmpty();
    }

    @Test
    @DisplayName("Every write path refuses by name — a no-op would report a phantom publish")
    void writePathsRefuse() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);
        TurSNSite site = new TurSNSite();
        site.setName(SITE);

        assertThatThrownBy(() -> plugin.indexDocument(site, LOCALE, Map.of("id", "1")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("read-only")
                .hasMessageContaining("indexDocument");
        assertThatThrownBy(() -> plugin.createIndex(instance, null, CORE, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plugin.deleteIndex(instance, CORE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plugin.clearIndex(instance, CORE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plugin.deIndex(site, LOCALE, "1"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plugin.deIndexByType(site, LOCALE, "Page"))
                .isInstanceOf(UnsupportedOperationException.class);

        // Nothing was written, so there is nothing to flush; the SN pipeline
        // commits unconditionally and must not see an error here.
        assertThat(plugin.commit(site, LOCALE)).isTrue();
        assertThat(plugin.getPluginType())
                .isEqualTo(TurContentAiSearchEnginePlugin.PLUGIN_TYPE);
    }

    @Test
    @DisplayName("getDocumentTotal counts with limit 0 and reads totalResults")
    void countsDocuments() throws IOException {
        String endpoint = startStub("""
                {"totalResults": 1234, "results": []}
                """);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);

        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);
        TurSNSite site = new TurSNSite();
        site.setName(SITE);
        site.setTurSEInstance(instance);
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setTurSNSite(site);
        siteLocale.setCore(CORE);

        assertThat(plugin.getDocumentTotal(siteLocale)).isEqualTo(1234L);
        assertThat(lastBody().get("queryOptions").get("pagination").get("limit").asInt()).isZero();
    }

    @Test
    @DisplayName("System info reports the federation honestly, including a missing credential")
    void systemInfoReportsFederation() throws IOException {
        String endpoint = startStub(RESPONSE_ONE_HIT);
        var plugin = plugin(endpoint, TurSNSiteFacetFieldEnum.AND);
        TurSEInstance instance = new TurSEInstance();
        instance.setEndpointUrl(endpoint);

        assertThat(plugin.getSystemInfo(instance))
                .containsEntry("engine", "contentai")
                .containsEntry("mode", "federated (read-only)")
                .containsEntry("credential", "configured")
                .containsEntry("status", "UP");

        property.setToken(null);
        property.setApiKey(null);
        assertThat(plugin.getSystemInfo(instance))
                .containsEntry("credential", "missing")
                .containsEntry("status", "DOWN");
    }
}
