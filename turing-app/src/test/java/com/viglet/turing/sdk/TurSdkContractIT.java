/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.turing.exchange.TurImportExchange;
import com.viglet.turing.exchange.TurImportExchange.ImportResult;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contract integration test for {@code @viglet/turing-sdk} (the vanilla JS SDK
 * under {@code frontend/packages/sdk}).
 *
 * <p>This is the automated form of "step 3" from the SDK rollout plan: it boots
 * the full Turing backend on a real HTTP port and asserts every REST/SSE
 * behavior the JS SDK's client + controllers depend on, so the SDK can never
 * silently break when the backend contract drifts. It does NOT execute the JS
 * SDK itself (that belongs in a JS test) — it pins the server side of the wire.
 *
 * <p><b>Self-contained &amp; CI-safe</b>: rather than depending on an external
 * Solr, the test imports one of the marketplace sample sites
 * ({@code mythical-creatures}) which ships with an <b>embedded Lucene</b> search
 * engine. The site definition ({@code export.json}) and its documents
 * ({@code mythical-creatures_content.json}) are committed under
 * {@code src/test/resources/sdk-it/} (copied from
 * {@code frontend/apps/marketplace/mythical-creatures/export/}). The Lucene
 * {@code endpointUrl} is rewritten to a unique temp directory so the in-process
 * index never collides with a locally-running app (which holds
 * {@code ./store/lucene/turing-sample} open) or with a parallel test JVM.
 * Content is indexed synchronously by {@link TurImportExchange} (no JMS queue
 * await), so the assertions are deterministic.
 *
 * <p>Inherits the ephemeral H2 + per-JVM Artemis isolation from
 * {@link AbstractTuringSpringIT}. The {@code @SpringBootTest} here re-declares
 * {@code spring.jmx.enabled=true} (so the parent's mandatory JMX bean still
 * wires) while switching to {@code RANDOM_PORT} for a real servlet container.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jmx.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurSdkContractIT extends AbstractTuringSpringIT {

    private static final String SITE = "mythical-creatures";
    private static final String LOCALE = "en_US";
    /** Slot the SDK reads from a fresh site with no GenAI agent configured. */
    private static final String EXPECTED_DISABLED_REASON = "NO_GENAI";
    /** T859 — the facet field carrying the reserved-character value. */
    private static final String RESERVED_FACET_FIELD = "creature_type";
    /** T859 — a facet value that cannot survive a naive query-string round-trip. */
    private static final String RESERVED_FACET_VALUE = "Ash & Ember";
    private static final String RESERVED_DOCUMENT_ID = "myth-contract-999";

    @Value("${local.server.port}")
    private int port;

    /**
     * This IT runs a {@code RANDOM_PORT} context that coexists in the same
     * Maven fork as the cached {@code MOCK}-env contexts of the other ITs.
     * With JMX enabled ({@code spring.jmx.enabled=true}, mandatory for
     * {@code TurQueueBrowserService}), both contexts' {@code MBeanExporter}
     * would register the {@code dataSource} HikariCP MBean under the same fixed
     * ObjectName ({@code com.zaxxer.hikari:name=dataSource,type=HikariDataSource})
     * — the second registration throws {@code InstanceAlreadyExistsException}
     * and the context fails to load (only when run alongside the full suite,
     * not in isolation). {@code spring.jmx.unique-names=true} appends an
     * identity suffix to this context's runtime MBean names, so it never
     * collides with the shared context's registration regardless of load order.
     */
    @DynamicPropertySource
    static void uniqueJmxObjectNames(DynamicPropertyRegistry registry) {
        registry.add("spring.jmx.unique-names", () -> "true");
        // T859 / §LXV.4 — the search cache is ON for this context on purpose: the
        // cache-key collision T858 fixed is invisible with the cache off, and a
        // mocked unit test cannot observe "two distinct bodies, one cached
        // response" at all. This is the one test shape that can.
        registry.add("turing.search.cache.enabled", () -> "true");
    }

    @Autowired
    private TurImportExchange importExchange;

    @Autowired
    private TurSNSiteRepository siteRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = JsonMapper.builder().build();

    private static Path luceneDir;

    /**
     * Imports the {@code mythical-creatures} Lucene sample (structure + content)
     * once for the class, pointing the Lucene index at a throwaway temp dir.
     */
    @BeforeAll
    void importLuceneSampleSite() throws IOException {
        luceneDir = Files.createTempDirectory("turing-sdk-it-lucene");

        byte[] zip = buildImportZip(luceneDir);
        MockMultipartFile bundle = new MockMultipartFile(
                "file", "mythical-creatures.zip", "application/zip", zip);

        // includeContent=true → indexes the docs synchronously; overwrite=true →
        // upserts the site even if the H2 snapshot already carried it.
        ImportResult result = importExchange.importFromMultipartFile(bundle, true, false, null, true);

        assertTrue(result.siteImported() || siteRepository.findByNameIgnoreCase(SITE).isPresent(),
                "marketplace Lucene site should be imported/present");
        assertTrue(result.searchEngineAvailable(), "embedded Lucene engine should be available");
        assertTrue(siteRepository.findByNameIgnoreCase(SITE).isPresent(),
                "site '" + SITE + "' must exist after import");
    }

    @AfterAll
    void cleanupLuceneDir() {
        if (luceneDir != null && Files.exists(luceneDir)) {
            try (var walk = Files.walk(luceneDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // write.lock may linger on Windows — OS reaps the temp dir later.
                    }
                });
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    // ── CSRF: the exact mechanism the SDK's ensureCsrfToken() relies on ──

    @Test
    void csrfEndpointExposesXsrfTokenHeader() throws Exception {
        HttpResponse<String> res = get("/csrf");
        assertEquals(200, res.statusCode(), "/csrf must be reachable");
        String token = res.headers().firstValue("X-XSRF-TOKEN").orElse(null);
        assertNotNull(token, "/csrf must surface the token via the X-XSRF-TOKEN header "
                + "(XSRF-TOKEN cookie is HttpOnly; SDK reads the header)");
        assertTrue(!token.isBlank(), "X-XSRF-TOKEN must be non-blank");
    }

    // ── Search: shape consumed by resolveDocuments() + real Lucene results ──

    @Test
    void searchReturnsDocumentedShapeWithRealResults() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search?q=dragon&_setlocale=" + LOCALE);
        assertEquals(200, res.statusCode());

        JsonNode body = json.readTree(res.body());
        JsonNode defaultFields = body.path("queryContext").path("defaultFields");
        // resolveDocuments() maps documents through these six default fields.
        for (String key : List.of("url", "title", "description", "text", "date", "image")) {
            assertTrue(defaultFields.has(key),
                    "queryContext.defaultFields must declare '" + key + "'");
        }
        assertEquals("title", defaultFields.path("title").asString());

        JsonNode documents = body.path("results").path("document");
        assertTrue(documents.isArray() && !documents.isEmpty(),
                "searching 'dragon' on the Lucene sample must return at least one document");
        assertTrue(documents.get(0).path("fields").has("title"),
                "each document must carry a 'fields' map");
    }

    @Test
    void wildcardSearchReturnsAllIndexedDocuments() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search?q=*&_setlocale=" + LOCALE);
        assertEquals(200, res.statusCode());

        JsonNode body = json.readTree(res.body());
        int count = body.path("queryContext").path("count").asInt(0);
        assertTrue(count >= 1, "wildcard search ('show all') must return the indexed corpus, got " + count);
    }

    // ── T859 / §LXV.4 — the whole Block AV POST surface, end to end ──

    /**
     * {@code LINKS} is the default, and the promise of T707 is that a consumer
     * that merely switches transport sees the response it already parses. Asserted
     * against the GET response for the <em>canonical</em> equivalent query, which
     * is what the adapter rebuilds the hrefs from.
     */
    @Test
    void postSearchQueryInLinksFormatMatchesTheGetResponse() throws Exception {
        HttpResponse<String> getRes =
                get("/sn/" + SITE + "/search?q=dragon&p=1&_setlocale=" + LOCALE);
        HttpResponse<String> postRes = postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"dragon","locale":"%s"}""".formatted(LOCALE));

        assertEquals(200, postRes.statusCode());
        assertEquals(json.readTree(getRes.body()), json.readTree(postRes.body()),
                "POST /search/query in LINKS format must return the GET response verbatim");
    }

    /**
     * The load-bearing assertion of T708: the structured body is
     * <em>link-free</em>. Not "has fewer links" — none at all, anywhere in the
     * body, so a consumer cannot accidentally keep parsing URLs.
     *
     * <p>
     * The scan covered everything except {@code results} until T864: this test
     * was the first thing to run the structured shape over a response that
     * actually has documents, and it found that
     * {@code results.document[].metadata[]} still carried {@code href}. T864
     * projected those chips onto {@code field}/{@code value}/{@code filterQuery}
     * and this scan lost its carve-out — which is what makes the claim real.
     * </p>
     */
    @Test
    void postSearchQueryInStructuredFormatCarriesNoLinks() throws Exception {
        HttpResponse<String> res = postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"*","locale":"%s","responseFormat":"STRUCTURED"}"""
                        .formatted(LOCALE));
        assertEquals(200, res.statusCode());

        String body = res.body();
        for (String forbidden : List.of("\"link\"", "\"href\"", "\"cleanUpLink\"",
                "\"cleanUpFacets\"", "/api/sn/")) {
            assertTrue(!body.contains(forbidden),
                    "STRUCTURED response must not contain " + forbidden + ": " + body);
        }
        JsonNode structured = json.readTree(body);
        // T864 — the document's facet chips are actions, not URLs.
        JsonNode chip = structured.path("results").path("document").get(0).path("metadata").get(0);
        assertTrue(!chip.path("filterQuery").asString().isBlank(),
                "each document chip must carry the filterQuery to add to request.fq");
        assertEquals("*", structured.path("request").path("q").asString(),
                "the structured response must echo the effective request");
        assertTrue(structured.path("facets").isArray() && !structured.path("facets").isEmpty(),
                "the sample site is faceted, so the structured response must carry facets");
    }

    /**
     * The production bug Block AV closed, end to end: a facet value containing
     * {@code &} is selected by putting its {@code filterQuery} on the request —
     * no URL is built, parsed or re-encoded anywhere — and it must actually
     * filter.
     */
    @Test
    void structuredFilterQueryWithAReservedCharacterActuallyFilters() throws Exception {
        JsonNode all = json.readTree(postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"*","locale":"%s","responseFormat":"STRUCTURED"}"""
                        .formatted(LOCALE)).body());
        int totalCount = all.path("queryContext").path("count").asInt();
        JsonNode item = facetItem(all, RESERVED_FACET_FIELD, RESERVED_FACET_VALUE);
        assertNotNull(item, "the reserved-character facet value must be offered by the site");

        String filterQuery = item.path("filterQuery").asString();
        assertTrue(filterQuery.contains("&"),
                "the filterQuery must carry the raw '&', not an escaped form: " + filterQuery);

        JsonNode filtered = json.readTree(postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"*","locale":"%s","responseFormat":"STRUCTURED","fq":["%s"]}"""
                        .formatted(LOCALE, filterQuery)).body());
        int filteredCount = filtered.path("queryContext").path("count").asInt();

        assertEquals(item.path("count").asInt(), filteredCount,
                "selecting the facet must return exactly the documents it counted");
        assertTrue(filteredCount < totalCount, "the filter must actually narrow the result set");
        assertEquals(RESERVED_DOCUMENT_ID,
                filtered.path("results").path("document").get(0).path("fields").path("id")
                        .asString(),
                "the filtered result must be the reserved-character document");
    }

    /**
     * T858 regression guard, and the exact shape a mocked unit suite cannot
     * express: with the cache <em>enabled</em>, two distinct bodies must not
     * share a cached response. The old key read the (absent) POST query string
     * and collapsed both onto {@code <site>_null}.
     */
    @Test
    void distinctPostBodiesDoNotShareACachedResponse() throws Exception {
        JsonNode dragons = json.readTree(postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"dragon","locale":"%s"}""".formatted(LOCALE)).body());
        JsonNode everything = json.readTree(postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"*","locale":"%s"}""".formatted(LOCALE)).body());

        int dragonCount = dragons.path("queryContext").path("count").asInt();
        int totalCount = everything.path("queryContext").path("count").asInt();
        assertTrue(dragonCount > 0, "the 'dragon' query must match something");
        assertTrue(dragonCount < totalCount,
                "a cached response was served for a different body: 'dragon' returned "
                        + dragonCount + " and '*' returned " + totalCount);
    }

    /** T712 — the autocomplete POST variant takes the same anonymous JSON body. */
    @Test
    void postAutoCompleteQueryAcceptsTheJsonBody() throws Exception {
        HttpResponse<String> res = postJson("/sn/" + SITE + "/ac/query",
                """
                        {"q":"dra","locale":"%s","rows":10,"fq":["%s:%s"]}"""
                        .formatted(LOCALE, RESERVED_FACET_FIELD, RESERVED_FACET_VALUE));

        assertEquals(200, res.statusCode());
        assertTrue(json.readTree(res.body()).isArray(),
                "/ac/query must return a JSON array of suggestions");
    }

    /** T712 — the select-list POST variant, same body, list projection. */
    @Test
    void postSelectListQueryAcceptsTheJsonBody() throws Exception {
        HttpResponse<String> res = postJson("/sn/" + SITE + "/search/list/query",
                """
                        {"q":"*","locale":"%s"}""".formatted(LOCALE));

        assertEquals(200, res.statusCode());
        assertTrue(json.readTree(res.body()).isArray(),
                "/search/list/query must return a JSON array");
    }

    /** T856 — the structured shape is reachable over GET as well. */
    @Test
    void getSearchHonoursTheStructuredResponseFormat() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search?q=*&_setlocale=" + LOCALE
                + "&responseFormat=structured");

        assertEquals(200, res.statusCode());
        JsonNode structured = json.readTree(res.body());
        assertTrue(!res.body().contains("\"href\""),
                "GET with responseFormat=structured must return the link-free shape");
        assertEquals("*", structured.path("request").path("q").asString(),
                "the GET structured response must echo the effective request too");
    }

    /** T857 — the sort options can be inlined instead of separately fetched. */
    @Test
    void structuredResponseCanInlineTheSortOptions() throws Exception {
        JsonNode lean = json.readTree(postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"*","locale":"%s","responseFormat":"STRUCTURED"}"""
                        .formatted(LOCALE)).body());
        assertTrue(lean.path("sortOptions").isMissingNode(),
                "the default structured body must stay lean");

        JsonNode withOptions = json.readTree(postJson("/sn/" + SITE + "/search/query",
                """
                        {"q":"*","locale":"%s","responseFormat":"STRUCTURED","includeSortOptions":true}"""
                        .formatted(LOCALE)).body());
        JsonNode inlined = withOptions.path("sortOptions");
        assertTrue(inlined.isArray() && !inlined.isEmpty(),
                "includeSortOptions must inline the sort options");
        assertEquals(json.readTree(get("/sn/" + SITE + "/search/sort-options").body()), inlined,
                "the inlined list must be exactly what the dedicated endpoint serves");
    }

    // ── chat/enabled: ChatEnabledResponse contract ──

    @Test
    void chatEnabledReturnsDocumentedShape() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/chat/enabled");
        assertEquals(200, res.statusCode());

        JsonNode body = json.readTree(res.body());
        assertTrue(body.has("enabled") && body.path("enabled").isBoolean(),
                "chat/enabled must return a boolean 'enabled'");
        if (!body.path("enabled").asBoolean()) {
            // A freshly-imported sample has no GenAI agent → deterministic reason.
            assertEquals(EXPECTED_DISABLED_REASON, body.path("reason").asString(),
                    "a site without GenAI must report reason=" + EXPECTED_DISABLED_REASON);
        }
    }

    // ── sort-options: useTuringSortOptions / fetchSortOptions contract ──

    @Test
    void sortOptionsReturnsArray() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search/sort-options");
        assertEquals(200, res.statusCode());
        assertTrue(json.readTree(res.body()).isArray(),
                "sort-options must be a JSON array of {value,label}");
    }

    // ── Chat SSE handshake: only meaningful when GenAI is configured ──

    @Test
    void chatConversationHandshakeIsServerSentEvents() throws Exception {
        boolean enabled = json.readTree(get("/sn/" + SITE + "/chat/enabled").body())
                .path("enabled").asBoolean();
        // The sample ships without an agent, so this asserts the SSE handshake
        // only on deployments where chat is actually enabled (no LLM required to
        // verify the content-type negotiation). Skips cleanly otherwise.
        assumeTrue(enabled, "chat disabled for the sample site — skipping SSE handshake assertion");

        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/sn/" + SITE + "/chat/conversation"))
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"locale\":\""
                        + LOCALE + "\"}"))
                .build();
        HttpResponse<String> res = http.send(req, BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        String contentType = res.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith("text/event-stream"),
                "chat/conversation must negotiate text/event-stream, got: " + contentType);
    }

    // ── helpers ──

    private String base() {
        return "http://localhost:" + port + "/api";
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path)).GET().build();
        return http.send(req, BodyHandlers.ofString());
    }

    /**
     * T859 — posts an anonymous JSON body, exactly as the JS SDK does. No CSRF
     * token: {@code /api/sn/**} is exempt, which is itself part of the contract
     * the public read surface depends on.
     */
    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        return http.send(req, BodyHandlers.ofString());
    }

    /** Finds a facet item by field + value in a structured response. */
    private static JsonNode facetItem(JsonNode structured, String field, String value) {
        for (JsonNode facet : structured.path("facets")) {
            if (!field.equals(facet.path("name").asString())) {
                continue;
            }
            for (JsonNode item : facet.path("items")) {
                if (value.equals(item.path("value").asString())) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * Builds an in-memory import zip from the committed marketplace fixtures,
     * rewriting the Lucene {@code endpointUrl} to {@code targetIndexDir} so the
     * test owns an isolated index.
     */
    private byte[] buildImportZip(Path targetIndexDir) throws IOException {
        byte[] exportJson = rewriteLuceneEndpoint(readResource("/sdk-it/export.json"), targetIndexDir);
        byte[] contentJson = addReservedCharacterDocument(
                readResource("/sdk-it/mythical-creatures_content.json"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("export.json"));
            zip.write(exportJson);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(SITE + "_content.json"));
            zip.write(contentJson);
            zip.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * T859 / §LXV.4 — appends one synthetic creature whose {@code creature_type}
     * facet value contains an {@code &}.
     *
     * <p>
     * The committed fixture is a verbatim copy of the marketplace sample and has
     * no reserved character in any facet value, so it cannot exercise the bug
     * Block AV closed. Rather than diverge the fixture from its source, the
     * document is added <em>in memory</em> at zip time — the same treatment
     * {@link #rewriteLuceneEndpoint} already gives the Lucene endpoint.
     * </p>
     */
    private byte[] addReservedCharacterDocument(byte[] contentJson) {
        ObjectNode root = (ObjectNode) json.readTree(contentJson);
        ArrayNode documents = (ArrayNode) root.path(SITE).path(LOCALE);
        assertTrue(!documents.isEmpty(), "content fixture must carry documents");

        ObjectNode creature = (ObjectNode) documents.get(0).deepCopy();
        creature.put("id", RESERVED_DOCUMENT_ID);
        creature.put("title", "Ash & Ember Chimera");
        creature.put("description", "Synthetic contract-test creature.");
        creature.put("text", "Exists only to prove a reserved character survives the round-trip.");
        creature.set("creature_type", json.createArrayNode().add(RESERVED_FACET_VALUE));
        documents.add(creature);
        return json.writeValueAsBytes(root);
    }

    private byte[] rewriteLuceneEndpoint(byte[] exportJson, Path targetIndexDir) {
        ObjectNode root = (ObjectNode) json.readTree(exportJson);
        JsonNode seArray = root.path("se");
        assertTrue(seArray.isArray() && !seArray.isEmpty(), "export.json must declare a search engine");
        ((ObjectNode) seArray.get(0)).put("endpointUrl",
                targetIndexDir.toString().replace('\\', '/'));
        return json.writeValueAsBytes(root);
    }

    private byte[] readResource(String classpath) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertNotNull(in, "missing test resource: " + classpath);
            return in.readAllBytes();
        }
    }
}
