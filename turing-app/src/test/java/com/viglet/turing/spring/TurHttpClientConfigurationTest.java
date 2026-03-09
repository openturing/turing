package com.viglet.turing.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.http.HttpClient;
import java.time.Duration;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;

class TurHttpClientConfigurationTest {

    private final TurHttpClientConfiguration config = new TurHttpClientConfiguration();

    @Test
    void shouldCreateSharedJavaHttpClientWithConnectTimeoutConfigured() {
        HttpClient httpClient = config.sharedJavaHttpClient();

        assertNotNull(httpClient);
        assertEquals(Duration.ofSeconds(10), httpClient.connectTimeout().orElseThrow());
    }

    @Test
    void shouldCreateProxyHttpClientInstance() {
        CloseableHttpClient proxyClient = config.proxyHttpClient();

        assertNotNull(proxyClient);
    }
}
