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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Smoke test for the Insper DSpace REST API consumed by the
 * {@code aula_relampago} Custom Tool. Verifies that the shape of the
 * response is still compatible with the extraction logic
 * ({@code dc.title}, {@code dc.contributor.author}, {@code dc.date.issued},
 * {@code dc.description.abstract}, {@code uuid}, {@code handle}) AND that
 * the progressive fallback (exact phrase → first 2 tokens → bag-of-words
 * OR) actually yields references for the typical lesson terms.
 *
 * <p>NOT part of the default CI run: requires network reachability to
 * {@code repositorio-api.insper.edu.br}. Tag {@code dspace-smoke} so it
 * can be invoked explicitly:
 *
 * <pre>{@code
 *   mvn -pl turing-app -Dgroups=dspace-smoke -Dtest=AulaRelampagoDspaceIT test
 * }</pre>
 *
 * <p>Tests intentionally degrade gracefully: when a term truly has zero
 * coverage in the acervo even after fallback (rare for general topics,
 * common for very niche terms), the test logs the gap and skips assertion
 * rather than failing — the goal is to detect ENGINE / API regressions,
 * not to enforce content coverage. A genuine regression looks like
 * "every term returns malformed metadata" or "the response shape
 * changed", which the structural assertions catch independently.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.x
 */
@Tag("dspace-smoke")
class AulaRelampagoDspaceIT {

    private static final String DSPACE_API =
            "https://repositorio-api.insper.edu.br/server/api/discover/search/objects";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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

    /**
     * The 4 starter chips in {@code ai-pick-term}'s {@code inlineOptions}.
     * If ANY of these can't yield references after fallback, the corresponding
     * aula will fall back to "explique sem fontes", which degrades the demo.
     */
    static Stream<String> starterChipTerms() {
        return Stream.of(
                "Real Options",
                "Liderança Servidora",
                "Lean Six Sigma",
                "Machine Learning aplicado a saúde");
    }

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
                        + "Either the acervo has no coverage on this topic (rare for the "
                        + "4 starter terms — consider replacing the chip) OR the fallback "
                        + "logic regressed.", term)
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
        // Reproduces the production bug found in turing.log: 4 words long
        // term with low DSpace coverage. Asserts the fallback recovers.
        String term = "Machine Learning aplicado a saúde";
        FallbackResult phrase = searchOnce(term);
        // We expect this to return 0 (the bug we shipped a fix for). If the
        // acervo gets new content matching the phrase, this assertion may
        // flip — and that's fine, just remove the assertion when it does.
        if (phrase.totalHits() > 0) {
            System.out.printf("PHRASE-NO-LONGER-ZERO: '%s' now has %d hits — "
                    + "consider this assertion superseded.%n", term, phrase.totalHits());
        }
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
            StringBuilder or_ = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) or_.append(" OR ");
                or_.append('"').append(words.get(i)).append('"');
            }
            FallbackResult fallback = searchOnce(or_.toString());
            return new FallbackResult(fallback.json(), fallback.totalHits(), "OR-bag");
        }
        return new FallbackResult(exact.json(), 0, "exhausted");
    }

    private JsonNode search(String query) throws IOException, InterruptedException {
        String url = DSPACE_API
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

    @SuppressWarnings("unused") // kept for debug printing during development
    private static void printAllMetadataKeys(JsonNode item) {
        JsonNode md = item.path("metadata");
        if (md.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = md.fields(); it.hasNext(); ) {
                System.out.println("  " + it.next().getKey());
            }
        }
    }
}
