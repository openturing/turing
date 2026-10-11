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
package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.manifest.TurSNManifest;
import com.viglet.turing.client.sn.manifest.TurSNManifestException;

/**
 * T1081 — a call to a Turing that accepts the connection and never answers (a
 * paused container) fails after the read timeout the caller set, not after
 * minutes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNTimeoutsTest {

    private ServerSocket silent;
    private final List<Socket> held = new ArrayList<>();

    @BeforeEach
    void startSilentServer() throws IOException {
        silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(() -> {
            try {
                while (!silent.isClosed()) {
                    held.add(silent.accept()); // accept, read nothing, answer nothing
                }
            } catch (IOException closed) {
                // server stopped
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopSilentServer() throws IOException {
        silent.close();
        for (Socket socket : held) {
            socket.close();
        }
    }

    private TurSNServer server() {
        return new TurSNServer(URI.create("http://127.0.0.1:" + silent.getLocalPort()), "Catalog",
                Locale.US, new TurApiKeyCredentials("key"));
    }

    @Test
    void aSilentTuringFailsTheManifestCallAfterTheReadTimeout() {
        TurSNServer turSNServer = server().withTimeouts(Duration.ofSeconds(2), Duration.ofMillis(300));
        TurSNManifest manifest = TurSNManifest.builder().name("Catalog").locale("en-US").build();

        long start = System.nanoTime();
        assertThatThrownBy(() -> turSNServer.provision(manifest))
                .isInstanceOf(TurSNManifestException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void defaultsConnectFastAndKeepTheLongRead() {
        TurSNTimeouts defaults = server().getTimeouts();

        assertThat(defaults).isEqualTo(TurSNTimeouts.DEFAULTS);
        assertThat(defaults.connect()).isEqualTo(Duration.ofSeconds(10));
        assertThat(defaults.read()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void aTimeoutMustBePositive() {
        TurSNServer turSNServer = server();
        assertThatThrownBy(() -> turSNServer.withTimeouts(Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connect");
    }

    @Test
    void aServerWithoutTimeoutsStillGetsAClient() throws IOException {
        TurSNServer turSNServer = server();
        turSNServer.setTimeouts(null);
        try (var client = TurSNTimeouts.httpClientFor(turSNServer)) {
            assertThat(client).isNotNull();
        }
    }
}
