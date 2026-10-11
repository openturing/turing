/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.client.sn;

import java.time.Duration;
import java.util.Objects;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

/**
 * T1081 — how long a {@link TurSNServer} call may take to connect and to read,
 * applied to every request it makes (search, manifest, import, inventory).
 *
 * <p>{@link #DEFAULTS}: connect within 10 seconds, which an unreachable host
 * fails fast on; read for up to 3 minutes, the wait an HTTP client without a
 * timeout already had, so a long import keeps working. A batch caller that would
 * rather give up sooner sets its own with {@link TurSNServer#withTimeouts}.
 *
 * @param connect time allowed to open the connection
 * @param read    time allowed between bytes of the response
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNTimeouts(Duration connect, Duration read) {

    public static final TurSNTimeouts DEFAULTS =
            new TurSNTimeouts(Duration.ofSeconds(10), Duration.ofMinutes(3));

    public TurSNTimeouts {
        requirePositive(connect, "connect");
        requirePositive(read, "read");
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " timeout must be positive: " + value);
        }
    }

    /** A new HTTP client with the server's timeouts, or {@link #DEFAULTS} when it has none. */
    public static CloseableHttpClient httpClientFor(TurSNServer server) {
        TurSNTimeouts timeouts = server == null ? null : server.getTimeouts();
        return (timeouts == null ? DEFAULTS : timeouts).newHttpClient();
    }

    /** A new HTTP client that applies these timeouts; the caller closes it. */
    public CloseableHttpClient newHttpClient() {
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connect))
                                .setSocketTimeout(Timeout.of(read))
                                .build())
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(read))
                        .build())
                .build();
    }
}
