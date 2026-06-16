/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.http;

import java.time.Duration;
import java.util.concurrent.Executors;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;

/**
 * Factory for the two outbound HTTP clients the Viglet products share: a pooled
 * Apache {@link CloseableHttpClient} (httpclient5) and a shared JDK
 * {@link java.net.http.HttpClient} backed by a bounded executor.
 *
 * <p>Consolidates the byte-for-byte duplicated wiring from Turing's
 * {@code TurHttpClientConfiguration} — the {@code proxyHttpClient} pool and the
 * {@code sharedJavaHttpClient} — and gives Dumont's {@code DumRestClientConfig}
 * a pooled request factory in place of the default-constructed one.
 *
 * <p>Pure builders, no Spring: register the returned clients as {@code @Bean}s
 * in the consuming app. The {@link CloseableHttpClient} owns a connection pool,
 * so the bean should be closed on context shutdown.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletHttpClients {

    private VigletHttpClients() {
    }

    /**
     * Builds a pooled httpclient5 {@link CloseableHttpClient} with the default
     * pool/timeout settings.
     */
    public static CloseableHttpClient pooledClient() {
        return pooledClient(VigletHttpClientSettings.defaults());
    }

    /**
     * Builds a pooled httpclient5 {@link CloseableHttpClient} from the given
     * pool/timeout settings.
     */
    public static CloseableHttpClient pooledClient(VigletHttpClientSettings settings) {
        return HttpClients.custom()
                .setConnectionManager(pooledConnectionManager(settings))
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(settings.responseTimeout()))
                        .build())
                .build();
    }

    /**
     * Builds the {@link PoolingHttpClientConnectionManager} used by
     * {@link #pooledClient(VigletHttpClientSettings)} — exposed separately so a
     * product can further customize the client before building it.
     */
    public static PoolingHttpClientConnectionManager pooledConnectionManager(VigletHttpClientSettings settings) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(settings.maxTotal());
        connectionManager.setDefaultMaxPerRoute(settings.maxPerRoute());
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(settings.connectTimeout()))
                .build());
        return connectionManager;
    }

    /**
     * Builds a shared JDK {@link java.net.http.HttpClient} backed by a fixed
     * thread pool — the bounded executor stops unbounded thread creation under
     * load (Turing's {@code sharedJavaHttpClient}).
     *
     * @param fixedThreads   size of the fixed executor pool (floored at 1)
     * @param connectTimeout TCP connect timeout (defaulted to 10s when null/non-positive)
     */
    public static java.net.http.HttpClient sharedJdkClient(int fixedThreads, Duration connectTimeout) {
        int threads = Math.max(1, fixedThreads);
        Duration connect = (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero())
                ? Duration.ofSeconds(10)
                : connectTimeout;
        return java.net.http.HttpClient.newBuilder()
                .executor(Executors.newFixedThreadPool(threads))
                .connectTimeout(connect)
                .build();
    }
}
