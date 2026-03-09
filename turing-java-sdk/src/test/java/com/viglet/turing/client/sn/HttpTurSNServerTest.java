package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;

class HttpTurSNServerTest {

    @Test
    void shouldCreateServerUsingBasicConstructor() {
        HttpTurSNServer server = new HttpTurSNServer(URI.create("http://localhost:2700"), "SampleSite");

        assertThat(server.getSiteName()).isEqualTo("SampleSite");
        assertThat(server.getLocale()).isEqualTo(Locale.US);
        assertThat(server.getApiKey()).isNull();
    }

    @Test
    void shouldCreateServerWithApiKeyAndUserId() {
        TurApiKeyCredentials apiKeyCredentials = new TurApiKeyCredentials("api-key-1");

        HttpTurSNServer server = new HttpTurSNServer(
                URI.create("http://localhost:2700"),
                "MySite",
                Locale.CANADA,
                apiKeyCredentials,
                "user-42");

        assertThat(server.getSiteName()).isEqualTo("MySite");
        assertThat(server.getLocale()).isEqualTo(Locale.CANADA);
        assertThat(server.getApiKey()).isEqualTo("api-key-1");
        assertThat(server.getTurSNSitePostParams().getUserId()).isEqualTo("user-42");
        assertThat(server.getTurSNSitePostParams().isPopulateMetrics()).isTrue();
    }

    @Test
    void shouldCreateServerUsingOtherConstructors() {
        TurApiKeyCredentials credentials = new TurApiKeyCredentials("api-key-2");

        HttpTurSNServer localeConstructor = new HttpTurSNServer(
                URI.create("http://localhost:2700"),
                "MySite",
                Locale.FRANCE);
        HttpTurSNServer apiKeyConstructor = new HttpTurSNServer(
                URI.create("http://localhost:2700"),
                "MySite",
                Locale.UK,
                credentials);
        HttpTurSNServer shortApiKeyConstructor = new HttpTurSNServer(
                URI.create("http://localhost:2700"),
                "MySite",
                credentials);

        assertThat(localeConstructor.getLocale()).isEqualTo(Locale.FRANCE);
        assertThat(apiKeyConstructor.getApiKey()).isEqualTo("api-key-2");
        assertThat(shortApiKeyConstructor.getApiKey()).isEqualTo("api-key-2");
    }
}
