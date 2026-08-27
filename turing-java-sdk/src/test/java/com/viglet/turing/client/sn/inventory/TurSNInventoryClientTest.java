/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;

/**
 * T1035 / §XX.20 — the client drives the cursor so the caller does not, which
 * only a real multi-page conversation proves. Tested over HTTP for the same
 * reason the manifest client is: a mocked transport would not show that the
 * query string, the credential and the cursor actually arrive.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNInventoryClientTest {

    private HttpServer server;
    private final List<String> queries = new ArrayList<>();
    private final AtomicReference<String> lastKeyHeader = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    /** Answers a request by its query string, so a paged conversation is scriptable. */
    private final AtomicReference<Function<String, String>> responder = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sn", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        queries.add(exchange.getRequestURI().getPath() + "?" + query);
        lastKeyHeader.set(exchange.getRequestHeaders().getFirst("Key"));
        byte[] body = responder.get().apply(query == null ? "" : query)
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private TurSNServer snServer() {
        return new TurSNServer(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "Catalog", Locale.US, new TurApiKeyCredentials("key-123"));
    }

    private static String page(String locale, List<String> locales, String entriesJson,
            String nextCursor, boolean hasMore, long remaining, boolean supported) {
        String localesJson = locales.stream().map("\"%s\""::formatted)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String cursorJson = nextCursor == null ? "null" : "\"" + nextCursor + "\"";
        return """
                {"siteId":"s1","siteName":"Catalog","locale":"%s","locales":%s,\
                "source":"aem","entries":[%s],"nextCursor":%s,"hasMore":%s,\
                "remaining":%d,"supported":%s}"""
                .formatted(locale, localesJson, entriesJson, cursorJson, hasMore, remaining,
                        supported);
    }

    private static String entry(String id, Long version, String checksum) {
        return """
                {"id":"%s","type":"Page","version":%s,"versionField":"modification_date",\
                "checksum":%s}"""
                .formatted(id, version == null ? "null" : version.toString(),
                        checksum == null ? "null" : "\"" + checksum + "\"");
    }

    @Test
    @DisplayName("A page request carries the site, the filters and the credential")
    void pageRequestCarriesEverything() {
        responder.set(q -> page("en-US", List.of("en-US"), entry("a", 5L, "sum-a"), "a", false, 1,
                true));

        var page = new TurSNInventoryClient(snServer()).page("aem", "en-US", "prev");

        assertThat(queries).hasSize(1);
        assertThat(queries.get(0)).startsWith("/api/sn/Catalog/inventory?")
                .contains("rows=200").contains("source=aem").contains("locale=en-US")
                .contains("after=prev");
        assertThat(lastKeyHeader.get()).isEqualTo("key-123");
        assertThat(page.entries()).hasSize(1);
        assertThat(page.entries().get(0).checksum()).isEqualTo("sum-a");
        assertThat(page.entries().get(0).version()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Absent filters are left out of the query rather than sent empty")
    void absentFiltersAreOmitted() {
        responder.set(q -> page("en-US", List.of("en-US"), "", null, false, 0, true));

        new TurSNInventoryClient(snServer()).page(null, "  ", null);

        assertThat(queries.get(0)).doesNotContain("source=").doesNotContain("locale=")
                .doesNotContain("after=");
    }

    @Test
    @DisplayName("Iterating entries walks every page, so the caller writes no cursor loop")
    void iterationWalksThePages() {
        responder.set(query -> {
            if (query.contains("after=a")) {
                return page("en-US", List.of("en-US"), entry("b", 6L, "sum-b"), "b", false, 1, true);
            }
            return page("en-US", List.of("en-US"), entry("a", 5L, "sum-a"), "a", true, 2, true);
        });

        List<String> ids = new ArrayList<>();
        new TurSNInventoryClient(snServer()).entries("aem", "en-US")
                .forEach(e -> ids.add(e.id()));

        assertThat(ids).containsExactly("a", "b");
        assertThat(queries).hasSize(2);
    }

    @Test
    @DisplayName("allLocales sweeps every locale the site reports, in order")
    void allLocalesSweepsEveryLocale() {
        responder.set(query -> {
            List<String> locales = List.of("en-US", "pt-BR");
            if (query.contains("locale=pt-BR")) {
                return page("pt-BR", locales, entry("p1", 7L, "sum-p1"), "p1", false, 1, true);
            }
            if (query.contains("locale=en-US")) {
                return page("en-US", locales, entry("e1", 5L, "sum-e1"), "e1", false, 1, true);
            }
            // The probe that discovers the locale list.
            return page("en-US", locales, entry("e1", 5L, "sum-e1"), "e1", false, 1, true);
        });

        List<String> ids = new ArrayList<>();
        new TurSNInventoryClient(snServer()).allLocales("aem").forEach(e -> ids.add(e.id()));

        assertThat(ids).containsExactly("e1", "p1");
    }

    @Test
    @DisplayName("A page that reports no cursor ends the walk instead of re-serving itself")
    void unadvanceableCursorTerminates() {
        // hasMore=true but nextCursor=null: without the guard this iterates forever.
        responder.set(q -> page("en-US", List.of("en-US"), entry("a", 5L, null), null, true, 99,
                true));

        List<String> ids = new ArrayList<>();
        new TurSNInventoryClient(snServer()).entries("aem", "en-US").forEach(e -> ids.add(e.id()));

        assertThat(ids).containsExactly("a");
    }

    @Test
    @DisplayName("An engine that cannot enumerate its index throws — it is not an empty index")
    void unsupportedEngineThrows() {
        responder.set(q -> page("en-US", List.of("en-US"), "", null, false, -1, false));
        var client = new TurSNInventoryClient(snServer());

        assertThatThrownBy(() -> client.page("aem", null, null))
                .isInstanceOf(TurSNInventoryException.class)
                .hasMessageContaining("not an empty index");
    }

    @Test
    @DisplayName("An engine that answered with nothing is an empty index, and iterates empty")
    void emptyIndexIteratesEmpty() {
        responder.set(q -> page("en-US", List.of("en-US"), "", null, false, 0, true));

        var page = new TurSNInventoryClient(snServer()).page("aem", null, null);
        assertThat(page.isEmptyIndex()).isTrue();
        assertThat(new TurSNInventoryClient(snServer()).entries("aem", "en-US")).isEmpty();
    }

    @Test
    @DisplayName("A 404 says the site was not found, so a retry is pointless")
    void siteNotFoundIsReported() {
        status.set(404);
        responder.set(q -> "");
        var client = new TurSNInventoryClient(snServer());

        assertThatThrownBy(() -> client.page("aem", null, null))
                .isInstanceOf(TurSNInventoryException.class)
                .satisfies(e -> assertThat(((TurSNInventoryException) e).isSiteNotFound()).isTrue());
    }

    @Test
    @DisplayName("A 400 surfaces the server's own sentence — a bad locale, typically")
    void badRequestSurfacesTheServerMessage() {
        status.set(400);
        responder.set(q -> "{\"error\":\"Site 'Catalog' has no index for locale 'de'\"}");
        var client = new TurSNInventoryClient(snServer());

        assertThatThrownBy(() -> client.page("aem", "de", null))
                .isInstanceOf(TurSNInventoryException.class)
                .hasMessageContaining("no index for locale 'de'");
    }

    @Test
    @DisplayName("An unreachable server names the URL rather than iterating empty")
    void unreachableServerFails() {
        var offline = new TurSNServer(URI.create("http://127.0.0.1:1"), "Catalog", Locale.US);
        var client = new TurSNInventoryClient(offline);

        assertThatThrownBy(() -> client.page("aem", null, null))
                .isInstanceOf(TurSNInventoryException.class)
                .hasMessageContaining("Could not reach the inventory endpoint");
    }

    @Test
    @DisplayName("An unknown response property does not break an older connector")
    void unknownPropertiesAreTolerated() {
        responder.set(q -> """
                {"siteId":"s1","siteName":"Catalog","locale":"en-US","locales":["en-US"],\
                "entries":[],"hasMore":false,"remaining":0,"supported":true,"somethingNew":42}""");

        assertThat(new TurSNInventoryClient(snServer()).page("aem", null, null).siteId())
                .isEqualTo("s1");
    }

    @Test
    @DisplayName("TurSNServer.indexInventory reaches the same sweep, so no second client is needed")
    void serverConvenienceDelegates() {
        responder.set(q -> page("en-US", List.of("en-US"), entry("a", 5L, "sum-a"), "a", false, 1,
                true));

        List<String> ids = new ArrayList<>();
        snServer().indexInventory("aem").forEach(e -> ids.add(e.id()));

        assertThat(ids).containsExactly("a");
    }

    @Test
    @DisplayName("A custom page size reaches the server")
    void customRowsReachTheServer() {
        responder.set(q -> page("en-US", List.of("en-US"), "", null, false, 0, true));

        new TurSNInventoryClient(snServer(), "site-1", 25).page(null, null, null);

        assertThat(queries.get(0)).startsWith("/api/sn/site-1/inventory?").contains("rows=25");
    }
}
