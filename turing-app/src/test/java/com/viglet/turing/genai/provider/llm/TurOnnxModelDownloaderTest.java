/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/**
 * Unit tests for {@link TurOnnxModelDownloader} (T656) exercising the resilient
 * acquisition logic against a loopback {@link HttpServer} — no external network:
 * pass-through of non-remote resources, 302-redirect following (the HuggingFace
 * Git-LFS fix), integrity gates (LFS-pointer stub / undersized {@code .onnx}),
 * and the persistent-cache hit that avoids a second download.
 */
class TurOnnxModelDownloaderTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger modelHits = new AtomicInteger();

    @TempDir
    Path cacheDir;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private TurOnnxModelDownloader downloader() {
        // maxAttempts=1, zero backoff → failure tests stay fast.
        return new TurOnnxModelDownloader(cacheDir.toString(), 1, 30, 0);
    }

    private void serve(String path, int status, byte[] body, String... headers) {
        server.createContext(path, exchange -> {
            for (int i = 0; i + 1 < headers.length; i += 2) {
                exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
            }
            exchange.sendResponseHeaders(status, status == 302 ? -1 : body.length);
            if (status != 302) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
            exchange.close();
        });
    }

    @Test
    void passesThroughNonRemoteResourcesUnchanged() {
        TurOnnxModelDownloader d = downloader();
        assertEquals("classpath:onnx/model.onnx", d.ensureLocal("classpath:onnx/model.onnx"));
        assertEquals("file:/opt/model.onnx", d.ensureLocal("file:/opt/model.onnx"));
        assertEquals("", d.ensureLocal(""));
    }

    @Test
    void downloadsTokenizerAndReturnsFileUriWithContent() throws Exception {
        byte[] body = "{\"tokenizer\":true}".getBytes(StandardCharsets.UTF_8);
        serve("/tokenizer.json", 200, body);

        String local = downloader().ensureLocal(base + "/tokenizer.json");

        assertTrue(local.startsWith("file:"), "expected a file: URI, got " + local);
        Path file = Path.of(URI.create(local));
        assertTrue(Files.exists(file));
        assertEquals(new String(body, StandardCharsets.UTF_8),
                Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void followsRedirectToCdn() throws Exception {
        // Mirrors the HuggingFace LFS flow: resolve/main/...onnx 302 → cdn path.
        byte[] onnx = new byte[8192]; // > MIN_ONNX_BYTES
        serve("/repo/model.onnx", 302, new byte[0], "Location", base + "/cdn/model.onnx");
        serve("/cdn/model.onnx", 200, onnx);

        String local = downloader().ensureLocal(base + "/repo/model.onnx");

        Path file = Path.of(URI.create(local));
        assertEquals(onnx.length, Files.size(file));
    }

    @Test
    void rejectsGitLfsPointerStubForOnnx() {
        byte[] pointer = ("version https://git-lfs.github.com/spec/v1\n"
                + "oid sha256:deadbeef\nsize 90000000\n").getBytes(StandardCharsets.UTF_8);
        serve("/repo/model.onnx", 200, pointer);

        TurOnnxModelDownloader d = downloader();
        String url = base + "/repo/model.onnx";
        assertThrows(IllegalStateException.class, () -> d.ensureLocal(url));
    }

    @Test
    void rejectsUndersizedOnnx() {
        serve("/repo/model.onnx", 200, new byte[128]); // < MIN_ONNX_BYTES, not a pointer

        TurOnnxModelDownloader d = downloader();
        String url = base + "/repo/model.onnx";
        assertThrows(IllegalStateException.class, () -> d.ensureLocal(url));
    }

    @Test
    void secondCallHitsPersistentCacheWithoutRedownloading() throws Exception {
        byte[] onnx = new byte[8192];
        server.createContext("/repo/model.onnx", exchange -> {
            modelHits.incrementAndGet();
            exchange.sendResponseHeaders(200, onnx.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(onnx);
            }
            exchange.close();
        });

        TurOnnxModelDownloader d = downloader();
        String url = base + "/repo/model.onnx";
        String first = d.ensureLocal(url);
        String second = d.ensureLocal(url);

        assertEquals(first, second);
        assertEquals(1, modelHits.get(), "second call must be served from the persistent cache");
    }

    @Test
    void cacheDirectoryFallsBackToStoreWhenBlank() {
        TurOnnxModelDownloader d = new TurOnnxModelDownloader("");
        assertTrue(d.cacheDirectory().contains(TurOnnxModelDownloader.CACHE_SUBDIR));
        // Blank config resolves under the project-wide store/ dir, not a temp dir.
        assertTrue(d.cacheDirectory().contains(TurOnnxModelDownloader.DEFAULT_STORE_DIR),
                "blank cache-dir must fall back under store/, got: " + d.cacheDirectory());
        assertNotEquals("", d.cacheDirectory());
    }
}
