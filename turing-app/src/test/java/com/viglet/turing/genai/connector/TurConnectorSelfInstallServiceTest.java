/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.viglet.turing.genai.connector.TurConnectorSelfInstallService.ConnectionTestRequest;
import com.viglet.turing.genai.connector.TurConnectorSelfInstallService.ConnectionTestResult;
import com.viglet.turing.genai.connector.TurConnectorSelfInstallService.ConnectorGuide;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseDriver;

/**
 * T190 / §X.15.d — unit tests for {@link TurConnectorSelfInstallService}: the
 * built-in catalogue, the guided-steps lookup, computer-use availability
 * reflection, and the fail-soft connection probe (success / auth-rejected /
 * unreachable / missing inputs) with the vendor's auth + extra headers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurConnectorSelfInstallServiceTest {

    @Mock
    private TurComputerUseDriver computerUseDriver;

    private MockRestServiceServer server;

    /** Permissive guard so the HTTP-probe tests stay hermetic (no real DNS). */
    private static final com.viglet.turing.spring.security.ssrf.TurSsrfGuard ALLOW_ALL_GUARD =
            new com.viglet.turing.spring.security.ssrf.TurSsrfGuard() {
                @Override
                public boolean isAllowedUrl(String urlString) {
                    return true;
                }
            };

    private TurConnectorSelfInstallService newService() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new TurConnectorSelfInstallService(computerUseDriver, ALLOW_ALL_GUARD, builder.build());
    }

    @Test
    void catalogExposesKnownConnectors() {
        var catalog = newService().catalog();
        assertThat(catalog).extracting(TurConnectorDescriptor::key)
                .contains("notion", "slack", "github", "google-drive", "confluence", "generic-mcp");
    }

    @Test
    void guideFoundForKnownConnectorReflectsComputerUseAvailability() {
        when(computerUseDriver.isAvailable()).thenReturn(false);
        ConnectorGuide guide = newService().guide("notion");
        assertThat(guide.found()).isTrue();
        assertThat(guide.connector().displayName()).isEqualTo("Notion");
        assertThat(guide.connector().setupSteps()).isNotEmpty();
        assertThat(guide.computerUseAvailable()).isFalse();
        assertThat(guide.error()).isNull();
    }

    @Test
    void guideNotFoundForUnknownConnector() {
        when(computerUseDriver.isAvailable()).thenReturn(true);
        ConnectorGuide guide = newService().guide("does-not-exist");
        assertThat(guide.found()).isFalse();
        assertThat(guide.connector()).isNull();
        assertThat(guide.computerUseAvailable()).isTrue(); // a real driver is deployed
        assertThat(guide.error()).contains("Unknown connector");
    }

    @Test
    void testConnectionSucceedsAndSendsAuthAndExtraHeaders() {
        TurConnectorSelfInstallService service = newService();
        // Notion: default endpoint + /users/me probe, Bearer auth, Notion-Version header.
        server.expect(requestTo("https://api.notion.com/v1/users/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer secret_abc"))
                .andExpect(header("Notion-Version", "2022-06-28"))
                .andRespond(withSuccess());

        ConnectionTestResult r = service.testConnection(
                new ConnectionTestRequest("notion", null, "secret_abc"));

        assertThat(r.success()).isTrue();
        assertThat(r.httpStatus()).isEqualTo(200);
        server.verify();
    }

    @Test
    void testConnectionReportsRejectedCredentials() {
        TurConnectorSelfInstallService service = newService();
        server.expect(requestTo("https://slack.com/api/auth.test"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ConnectionTestResult r = service.testConnection(
                new ConnectionTestRequest("slack", null, "xoxb-bad"));

        assertThat(r.success()).isFalse();
        assertThat(r.httpStatus()).isEqualTo(401);
        assertThat(r.message()).contains("rejected");
    }

    @Test
    void testConnectionRequiresToken() {
        ConnectionTestResult r = newService().testConnection(
                new ConnectionTestRequest("notion", null, "  "));
        assertThat(r.success()).isFalse();
        assertThat(r.httpStatus()).isZero();
        assertThat(r.message()).contains("token");
    }

    @Test
    void testConnectionRequiresEndpointWhenConnectorHasNoDefault() {
        // generic-mcp has no default endpoint and none supplied → bad input, no HTTP call.
        ConnectionTestResult r = newService().testConnection(
                new ConnectionTestRequest("generic-mcp", null, "tok"));
        assertThat(r.success()).isFalse();
        assertThat(r.httpStatus()).isZero();
        assertThat(r.message()).contains("endpoint");
    }

    @Test
    void testConnectionUsesSuppliedEndpointForGenericBearerProbe() {
        TurConnectorSelfInstallService service = newService();
        server.expect(requestTo("https://mcp.example.com/health"))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess());

        // Unknown key → generic bearer probe against the supplied endpoint + path.
        ConnectionTestResult r = service.testConnection(
                new ConnectionTestRequest("unknown", "https://mcp.example.com/health", "tok"));

        assertThat(r.success()).isTrue();
        server.verify();
    }

    @Test
    void testConnectionBlockedBySsrfGuard() {
        // A guard that blocks everything → no HTTP call, failed result (T643).
        var blockingGuard = new com.viglet.turing.spring.security.ssrf.TurSsrfGuard() {
            @Override
            public boolean isAllowedUrl(String urlString) {
                return false;
            }
        };
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer strictServer = MockRestServiceServer.bindTo(builder).build();
        var service = new TurConnectorSelfInstallService(computerUseDriver, blockingGuard, builder.build());

        ConnectionTestResult r = service.testConnection(
                new ConnectionTestRequest("unknown", "http://169.254.169.254/latest/meta-data/", "tok"));

        assertThat(r.success()).isFalse();
        assertThat(r.message()).contains("egress guard");
        strictServer.verify(); // no request was made
    }
}
