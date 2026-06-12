/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * T338 — wire-contract coverage for {@link TurHttpRerankClient} against a real
 * loopback HTTP server (JDK built-in, no extra dependency). Pins both response
 * shapes, index bounds-checking, the request body, and the error path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurHttpRerankClientTest {

    private final TurHttpRerankClient client = new TurHttpRerankClient();
    private HttpServer server;
    private String endpoint;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void noop() {
        // server is started per-test via stub(...)
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void stub(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rerank", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/rerank";
    }

    @Test
    void parsesBareArrayShapeTeiStyle() throws IOException {
        stub(200, "[{\"index\":2,\"score\":0.9},{\"index\":0,\"score\":0.5}]");

        List<Integer> order = client.rankIndices(endpoint, null, "bge", "q",
                List.of("a", "b", "c"), 2);

        assertThat(order).containsExactly(2, 0);
    }

    @Test
    void parsesResultsEnvelopeCohereStyle() throws IOException {
        stub(200, "{\"results\":[{\"index\":1,\"relevance_score\":0.8},"
                + "{\"index\":2,\"relevance_score\":0.7}]}");

        List<Integer> order = client.rankIndices(endpoint, "secret-key", "rerank-v3.5", "q",
                List.of("a", "b", "c"), 3);

        assertThat(order).containsExactly(1, 2);
        assertThat(lastAuth.get()).isEqualTo("Bearer secret-key");
    }

    @Test
    void dropsOutOfBoundsAndDuplicateIndices() throws IOException {
        stub(200, "[{\"index\":5},{\"index\":1},{\"index\":1},{\"index\":-1}]");

        List<Integer> order = client.rankIndices(endpoint, null, null, "q",
                List.of("a", "b"), 2);

        assertThat(order).containsExactly(1);
    }

    @Test
    void sendsQueryDocumentsAndTopNInBody() throws IOException {
        stub(200, "[]");

        client.rankIndices(endpoint, null, "bge-reranker", "my question",
                List.of("doc one", "doc two"), 7);

        String body = lastBody.get();
        assertThat(body).contains("\"query\":\"my question\"");
        assertThat(body).contains("\"top_n\":7");
        assertThat(body).contains("\"model\":\"bge-reranker\"");
        assertThat(body).contains("doc one").contains("doc two");
    }

    @Test
    void throwsOnNon2xxStatus() throws IOException {
        stub(500, "boom");

        assertThatThrownBy(() -> client.rankIndices(endpoint, null, null, "q",
                List.of("a", "b"), 2))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }
}
