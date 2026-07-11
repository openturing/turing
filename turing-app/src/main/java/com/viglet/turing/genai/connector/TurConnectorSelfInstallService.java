/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.connector;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseDriver;

import lombok.extern.slf4j.Slf4j;

/**
 * T190 / §X.15.d — "self-installing" enterprise connectors. When a customer says
 * "connect my Notion", this service drives the setup: it produces the vendor's
 * token-generation steps, then <b>tests the credential</b> with a lightweight
 * authenticated probe before the connector is saved/enabled through the existing
 * integration / MCP-server CRUD.
 *
 * <p><b>The computer_use leg (T136).</b> When a real {@link TurComputerUseDriver}
 * is deployed ({@link TurComputerUseDriver#isAvailable()}), a computer-use agent
 * can walk the very steps in {@link TurConnectorDescriptor#setupSteps()} inside a
 * browser session and read back the generated token. With the shipped no-op
 * driver, {@link #computerUseAvailable()} is {@code false} and the flow degrades
 * to guided instructions + the connection probe — the same "ship the scaffold
 * against the no-op driver" pattern T136/T494 use, so no browser is bundled.
 *
 * <p>The connection test is strictly fail-soft and time-bounded: any
 * non-2xx/exception becomes a {@link ConnectionTestResult} the caller surfaces,
 * never a thrown error.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurConnectorSelfInstallService {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(8);

    /** The shipped catalogue of well-known connectors a customer can self-install. */
    private static final List<TurConnectorDescriptor> CATALOG = List.of(
            new TurConnectorDescriptor("notion", "Notion", "MCP", "BEARER",
                    "https://api.notion.com/v1", "/users/me",
                    "Authorization", "Bearer ", Map.of("Notion-Version", "2022-06-28"),
                    List.of(
                            "Open notion.so and sign in.",
                            "Go to Settings → Connections → Develop or manage integrations (notion.so/my-integrations).",
                            "Click 'New integration', name it 'Turing', pick the workspace.",
                            "Copy the 'Internal Integration Secret' (starts with 'secret_' / 'ntn_').",
                            "Share the pages/databases Turing should read with the integration."),
                    "https://developers.notion.com/docs/create-a-notion-integration"),
            new TurConnectorDescriptor("slack", "Slack", "MCP", "BEARER",
                    "https://slack.com/api", "/auth.test",
                    "Authorization", "Bearer ", Map.of(),
                    List.of(
                            "Open api.slack.com/apps and create (or pick) an app for your workspace.",
                            "Under 'OAuth & Permissions', add the scopes Turing needs (e.g. search:read, channels:history).",
                            "Install the app to the workspace.",
                            "Copy the 'Bot User OAuth Token' (starts with 'xoxb-')."),
                    "https://api.slack.com/authentication/token-types"),
            new TurConnectorDescriptor("github", "GitHub", "MCP", "BEARER",
                    "https://api.github.com", "/user",
                    "Authorization", "Bearer ", Map.of("Accept", "application/vnd.github+json"),
                    List.of(
                            "Open github.com → Settings → Developer settings → Personal access tokens → Fine-grained tokens.",
                            "Click 'Generate new token', set an expiry and the repositories Turing may read.",
                            "Grant read-only Contents / Metadata (and Issues/PRs if needed).",
                            "Copy the token (starts with 'github_pat_')."),
                    "https://docs.github.com/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens"),
            new TurConnectorDescriptor("google-drive", "Google Drive", "MCP", "OAUTH",
                    "https://www.googleapis.com/drive/v3", "/about?fields=user",
                    "Authorization", "Bearer ", Map.of(),
                    List.of(
                            "Open console.cloud.google.com and select or create a project.",
                            "Enable the 'Google Drive API' under APIs & Services.",
                            "Create OAuth credentials and complete the consent screen.",
                            "Authorize the read-only Drive scope and obtain an access token."),
                    "https://developers.google.com/drive/api/guides/about-auth"),
            new TurConnectorDescriptor("confluence", "Confluence", "INTEGRATION", "BEARER",
                    null, "/rest/api/space?limit=1",
                    "Authorization", "Bearer ", Map.of(),
                    List.of(
                            "Open your Confluence/Atlassian account security settings.",
                            "Create an API token at id.atlassian.com/manage-profile/security/api-tokens.",
                            "Copy the token and note your Confluence base URL (e.g. https://your-org.atlassian.net/wiki).",
                            "Supply that base URL as the endpoint when testing the connection."),
                    "https://support.atlassian.com/atlassian-account/docs/manage-api-tokens-for-your-atlassian-account/"),
            new TurConnectorDescriptor("generic-mcp", "Generic MCP server", "MCP", "BEARER",
                    null, "",
                    "Authorization", "Bearer ", Map.of(),
                    List.of(
                            "Obtain the remote MCP server's HTTPS endpoint URL.",
                            "Obtain a bearer token from that server's owner, if it requires auth.",
                            "Supply the endpoint and token to test the connection."),
                    "https://modelcontextprotocol.io/"));

    private final TurComputerUseDriver computerUseDriver;
    private final RestClient restClient;
    private final com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard;

    @Autowired
    public TurConnectorSelfInstallService(TurComputerUseDriver computerUseDriver,
            com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard) {
        this(computerUseDriver, ssrfGuard, defaultClient());
    }

    /** Test seam: lets a unit test bind a {@code MockRestServiceServer} to the client. */
    TurConnectorSelfInstallService(TurComputerUseDriver computerUseDriver, RestClient restClient) {
        this(computerUseDriver, new com.viglet.turing.spring.security.ssrf.TurSsrfGuard(), restClient);
    }

    TurConnectorSelfInstallService(TurComputerUseDriver computerUseDriver,
            com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard, RestClient restClient) {
        this.computerUseDriver = computerUseDriver;
        this.ssrfGuard = ssrfGuard;
        this.restClient = restClient;
    }

    private static RestClient defaultClient() {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(PROBE_TIMEOUT);
        rf.setReadTimeout(PROBE_TIMEOUT);
        return RestClient.builder().requestFactory(rf).build();
    }

    /** The shipped connector catalogue. */
    public List<TurConnectorDescriptor> catalog() {
        return CATALOG;
    }

    /** Find a catalogue entry by its key (case-insensitive). */
    public Optional<TurConnectorDescriptor> find(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        return CATALOG.stream().filter(d -> d.key().equalsIgnoreCase(key.trim())).findFirst();
    }

    /**
     * Whether a real computer-use driver is deployed (so the setup steps could be
     * driven automatically in a browser). {@code false} with the no-op default →
     * the caller uses guided instructions.
     */
    public boolean computerUseAvailable() {
        try {
            return computerUseDriver.isAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The guided-install payload for a connector: its descriptor, the
     * token-generation steps, and whether automated (computer-use) install is
     * possible on this deployment.
     */
    public ConnectorGuide guide(String key) {
        TurConnectorDescriptor descriptor = find(key).orElse(null);
        if (descriptor == null) {
            return new ConnectorGuide(false, null, computerUseAvailable(),
                    "Unknown connector '" + key + "'. Call the catalog for supported keys.");
        }
        return new ConnectorGuide(true, descriptor, computerUseAvailable(), null);
    }

    /**
     * Test a credential with a lightweight authenticated GET. Resolves the
     * descriptor (when {@code key} is known) for the probe path + auth shape, or
     * treats the request as a generic bearer probe against the supplied endpoint.
     * Fail-soft: any failure becomes an unsuccessful result, never an exception.
     */
    public ConnectionTestResult testConnection(ConnectionTestRequest request) {
        if (request == null || !StringUtils.hasText(request.token())) {
            return new ConnectionTestResult(false, 0, "A token is required to test the connection.");
        }
        TurConnectorDescriptor descriptor = find(request.key()).orElse(null);
        String endpoint = StringUtils.hasText(request.endpoint())
                ? request.endpoint().trim()
                : (descriptor != null ? descriptor.defaultEndpoint() : null);
        if (!StringUtils.hasText(endpoint)) {
            return new ConnectionTestResult(false, 0,
                    "An endpoint is required for this connector — supply the base URL.");
        }

        String headerName = descriptor != null ? descriptor.authHeaderName() : "Authorization";
        String headerPrefix = descriptor != null ? descriptor.authHeaderPrefix() : "Bearer ";
        String testPath = descriptor != null && descriptor.testPath() != null
                ? descriptor.testPath() : "";
        Map<String, String> extra = descriptor != null && descriptor.extraHeaders() != null
                ? descriptor.extraHeaders() : Map.of();
        String url = trimTrailingSlash(endpoint) + testPath;

        // T643 / §XXXVII.5 — this authenticated probe is a response-observable
        // SSRF oracle (status/message are returned to the caller and a bearer
        // token is forwarded to the chosen host). Gate the caller-supplied URL
        // through the central egress guard before connecting.
        if (!ssrfGuard.isAllowedUrl(url)) {
            log.warn("[Connector] connection test blocked by SSRF guard: {}", url);
            return new ConnectionTestResult(false, 0,
                    "The endpoint is not an allowed target (blocked by the egress guard).");
        }

        try {
            var spec = restClient.get().uri(url)
                    .header(headerName, headerPrefix + request.token());
            extra.forEach(spec::header);
            var entity = spec.retrieve().toBodilessEntity();
            int status = entity.getStatusCode().value();
            return new ConnectionTestResult(true, status, "Connection succeeded.");
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String message = (status == 401 || status == 403)
                    ? "Credentials were rejected by the connector (" + status + ")."
                    : "The connector returned HTTP " + status + ".";
            return new ConnectionTestResult(false, status, message);
        } catch (RuntimeException e) {
            log.debug("[Connector] connection test to {} failed: {}", url, e.getMessage());
            return new ConnectionTestResult(false, 0,
                    "Could not reach the connector: " + e.getMessage());
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** A request to test one credential against a connector. */
    public record ConnectionTestRequest(String key, String endpoint, String token) {
    }

    /** The outcome of a connection test. {@code httpStatus} is 0 when no response was received. */
    public record ConnectionTestResult(boolean success, int httpStatus, String message) {
    }

    /** The guided-install payload for a connector. */
    public record ConnectorGuide(boolean found, TurConnectorDescriptor connector,
            boolean computerUseAvailable, String error) {
    }
}
