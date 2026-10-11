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
package com.viglet.turing.client.sn.manifest;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * T1030 / §XX.17 — the client is HTTP, so it is tested over HTTP. A stub in front
 * of a mocked {@code CloseableHttpClient} would not prove that the payload
 * arrives at {@code /api/sn/manifest}, that the credential is sent, or that a 409
 * body parses back into the breaking changes a connector reports.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNManifestClientTest {

    private static final String RESULT_JSON = """
            {"siteId":"s1","siteName":"Catalog","siteCreated":true,\
            "fieldsCreated":["sku"],"fieldsSkipped":[],"fieldsMigrated":[],\
            "localesCreated":["en-US"],"schemaVersion":"3"}""";

    private static final String DIFF_JSON = """
            {"schemaVersion":"3","siteExists":true,"fieldsToAdd":["stock"],\
            "breakingChanges":[{"field":"price","attribute":"type","liveValue":"STRING",\
            "manifestValue":"CURRENCY","migrationDeclared":false}],\
            "fieldsUnchanged":["sku"],"localesToAdd":[]}""";

    private static final String CONFLICT_JSON = """
            {"error":"Manifest declares breaking change(s) without a migration",\
            "breakingChanges":[{"field":"price","attribute":"type","liveValue":"STRING",\
            "manifestValue":"CURRENCY","migrationDeclared":false}]}""";

    private HttpServer server;
    private final List<String> paths = new ArrayList<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastKeyHeader = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>(RESULT_JSON);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sn/manifest", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        paths.add(exchange.getRequestURI().getPath());
        lastKeyHeader.set(exchange.getRequestHeaders().getFirst("Key"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private TurSNServer server() {
        return new TurSNServer(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "Catalog", Locale.US, new TurApiKeyCredentials("key-123"));
    }

    private static TurSNManifest manifest() {
        return TurSNManifest.builder()
                .name("Catalog")
                .locale("en-US")
                .field(TurSNJobAttributeSpec.builder()
                        .name("sku")
                        .type(TurSEFieldType.STRING)
                        .build())
                .build();
    }

    @Test
    @DisplayName("provision posts the manifest to /api/sn/manifest with the server's credential")
    void provisionPostsTheManifest() {
        var result = new TurSNManifestClient(server()).provision(manifest());

        assertThat(paths).containsExactly("/api/sn/manifest");
        assertThat(lastKeyHeader.get()).isEqualTo("key-123");
        assertThat(lastBody.get()).contains("\"name\":\"Catalog\"").contains("\"sku\"");
        assertThat(result.siteId()).isEqualTo("s1");
        assertThat(result.siteCreated()).isTrue();
        assertThat(result.fieldsCreated()).containsExactly("sku");
        assertThat(result.isNoOp()).isFalse();
    }

    @Test
    @DisplayName("A field's searchWeight is sent with the manifest, and an unweighted field sends none (T1082)")
    void provisionSendsTheSearchWeight() {
        var manifest = TurSNManifest.builder()
                .name("Catalog")
                .locale("en-US")
                .field(TurSNJobAttributeSpec.builder()
                        .name("title")
                        .type(TurSEFieldType.TEXT)
                        .searchWeight(3f)
                        .build())
                .field(TurSNJobAttributeSpec.builder()
                        .name("text")
                        .type(TurSEFieldType.TEXT)
                        .build())
                .build();

        new TurSNManifestClient(server()).provision(manifest);

        var fields = new tools.jackson.databind.ObjectMapper().readTree(lastBody.get()).get("fields");
        assertThat(fields.get(0).get("name").asString()).isEqualTo("title");
        assertThat(fields.get(0).get("searchWeight").asDouble()).isEqualTo(3.0);
        // null leaves the site's weight alone, which is what the endpoint does with it
        var unweighted = fields.get(1).get("searchWeight");
        assertThat(unweighted == null || unweighted.isNull()).isTrue();
    }

    @Test
    @DisplayName("plan posts to the /plan sub-path and reads the diff back")
    void planPostsToThePlanPath() {
        responseBody.set(DIFF_JSON);

        var diff = new TurSNManifestClient(server()).plan(manifest());

        assertThat(paths).containsExactly("/api/sn/manifest/plan");
        assertThat(diff.fieldsToAdd()).containsExactly("stock");
        assertThat(diff.undeclaredBreakingChanges()).extracting(TurSNManifestFieldChange::field)
                .containsExactly("price");
    }

    @Test
    @DisplayName("A 409 comes back as a migration-required failure carrying the fields it would rewrite")
    void conflictCarriesTheBreakingChanges() {
        status.set(409);
        responseBody.set(CONFLICT_JSON);
        var client = new TurSNManifestClient(server());
        var manifest = manifest();

        assertThatThrownBy(() -> client.provision(manifest))
                .isInstanceOf(TurSNManifestException.class)
                .hasMessageContaining("breaking field change is not declared")
                .satisfies(e -> {
                    var failure = (TurSNManifestException) e;
                    assertThat(failure.isMigrationRequired()).isTrue();
                    assertThat(failure.getStatusCode()).isEqualTo(409);
                    assertThat(failure.getBreakingChanges())
                            .extracting(TurSNManifestFieldChange::field)
                            .containsExactly("price");
                });
    }

    @Test
    @DisplayName("A 400 surfaces the server's own sentence, not just the status")
    void badRequestSurfacesTheServerMessage() {
        status.set(400);
        responseBody.set("{\"error\":\"Manifest needs at least one locale\"}");
        var client = new TurSNManifestClient(server());
        var manifest = manifest();

        assertThatThrownBy(() -> client.provision(manifest))
                .isInstanceOf(TurSNManifestException.class)
                .hasMessageContaining("Manifest needs at least one locale")
                .satisfies(e -> assertThat(((TurSNManifestException) e).isMigrationRequired())
                        .isFalse());
    }

    @Test
    @DisplayName("A 403 from an index-only credential is thrown, never swallowed into a no-op")
    void forbiddenIsThrown() {
        status.set(403);
        responseBody.set("");
        var client = new TurSNManifestClient(server());
        var manifest = manifest();

        assertThatThrownBy(() -> client.provision(manifest))
                .isInstanceOf(TurSNManifestException.class)
                .hasMessageContaining("403");
    }

    @Test
    @DisplayName("An unknown property in the response does not break an older connector")
    void unknownResponsePropertiesAreTolerated() {
        responseBody.set("{\"siteId\":\"s1\",\"siteName\":\"Catalog\",\"siteCreated\":false,"
                + "\"somethingNew\":42}");

        var result = new TurSNManifestClient(server()).provision(manifest());

        assertThat(result.siteId()).isEqualTo("s1");
        assertThat(result.isNoOp()).isTrue();
    }

    @Test
    @DisplayName("A manifest with no site name is refused before a request is made")
    void namelessManifestIsRefusedLocally() {
        var client = new TurSNManifestClient(server());
        var nameless = TurSNManifest.builder().locale("en-US").build();

        assertThatThrownBy(() -> client.provision(nameless))
                .isInstanceOf(TurSNManifestException.class)
                .hasMessageContaining("site name");
        assertThat(paths).isEmpty();
    }

    @Test
    @DisplayName("An unreachable server is a failure with the URL, not a silent false")
    void unreachableServerFails() {
        var offline = new TurSNServer(URI.create("http://127.0.0.1:1"), "Catalog", Locale.US);
        var client = new TurSNManifestClient(offline);
        var manifest = manifest();

        assertThatThrownBy(() -> client.provision(manifest))
                .isInstanceOf(TurSNManifestException.class)
                .hasMessageContaining("Could not reach the manifest endpoint");
    }

    @Test
    @DisplayName("TurSNServer.provision reaches the same endpoint, so a connector needs no second client")
    void serverConvenienceDelegates() {
        var result = server().provision(manifest());

        assertThat(paths).containsExactly("/api/sn/manifest");
        assertThat(result.siteName()).isEqualTo("Catalog");
    }
}
