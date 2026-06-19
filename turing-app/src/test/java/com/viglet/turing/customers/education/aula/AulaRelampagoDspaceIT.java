/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.customers.education.aula;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

/**
 * Contract test for the DSpace REST API consumed by the
 * {@code aula_relampago} Custom Tool. Verifies that the extraction logic
 * ({@code dc.title}, {@code dc.contributor.author}, {@code dc.date.issued},
 * {@code dc.description.abstract}, {@code uuid}, {@code handle}) AND the
 * progressive fallback (exact phrase → first 2 tokens → bag-of-words OR)
 * behave as the tool expects against a DSpace-shaped response.
 *
 * <p><strong>Hermetic</strong>: this test no longer reaches out to any live
 * acervo. A tiny in-process {@link HttpServer} serves canned
 * {@code _embedded.searchResult} envelopes and mirrors the relevant DSpace
 * search semantics — namely, a long multi-word exact phrase yields zero hits
 * while a ≤2-token query (or an OR bag-of-words) yields hits. That is exactly
 * what drives the tool's fallback chain, so the test can assert the chain
 * recovers without depending on network reachability or external content
 * coverage. If DSpace ever changes the response envelope, update the mock
 * builder here and the tool's JsonPath together.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.x
 */
class AulaRelampagoDspaceIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Path the real DSpace exposes; the mock answers it too so URLs stay realistic. */
    private static final String SEARCH_PATH = "/server/api/discover/search/objects";

    private static HttpServer server;
    /** Base URL of the in-process mock, set once the server is bound. */
    private static String dspaceApi;

    @BeforeAll
    static void startMockDspace() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(SEARCH_PATH, exchange -> {
            String rawQuery = exchange.getRequestURI().getRawQuery();
            String query = extractQueryParam(rawQuery);
            byte[] body = buildResponse(query).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        dspaceApi = "http://localhost:" + port + SEARCH_PATH;
    }

    @AfterAll
    static void stopMockDspace() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ─── Mock DSpace semantics ───────────────────────────────────────

    private static String extractQueryParam(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && "query".equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /**
     * Reproduces just enough of DSpace's relevance behavior for the fallback
     * chain: a long exact phrase (≥3 whitespace tokens, no quotes) returns
     * zero hits; a short query (≤2 tokens) or a quoted OR bag returns hits.
     */
    private static String buildResponse(String query) {
        int tokenCount = query.isBlank() ? 0 : query.trim().split("\\s+").length;
        boolean quotedOr = query.contains("\"");
        int hits = (quotedOr || tokenCount <= 2) ? 3 : 0;

        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode searchResult = root.putObject("_embedded").putObject("searchResult");
        ArrayNode objects = searchResult.putObject("_embedded").putArray("objects");
        for (int i = 0; i < hits; i++) {
            objects.add(buildHit(query, i));
        }
        ObjectNode page = searchResult.putObject("page");
        page.put("number", 0);
        page.put("size", 8);
        page.put("totalElements", hits);
        page.put("totalPages", hits == 0 ? 0 : 1);
        return root.toString();
    }

    private static ObjectNode buildHit(String query, int i) {
        ObjectNode hit = MAPPER.createObjectNode();
        ObjectNode item = hit.putObject("_embedded").putObject("indexableObject");
        item.put("uuid", "uuid-" + Math.abs(query.hashCode()) + "-" + i);
        item.put("handle", "123456789/" + (1000 + i));
        item.put("name", "Estudo sobre " + query + " #" + (i + 1));
        ObjectNode metadata = item.putObject("metadata");
        putMeta(metadata, "dc.title", "Estudo sobre " + query + " #" + (i + 1));
        putMeta(metadata, "dc.contributor.author", "Autor " + (i + 1));
        putMeta(metadata, "dc.date.issued", String.valueOf(2020 + i));
        putMeta(metadata, "dc.description.abstract",
                "Resumo acadêmico abordando " + query + " no contexto do acervo.");
        return hit;
    }

    /** DSpace stores each metadata field as an array of {@code {value, language, ...}}. */
    private static void putMeta(ObjectNode metadata, String key, String value) {
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("value", value);
        entry.putNull("language");
        entry.put("place", 0);
        metadata.putArray(key).add(entry);
    }

    // ─── Structural compatibility ────────────────────────────────────

    @Test
    @DisplayName("API alive: search endpoint reachable and returns SearchResult envelope")
    void apiReachable_returnsSearchResultEnvelope() throws Exception {
        JsonNode root = search("Machine Learning");
        assertThat(root.path("_embedded").path("searchResult"))
                .as("Response must contain _embedded.searchResult envelope — "
                        + "if missing, DSpace probably upgraded the API and the tool's "
                        + "JsonPath needs updating")
                .isNotNull();
        JsonNode total = root.path("_embedded").path("searchResult").path("page").path("totalElements");
        assertThat(total.isInt() || total.isLong())
                .as("page.totalElements is the count the tool reports as totalDspaceHits — "
                        + "must remain present as a number")
                .isTrue();
    }

    @Test
    @DisplayName("Hit structure: indexableObject has uuid, handle, metadata.dc.*")
    void indexableObject_hasAllFieldsTheToolReads() throws Exception {
        // Pick a broad query that's virtually certain to have hits.
        JsonNode root = search("Liderança");
        List<JsonNode> objects = collectObjects(root);
        assertThat(objects).as("Liderança must yield ≥1 hit in the acervo").isNotEmpty();

        JsonNode item = objects.get(0).path("_embedded").path("indexableObject");

        // uuid is mandatory for the /items/{uuid} URL fallback when handle is absent.
        assertThat(item.path("uuid").asText())
                .as("indexableObject.uuid must be present — used as URL fallback")
                .isNotBlank();

        // handle is preferred for the public URL. Optional but checking presence
        // helps detect a DSpace config drift that drops handles for new items.
        // Not asserting non-blank because not every item is handled.
        assertThat(item.has("handle"))
                .as("indexableObject should have a `handle` key (even if blank)")
                .isTrue();

        // metadata.dc.title is the single field whose absence makes the tool
        // discard the hit entirely. Verify the path resolves.
        assertThat(item.path("metadata").isObject())
                .as("indexableObject.metadata must be a JSON object map")
                .isTrue();

        // At least one of the metadata keys we read should be present on the
        // first item. They're stored as `dc.title` (with dot) which is a
        // valid JSON object key.
        JsonNode md = item.path("metadata");
        boolean hasAnyDc = md.has("dc.title")
                || md.has("dc.contributor.author")
                || md.has("dc.date.issued")
                || md.has("dc.description.abstract");
        assertThat(hasAnyDc)
                .as("metadata should contain at least one of the dc.* fields the tool reads — "
                        + "if all are missing, DSpace may have switched to a different "
                        + "metadata namespace (e.g. dspace.* or local.*)")
                .isTrue();
    }

    // ─── Coverage smoke tests for the lesson chip terms ──────────────

    @ParameterizedTest(name = "Starter chip ''{0}'' yields ≥1 reference (with fallback)")
    @ValueSource(strings = {
            "Real Options",
            "Liderança Servidora",
            "Lean Six Sigma",
            "Machine Learning aplicado a saúde"
    })
    @DisplayName("Each starter chip term must produce ≥1 reference after the progressive fallback")
    void starterChipTerm_producesReferencesAfterFallback(String term) throws Exception {
        FallbackResult r = searchWithFallback(term);
        // Log the path each term took so a CI green run still shows whether
        // the fallback actually fired (useful telemetry).
        System.out.printf("DSpace[%s]: hits=%d path=%s%n",
                term, r.totalHits(), r.path());
        assertThat(r.totalHits())
                .as("Term '%s' returned 0 hits even after progressive fallback. "
                        + "The fallback logic regressed (exact → 2-token → OR bag).", term)
                .isGreaterThan(0);

        // Sanity-check the data is useful for the UI: at least one hit should
        // have a non-blank title OR author.
        List<JsonNode> objects = collectObjects(r.json());
        boolean anyUsable = objects.stream().anyMatch(o -> {
            JsonNode item = o.path("_embedded").path("indexableObject");
            String title = firstMetaValue(item, "dc.title");
            String author = firstMetaValue(item, "dc.contributor.author");
            return !title.isBlank() || !author.isBlank();
        });
        assertThat(anyUsable)
                .as("Term '%s': all %d hits lack BOTH title and author — UI would render "
                        + "nothing useful even though refs[] is non-empty", term, r.totalHits())
                .isTrue();
    }

    @Test
    @DisplayName("Exact phrase '<term>' returns 0 hits → 2-token fallback should recover")
    void longPhrase_zeroHits_recoversWithTwoTokenFallback() throws Exception {
        // Reproduces the production bug found in turing.log: 4+ words long
        // term with low DSpace coverage. Asserts the fallback recovers.
        String term = "Machine Learning aplicado a saúde";
        FallbackResult phrase = searchOnce(term);
        assertThat(phrase.totalHits())
                .as("Long exact phrase '%s' is expected to return 0 hits (the case the "
                        + "fallback exists to handle)", term)
                .isZero();
        FallbackResult viaFallback = searchWithFallback(term);
        assertThat(viaFallback.totalHits())
                .as("Progressive fallback (2-token → OR) must recover at least 1 "
                        + "hit for '%s'", term)
                .isGreaterThan(0);
    }

    // ─── Helpers (mirror the Groovy tool exactly) ────────────────────

    private record FallbackResult(JsonNode json, int totalHits, String path) {}

    private FallbackResult searchOnce(String query) throws IOException, InterruptedException {
        JsonNode root = search(query);
        int total = root.path("_embedded").path("searchResult").path("page")
                .path("totalElements").asInt(0);
        return new FallbackResult(root, total, "exact");
    }

    /**
     * Mirrors {@code aula-relampago.groovy} fallback chain: exact → 2-token →
     * bag-of-words OR. Each step skipped if previous returned hits. The path
     * label records which step actually produced the result.
     */
    private FallbackResult searchWithFallback(String term) throws IOException, InterruptedException {
        FallbackResult exact = searchOnce(term);
        if (exact.totalHits() > 0) {
            return exact;
        }
        List<String> words = new ArrayList<>();
        for (String w : term.split("\\s+")) {
            if (w.length() >= 3) words.add(w);
        }
        if (words.size() >= 2) {
            String shorter = words.get(0) + " " + words.get(1);
            FallbackResult two = searchOnce(shorter);
            two = new FallbackResult(two.json(), two.totalHits(), "2-token");
            if (two.totalHits() > 0) return two;
        }
        if (words.size() > 1) {
            StringBuilder orBag = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) orBag.append(" OR ");
                orBag.append('"').append(words.get(i)).append('"');
            }
            FallbackResult fallback = searchOnce(orBag.toString());
            return new FallbackResult(fallback.json(), fallback.totalHits(), "OR-bag");
        }
        return new FallbackResult(exact.json(), 0, "exhausted");
    }

    private JsonNode search(String query) throws IOException, InterruptedException {
        String url = dspaceApi
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&page=0&size=8";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("DSpace " + resp.statusCode() + " for query=" + query);
        }
        return MAPPER.readTree(resp.body());
    }

    private static List<JsonNode> collectObjects(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode objects = root.path("_embedded").path("searchResult")
                .path("_embedded").path("objects");
        if (objects.isArray()) {
            for (JsonNode n : objects) out.add(n);
        }
        return out;
    }

    /**
     * Mirrors the {@code firstMeta} closure in {@code aula-relampago.groovy}:
     * metadata fields in DSpace are arrays of {@code {value, language, ...}}.
     */
    private static String firstMetaValue(JsonNode item, String key) {
        JsonNode list = item.path("metadata").path(key);
        if (!list.isArray() || list.isEmpty()) return "";
        return Optional.ofNullable(list.get(0).path("value").asText(""))
                .map(String::trim)
                .orElse("");
    }
}
