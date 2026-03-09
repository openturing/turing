package com.viglet.turing.client.auth.credentials;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurUsernamePasswordCredentialsTest {

    @Test
    void shouldStoreAndExposeUsernameAndPassword() {
        TurUsernamePasswordCredentials credentials = new TurUsernamePasswordCredentials("user", "pass");

        assertThat(credentials.getUsername()).isEqualTo("user");
        assertThat(credentials.getPassword()).isEqualTo("pass");

        credentials.setUsername("user2");
        credentials.setPassword("pass2");

        assertThat(credentials.getUsername()).isEqualTo("user2");
        assertThat(credentials.getPassword()).isEqualTo("pass2");
    }

    @Test
    void shouldImplementCredentialsMarkerInterface() {
        TurUsernamePasswordCredentials credentials = new TurUsernamePasswordCredentials("u", "p");

        assertThat(credentials).isInstanceOf(TurCredentials.class);
    }
}
