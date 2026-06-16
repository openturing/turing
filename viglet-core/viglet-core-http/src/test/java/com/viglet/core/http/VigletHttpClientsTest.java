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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;

class VigletHttpClientsTest {

    @Test
    void pooledClientWithDefaultsIsBuilt() throws Exception {
        try (CloseableHttpClient client = VigletHttpClients.pooledClient()) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void pooledConnectionManagerHonoursSettings() {
        VigletHttpClientSettings settings = new VigletHttpClientSettings(50, 7, Duration.ofSeconds(2),
                Duration.ofSeconds(8));

        PoolingHttpClientConnectionManager manager = VigletHttpClients.pooledConnectionManager(settings);

        assertThat(manager.getMaxTotal()).isEqualTo(50);
        assertThat(manager.getDefaultMaxPerRoute()).isEqualTo(7);
    }

    @Test
    void sharedJdkClientCarriesConnectTimeout() {
        java.net.http.HttpClient client = VigletHttpClients.sharedJdkClient(20, Duration.ofSeconds(10));

        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(10));
    }

    @Test
    void sharedJdkClientFloorsThreadsAndDefaultsTimeout() {
        java.net.http.HttpClient client = VigletHttpClients.sharedJdkClient(0, null);

        assertThat(client.connectTimeout()).contains(Duration.ofSeconds(10));
    }
}
