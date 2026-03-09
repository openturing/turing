package com.viglet.turing.client.auth.credentials;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurApiKeyCredentialsTest {

    @Test
    void shouldStoreAndExposeApiKey() {
        TurApiKeyCredentials credentials = new TurApiKeyCredentials("initial-key");

        assertThat(credentials.getApiKey()).isEqualTo("initial-key");

        credentials.setApiKey("updated-key");
        assertThat(credentials.getApiKey()).isEqualTo("updated-key");
    }

    @Test
    void shouldImplementCredentialsMarkerInterface() {
        TurApiKeyCredentials credentials = new TurApiKeyCredentials("key");

        assertThat(credentials).isInstanceOf(TurCredentials.class);
    }
}
