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

/**
 * Connection-pool sizing and timeouts for a pooled
 * {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient} built by
 * {@link VigletHttpClients}.
 *
 * <p>The defaults mirror the values the products hand-tuned identically
 * (Turing's {@code proxyHttpClient}): 100 total connections, 20 per route, a 5s
 * TCP connect timeout and a 30s response timeout.
 *
 * @param maxTotal        max total pooled connections (floored at 1)
 * @param maxPerRoute     max connections per route (floored at 1)
 * @param connectTimeout  TCP connect timeout
 * @param responseTimeout per-request response (read) timeout
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record VigletHttpClientSettings(int maxTotal, int maxPerRoute, Duration connectTimeout,
        Duration responseTimeout) {

    public VigletHttpClientSettings {
        if (maxTotal < 1) {
            maxTotal = 1;
        }
        if (maxPerRoute < 1) {
            maxPerRoute = 1;
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (responseTimeout == null || responseTimeout.isNegative() || responseTimeout.isZero()) {
            responseTimeout = Duration.ofSeconds(30);
        }
    }

    /** Sensible defaults: 100 total / 20 per route, 5s connect, 30s response. */
    public static VigletHttpClientSettings defaults() {
        return new VigletHttpClientSettings(100, 20, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }
}
