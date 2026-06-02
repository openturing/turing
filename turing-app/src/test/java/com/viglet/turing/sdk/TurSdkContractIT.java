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
     * Builds an in-memory import zip from the committed marketplace fixtures,
     * rewriting the Lucene {@code endpointUrl} to {@code targetIndexDir} so the
     * test owns an isolated index.
     */
    private byte[] buildImportZip(Path targetIndexDir) throws IOException {
        byte[] exportJson = rewriteLuceneEndpoint(readResource("/sdk-it/export.json"), targetIndexDir);
        byte[] contentJson = readResource("/sdk-it/mythical-creatures_content.json");

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
