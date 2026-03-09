package com.viglet.turing.client.sn.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.client.auth.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.TurSNServer;

class TurSNJobUtilsTest {

    @Test
    void utilityConstructorShouldThrowIllegalStateException() throws Exception {
        Constructor<TurSNJobUtils> constructor = TurSNJobUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldReturnFalseWhenImportJobIsNullOrEmpty() {
        TurSNServer turSNServer = mock(TurSNServer.class);

        assertThat(TurSNJobUtils.importItems(null, turSNServer, false)).isFalse();
        assertThat(TurSNJobUtils.importItems(new TurSNJobItems(), turSNServer, false)).isFalse();
    }

    @Test
    void shouldImportItemsWithSuccessStatus() throws Exception {
        try (ImportHttpServer server = ImportHttpServer.start(200)) {
            TurSNServer turSNServer = new TurSNServer(
                    URI.create("http://localhost:" + server.port()),
                    "Portal",
                    Locale.US,
                    new TurUsernamePasswordCredentials("john", "secret"),
                    "john");

            TurSNJobItems items = new TurSNJobItems();
            items.add(new TurSNJobItem(TurSNJobAction.CREATE, Collections.singletonList("Portal"), Locale.US));

            assertThat(TurSNJobUtils.importItems(items, turSNServer, true)).isTrue();
            assertThat(server.lastBody()).contains("CREATE");
        }
    }

    @Test
    void shouldReturnFalseWhenImportReturnsNon200() throws Exception {
        try (ImportHttpServer server = ImportHttpServer.start(500)) {
            TurSNServer turSNServer = new TurSNServer(
                    URI.create("http://localhost:" + server.port()),
                    "Portal",
                    Locale.US,
                    new TurUsernamePasswordCredentials("john", "secret"),
                    "john");

            TurSNJobItems items = new TurSNJobItems();
            items.add(new TurSNJobItem(TurSNJobAction.COMMIT, Collections.singletonList("Portal"), Locale.US));

            assertThat(TurSNJobUtils.importItems(items, turSNServer, false)).isFalse();
        }
    }

    @Test
    void shouldBuildDeleteItemsByTypePayload() throws Exception {
        try (ImportHttpServer server = ImportHttpServer.start(200)) {
            TurSNServer turSNServer = new TurSNServer(
                    URI.create("http://localhost:" + server.port()),
                    "Portal",
                    Locale.US,
                    new TurUsernamePasswordCredentials("john", "secret"),
                    "john");

            TurSNJobUtils.deleteItemsByType(turSNServer, "Page");

            assertThat(server.lastBody()).contains("DELETE");
            assertThat(server.lastBody()).contains("\"type\":\"Page\"");
            assertThat(server.lastBody()).contains("\"source_apps\":\"turing-java-sdk\"");
        }
    }

    private static final class ImportHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> lastBody;

        private ImportHttpServer(HttpServer server, AtomicReference<String> lastBody) {
            this.server = server;
            this.lastBody = lastBody;
        }

        static ImportHttpServer start(int statusCode) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            AtomicReference<String> bodyRef = new AtomicReference<>("");
            server.createContext("/api/sn/import", exchange -> handleImport(exchange, bodyRef, statusCode));
            server.start();
            return new ImportHttpServer(server, bodyRef);
        }

        int port() {
            return server.getAddress().getPort();
        }

        String lastBody() {
            return lastBody.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void handleImport(HttpExchange exchange, AtomicReference<String> bodyRef, int code)
                throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodyRef.set(body);
            byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }
    }
}
