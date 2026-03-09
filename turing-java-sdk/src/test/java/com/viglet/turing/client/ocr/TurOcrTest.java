package com.viglet.turing.client.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;

class TurOcrTest {

    @Test
    void shouldExposeExpectedConstants() {
        assertThat(TurOcr.TIMEOUT_MINUTES).isEqualTo(5);
        assertThat(TurOcr.API_OCR_URL).contains("/api/ocr/url");
        assertThat(TurOcr.API_OCR_FILE).contains("/api/ocr/file");
        assertThat(TurOcr.FILE).isEqualTo("file");
        assertThat(TurOcr.URL).isEqualTo("url");
    }

    @Test
    void shouldCreateConnectionPoolOnConstruction() throws Exception {
        TurOcr turOcr = new TurOcr();
        Field poolField = TurOcr.class.getDeclaredField("pool");
        poolField.setAccessible(true);

        assertThat(poolField.get(turOcr)).isNotNull();
    }

    @Test
    void shouldBuildJsonRequestEntityFromUrlUsingPrivateHelper() throws Exception {
        Method method = TurOcr.class.getDeclaredMethod("getRequestEntity", URI.class);
        method.setAccessible(true);

        StringEntity entity = (StringEntity) method.invoke(null, URI.create("https://example.org/doc.pdf"));

        assertThat(entity.getContentType()).contains("application/json");
    }

    @Test
    void shouldBuildMultipartRequestEntityFromFileUsingPrivateHelper() throws Exception {
        Method method = TurOcr.class.getDeclaredMethod("getRequestEntity", File.class);
        method.setAccessible(true);

        HttpEntity entity = (HttpEntity) method.invoke(null, new File("example.pdf"));

        assertThat(entity).isNotNull();
        assertThat(entity.getContentType()).isNotNull();
    }

    @Test
    void shouldProcessUrlAndFileAgainstLocalServer() throws Exception {
        try (OcrHttpServer server = OcrHttpServer.start()) {
            TurServer turServer = new TurServer(
                    URI.create("http://localhost:" + server.port()),
                    new TurApiKeyCredentials("api-key"));

            assertThat(new TurOcr().processUrl(turServer, URI.create("http://example.com/file.pdf"), false).getName())
                    .isEqualTo("ocr-ok");

            File tempFile = Files.createTempFile("ocr-test", ".txt").toFile();
            Files.writeString(tempFile.toPath(), "sample", StandardCharsets.UTF_8);
            try {
                assertThat(new TurOcr().processFile(turServer, tempFile, false).getName()).isEqualTo("ocr-ok");
            } finally {
                tempFile.delete();
            }
        }
    }

    @Test
    void shouldReturnEmptyAttributesWhenServerIsUnavailable() {
        TurOcr turOcr = new TurOcr();
        TurServer turServer = new TurServer(URI.create("http://localhost:1"), new TurApiKeyCredentials("api-key"));

        assertThat(turOcr.processUrl(turServer, URI.create("http://example.com/file.pdf"), false).getName()).isNull();
    }

    private static final class OcrHttpServer implements AutoCloseable {
        private final HttpServer server;

        private OcrHttpServer(HttpServer server) {
            this.server = server;
        }

        static OcrHttpServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/ocr/url", OcrHttpServer::handle);
            server.createContext("/api/ocr/file", OcrHttpServer::handle);
            server.start();
            return new OcrHttpServer(server);
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void handle(HttpExchange exchange) throws IOException {
            byte[] payload = "{\"name\":\"ocr-ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }
    }
}
