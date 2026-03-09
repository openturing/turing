package com.viglet.turing.client.auth.credentials;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurCredentialsTest {

    @Test
    void shouldBeImplementedBySdkCredentialTypes() {
        assertThat(TurCredentials.class.isAssignableFrom(TurApiKeyCredentials.class)).isTrue();
        assertThat(TurCredentials.class.isAssignableFrom(TurUsernamePasswordCredentials.class)).isTrue();
    }
}
