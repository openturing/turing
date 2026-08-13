/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.service.storage.TurStorageKeys;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T951 / §XXXVII.19 — an encoded traversal must not reach the storage key.
 *
 * <p>The chain this pins, measured rather than assumed: {@code StrictHttpFirewall}
 * rejects {@code ../..}, {@code %2e%2e} and {@code %252e%252e}, and <em>passes</em>
 * {@code ..%2f..%2fsecret}, because Turing enables
 * {@code setAllowUrlEncodedSlash(true)} and the firewall only rejects {@code .}
 * and {@code ..} as whole segments. What passed then reached
 * {@code serveFromStorage}, which decoded it and concatenated it into an object
 * name — leaving the only real defence in {@code viglet-core-storage}, a separate
 * artifact, and one the MinIO backend has no equivalent of.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSearchControllerTraversalTest {

    private static final String SITE = "site";
    private static final String TEMPLATE = "storefront";

    @Mock
    private TurSNSiteRepositoryPort turSNSiteRepositoryPort;
    @Mock
    private TurStorageService storageService;

    private TurSNSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new TurSNSearchController(turSNSiteRepositoryPort, storageService);
        when(storageService.isEnabled()).thenReturn(true);
        TurSNSiteDomain site = org.mockito.Mockito.mock(TurSNSiteDomain.class);
        when(site.hasSearchTemplate()).thenReturn(true);
        when(site.searchTemplate()).thenReturn(TEMPLATE);
        when(turSNSiteRepositoryPort.findByName(SITE)).thenReturn(Optional.of(site));
        when(storageService.guessContentType(anyString())).thenReturn("text/plain");
        when(storageService.downloadObject(anyString()))
                .thenReturn(new ByteArrayInputStream("ok".getBytes()));
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    // ---- the firewall, measured -------------------------------------------

    /** Exactly how {@code allowUrlEncodedSlaturHttpFirewall()} configures it. */
    private static StrictHttpFirewall productionFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }

    private static boolean passesFirewall(String uri) {
        try {
            productionFirewall().getFirewalledRequest(request(uri));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/sn/site/../../../etc/passwd",
            "/sn/site/%2e%2e/%2e%2e/secret",
            "/sn/site/%252e%252e/secret",
    })
    void theFirewallStopsTheObviousShapes(String uri) {
        assertThat(passesFirewall(uri)).isFalse();
    }

    /**
     * The one it does not stop, and the reason this task exists. If this ever
     * starts failing, the encoded-slash allowance changed — which would be good
     * news, and would still not make the controller's own check redundant.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/sn/site/..%2f..%2fsecret",
            "/sn/site/a%2f..%2f..%2fsecret",
    })
    void theFirewallLetsEncodedSlashTraversalThrough(String uri) {
        assertThat(passesFirewall(uri))
                .as("setAllowUrlEncodedSlash(true) admits %2f and the firewall only "
                        + "rejects '.'/'..' as whole segments")
                .isTrue();
    }

    // ---- the controller's own check ---------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "..%2f..%2fsecret",
            "a%2f..%2f..%2fsecret",
            "..%2fsecret",
            "%2e%2e%2fsecret",
    })
    void rejectsAnEncodedTraversalBeforeItBecomesAnObjectName(String encodedPath) {
        var response = controller.serveSubPath(SITE, request("/sn/" + SITE + "/" + encodedPath));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(storageService, never()).downloadObject(anyString());
    }

    @Test
    void servesAnOrdinaryAssetUnderTheTemplatePrefix() {
        var response = controller.serveSubPath(SITE, request("/sn/" + SITE + "/assets/app.js"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).downloadObject(key.capture());
        assertThat(key.getValue()).isEqualTo("public/" + TEMPLATE + "/assets/app.js");
    }

    @Test
    void stillDecodesAnOrdinaryEncodedCharacter() {
        // The decode is why the check is needed; it must not be what the check removes.
        var response = controller.serveSubPath(SITE,
                request("/sn/" + SITE + "/assets/my%20file.css"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).downloadObject(key.capture());
        assertThat(key.getValue()).isEqualTo("public/" + TEMPLATE + "/assets/my file.css");
    }

    @Test
    void aMissingAssetStillFallsBackToTheSpaIndex() {
        // The rejection must not be folded into the SPA fallback: a real 404 keeps
        // serving index.html for client-side routing, a traversal attempt does not.
        when(storageService.downloadObject("public/" + TEMPLATE + "/deep/route"))
                .thenThrow(new IllegalStateException("not found"));
        when(storageService.downloadObject("public/" + TEMPLATE + "/index.html"))
                .thenReturn(new ByteArrayInputStream("<html/>".getBytes()));

        var response = controller.serveSubPath(SITE, request("/sn/" + SITE + "/deep/route"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    // ---- the rule itself ---------------------------------------------------

    @Test
    void theSharedRuleRejectsEveryEscapeSpelling() {
        assertThatThrownBy(() -> TurStorageKeys.safeRelative("../secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurStorageKeys.safeRelative("a/../../secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurStorageKeys.safeRelative("..\\secret"))
                .as("a backslash is a separator on a Windows host")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurStorageKeys.safeRelative("/absolute"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurStorageKeys.safeRelative("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSharedRuleKeepsAnOrdinaryKeyIntact() {
        assertThat(TurStorageKeys.safeRelative("assets/app.js")).isEqualTo("assets/app.js");
        assertThat(TurStorageKeys.safeRelative("./assets//app.js")).isEqualTo("assets/app.js");
        assertThat(TurStorageKeys.safeRelative("index.html")).isEqualTo("index.html");
    }

}
