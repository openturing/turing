package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.auth.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;

class TurSNServerTest {

    @Test
    void shouldInitializeFromUsernamePasswordConstructor() {
        TurUsernamePasswordCredentials credentials = new TurUsernamePasswordCredentials("john", "secret");

        TurSNServer server = new TurSNServer(
                URI.create("http://localhost:2700"),
                "Portal",
                Locale.US,
                credentials,
                "john");

        assertThat(server.getServerURL()).isEqualTo(URI.create("http://localhost:2700"));
        assertThat(server.getSiteName()).isEqualTo("Portal");
        assertThat(server.getLocale()).isEqualTo(Locale.US);
        assertThat(server.getCredentials()).isSameAs(credentials);
        assertThat(server.getProviderName()).isEqualTo("turing-java-sdk");
        assertThat(server.getSnServer()).isEqualTo("http://localhost:2700/api/sn/Portal");
        assertThat(server.getTurSNSitePostParams().getUserId()).isEqualTo("john");
        assertThat(server.getTurSNSitePostParams().isPopulateMetrics()).isTrue();
    }

    @Test
    void shouldInitializeFromApiKeyConstructorAndReturnEmptyLatestSearchesWithoutCredentials() {
        TurApiKeyCredentials apiKeyCredentials = new TurApiKeyCredentials("my-api-key");

        TurSNServer server = new TurSNServer(
                URI.create("http://localhost:2700"),
                "Portal",
                Locale.US,
                apiKeyCredentials,
                "user-id");

        assertThat(server.getApiKey()).isEqualTo("my-api-key");
        assertThat(server.getCredentials()).isNull();
        assertThat(server.getLatestSearches(5)).isEqualTo(Collections.emptyList());
    }

    @Test
    void shouldUseDefaultSiteAndLocaleWhenUsingShortConstructor() {
        TurSNServer server = new TurSNServer(URI.create("http://localhost:2700"), new TurApiKeyCredentials("k"));

        assertThat(server.getSiteName()).isEqualTo("Sample");
        assertThat(server.getLocale()).isEqualTo(Locale.US);
        assertThat(server.getSnServer()).isEqualTo("http://localhost:2700/api/sn/Sample");
    }

    @Test
    void shouldReadLocalesAndAutoCompleteFromHttpEndpoints() throws Exception {
        try (TestHttpServer testHttpServer = TestHttpServer.start()) {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:" + testHttpServer.port()),
                    "Portal",
                    Locale.US,
                    new TurApiKeyCredentials("api-key"),
                    "user-id");

            List<TurSNLocale> locales = server.getLocales();
            assertThat(locales).hasSize(1);
            assertThat(locales.getFirst().getLocale()).isEqualTo("en-US");
            assertThat(locales.getFirst().getLink()).isEqualTo("/en-US");

            TurSNAutoCompleteQuery autoCompleteQuery = new TurSNAutoCompleteQuery();
            autoCompleteQuery.setQuery("vig");
            autoCompleteQuery.setRows(2);

            assertThat(server.autoComplete(autoCompleteQuery)).containsExactly("viglet", "vigor");
        }
    }

    @Test
    void shouldReadLatestSearchesWhenCredentialsAreProvided() throws Exception {
        try (TestHttpServer testHttpServer = TestHttpServer.start()) {
            TurSNServer server = new TurSNServer(
                    URI.create("http://localhost:" + testHttpServer.port()),
                    "Portal",
                    Locale.US,
                    new TurUsernamePasswordCredentials("john", "secret"),
                    "john");

            assertThat(server.getLatestSearches(10)).containsExactly("first search", "second search");
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        private final HttpServer server;

        private TestHttpServer(HttpServer server) {
            this.server = server;
        }

        static TestHttpServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/sn/Portal/search/locales",
                    exchange -> writeJsonResponse(exchange, "[{\"locale\":\"en-US\",\"link\":\"/en-US\"}]"));
            server.createContext("/api/sn/Portal/ac",
                    exchange -> writeJsonResponse(exchange, "[\"viglet\",\"vigor\"]"));
            server.createContext("/api/sn/Portal/search/latest",
                    exchange -> writeJsonResponse(exchange, "[\"first search\",\"second search\"]"));
            server.start();
            return new TestHttpServer(server);
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void writeJsonResponse(HttpExchange exchange, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }
    }
}
