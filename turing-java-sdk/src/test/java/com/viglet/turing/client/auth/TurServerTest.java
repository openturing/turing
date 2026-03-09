package com.viglet.turing.client.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;

class TurServerTest {

    @Test
    void shouldInitializeDefaultProviderNameAndCredentials() {
        URI serverUrl = URI.create("http://localhost:2700");
        TurApiKeyCredentials credentials = new TurApiKeyCredentials("api-key-value");

        TurServer turServer = new TurServer(serverUrl, credentials);

        assertThat(turServer.getServerUrl()).isEqualTo(serverUrl);
        assertThat(turServer.getApiKey()).isEqualTo("api-key-value");
        assertThat(turServer.getProviderName()).isEqualTo("turing-java-sdk");
    }
}
