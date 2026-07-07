/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.showcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * End-to-end smoke test for the <b>Atlas Store reference showcase</b> (Block Y).
 *
 * <p>The T457 "coverage audit" deliverable in test form: it imports the very
 * same {@code export.json} + {@code atlas-store_content.json} the showcase app
 * ships ({@code frontend/apps/showcase/export/}, committed verbatim under
 * {@code src/test/resources/showcase-it/}) into a real Turing backend on a
 * random HTTP port, then drives the REST/SSE contract the storefront + copilot
 * depend on — proving the showcase's seed actually provisions, indexes, and
 * serves against the live platform (not just type-checks).
 *
 * <p>Self-contained &amp; CI-safe, following {@code TurSdkContractIT}: the
 * embedded Lucene engine's {@code endpointUrl} is rewritten to a throwaway temp
 * dir so the in-process index never collides with a running app or a parallel
 * test JVM; content is indexed synchronously by {@link TurImportExchange}.
 * Chat/agent-side assertions are limited to the contract the agent-less seed can
 * satisfy ({@code chat/enabled → NO_GENAI}); the agent surface lights up only
 * with a configured GenAI agent, asserted elsewhere.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jmx.enabled=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurShowcaseSmokeIT extends AbstractTuringSpringIT {

    private static final String SITE = "atlas-store";
    private static final String LOCALE = "en_US";
    private static final int EXPECTED_PRODUCTS = 200;

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void uniqueJmxObjectNames(DynamicPropertyRegistry registry) {
        registry.add("spring.jmx.unique-names", () -> "true");
    }

    @Autowired
    private TurImportExchange importExchange;

    @Autowired
    private TurSNSiteRepository siteRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = JsonMapper.builder().build();

    private static Path luceneDir;

    @BeforeAll
    void importShowcaseSite() throws IOException {
        luceneDir = Files.createTempDirectory("turing-showcase-it-lucene");

        byte[] zip = buildImportZip(luceneDir);
        MockMultipartFile bundle = new MockMultipartFile(
                "file", "atlas-store.zip", "application/zip", zip);

        ImportResult result = importExchange.importFromMultipartFile(bundle, true, false, null, true);

        assertTrue(result.siteImported() || siteRepository.findByNameIgnoreCase(SITE).isPresent(),
                "Atlas Store site should be imported/present");
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

    // ── CSRF (the storefront/SDK ensureCsrfToken contract) ──

    @Test
    void csrfEndpointExposesXsrfTokenHeader() throws Exception {
        HttpResponse<String> res = get("/csrf");
        assertEquals(200, res.statusCode(), "/csrf must be reachable");
        String token = res.headers().firstValue("X-XSRF-TOKEN").orElse(null);
        assertNotNull(token, "/csrf must surface the token via the X-XSRF-TOKEN header");
        assertTrue(!token.isBlank(), "X-XSRF-TOKEN must be non-blank");
    }

    // ── Wildcard search returns the full seeded catalog ──

    @Test
    void wildcardSearchReturnsFullCatalog() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search?q=*&_setlocale=" + LOCALE + "&rows=1");
        assertEquals(200, res.statusCode());
        int count = json.readTree(res.body()).path("queryContext").path("count").asInt(0);
        assertEquals(EXPECTED_PRODUCTS, count,
                "wildcard search must return all " + EXPECTED_PRODUCTS + " seeded products, got " + count);
    }

    // ── Typed default fields the storefront ProductCard reads ──

    @Test
    void searchExposesTypedDefaultFields() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search?q=*&_setlocale=" + LOCALE);
        assertEquals(200, res.statusCode());

        JsonNode defaultFields = json.readTree(res.body()).path("queryContext").path("defaultFields");
        for (String key : List.of("url", "title", "description", "text", "date", "image")) {
            assertTrue(defaultFields.has(key),
                    "queryContext.defaultFields must declare '" + key + "'");
        }
        assertEquals("title", defaultFields.path("title").asString());
        assertEquals("image", defaultFields.path("image").asString());
    }

    // ── Facets the storefront sidebar + category tabs depend on ──

    @Test
    void searchReturnsConfiguredFacets() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search?q=*&_setlocale=" + LOCALE);
        assertEquals(200, res.statusCode());

        JsonNode facets = json.readTree(res.body()).path("widget").path("facet");
        assertTrue(facets.isArray() && !facets.isEmpty(),
                "the Atlas Store site must return facet widgets");
        // Each facet group carries its field name under `.name` (see the SDK's
        // useTuringFacets, which reads store.data.widget.facet[].name).
        boolean hasCategory = false;
        boolean hasBrand = false;
        for (JsonNode f : facets) {
            String name = f.path("name").asString("");
            if ("category".equals(name)) hasCategory = true;
            if ("brand".equals(name)) hasBrand = true;
        }
        assertTrue(hasCategory, "a 'category' facet must be configured (drives the tabs)");
        assertTrue(hasBrand, "a 'brand' facet must be configured (drives the sidebar)");
    }

    // ── A real keyword query returns matching products ──

    @Test
    void keywordSearchReturnsResults() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search?q=headphones&_setlocale=" + LOCALE);
        assertEquals(200, res.statusCode());
        JsonNode documents = json.readTree(res.body()).path("results").path("document");
        assertTrue(documents.isArray() && !documents.isEmpty(),
                "searching 'headphones' must return at least one product");
    }

    // ── Custom sorts (price/rating/newest …) the SortSelect renders ──

    @Test
    void sortOptionsIncludeShowcaseCustomSorts() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/search/sort-options");
        assertEquals(200, res.statusCode());
        JsonNode arr = json.readTree(res.body());
        assertTrue(arr.isArray() && !arr.isEmpty(), "sort-options must be a non-empty JSON array");
    }

    // ── chat/enabled: agent-less seed reports the documented reason ──

    @Test
    void chatEnabledReportsNoGenAiOnSeed() throws Exception {
        HttpResponse<String> res = get("/sn/" + SITE + "/chat/enabled");
        assertEquals(200, res.statusCode());
        JsonNode body = json.readTree(res.body());
        assertTrue(body.has("enabled") && body.path("enabled").isBoolean(),
                "chat/enabled must return a boolean 'enabled'");
        if (!body.path("enabled").asBoolean()) {
            assertEquals("NO_GENAI", body.path("reason").asString(),
                    "the agent-less showcase seed must report reason=NO_GENAI");
        }
    }

    // ── helpers ──

    private String base() {
        return "http://localhost:" + port + "/api";
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + path)).GET().build();
        return http.send(req, BodyHandlers.ofString());
    }

    private byte[] buildImportZip(Path targetIndexDir) throws IOException {
        byte[] exportJson = rewriteLuceneEndpoint(readResource("/showcase-it/export.json"), targetIndexDir);
        byte[] contentJson = readResource("/showcase-it/atlas-store_content.json");

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
