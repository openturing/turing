/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.vigletai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.security.KeyPairGenerator;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T968 / §LXXVII — the wire Viglet Cloud has to implement, asserted from the
 * requests that actually leave.
 *
 * <p>Every other test of this flow stubs {@code post(...)} or mocks the identity
 * service, which proves the branching and proves nothing about the bytes. That
 * was survivable while both halves were ours; it stopped being survivable when
 * the server half (Viglet Cloud C125–C131) started being written from a
 * document. A field renamed here and a green build there is exactly the failure
 * this class exists to make impossible.
 *
 * <p>So the assertions are deliberately literal — the paths, the JSON keys, the
 * header names, the RFC 8628 error strings. <b>These are the fixtures the cloud
 * side should be handed</b>: if one of them has to change, it changes in both
 * repositories on the same day or the pairing breaks in front of the operator
 * the credit exists to win.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurVigletWireContractTest {

    private static final String PAIR_BASE = "https://llm.viglet.cloud/pair";

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;
    @Mock
    private TurSecretCryptoService turSecretCryptoService;
    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock
    private TurLLMVendorRepository turLLMVendorRepository;
    @Mock
    private TurVigletInstallIdentityService installIdentityService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private MockRestServiceServer server;
    private TurVigletPairingService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        TurVigletAiProperty property = new TurVigletAiProperty();
        property.setPairingBaseUrl(PAIR_BASE);
        service = new TurVigletPairingService(property, turGlobalSettingsService,
                turSecretCryptoService, turLLMInstanceRepository, turLLMVendorRepository,
                installIdentityService, transactionManager, builder);

        lenient().when(turGlobalSettingsService.getOrCreateVigletAiInstallId())
                .thenReturn("install-uuid");
        lenient().when(installIdentityService.getOrCreatePublicKey()).thenReturn("pub-key-b64");
    }

    /** The attempt: five fields, and the public key is one of them (T960). */
    @Test
    void deviceCodeRequestCarriesTheInstallAndItsPublicKey() {
        server.expect(requestTo(PAIR_BASE + "/device/code"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.install_id").value("install-uuid"))
                .andExpect(jsonPath("$.product").value("turing"))
                .andExpect(jsonPath("$.install_version").exists())
                .andExpect(jsonPath("$.install_public_key").value("pub-key-b64"))
                .andExpect(jsonPath("$.install_key_alg").value("Ed25519"))
                .andRespond(withSuccess("""
                        {"device_code":"dev-123","user_code":"ABCD-EFGH",
                         "verification_uri":"https://viglet.cloud/activate",
                         "interval":7,"expires_in":600}
                        """, MediaType.APPLICATION_JSON));

        TurVigletPairing.Session session = service.start();

        server.verify();
        assertThat(session.deviceCode()).isEqualTo("dev-123");
        assertThat(session.userCode()).isEqualTo("ABCD-EFGH");
        assertThat(session.intervalSeconds()).isEqualTo(7);
    }

    /** The poll: two fields, and the handle is the one `start` returned. */
    @Test
    void deviceTokenRequestCarriesTheInstallAndTheDeviceCode() {
        server.expect(requestTo(PAIR_BASE + "/device/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.install_id").value("install-uuid"))
                .andExpect(jsonPath("$.device_code").value("dev-123"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"authorization_pending\"}"));

        TurVigletPairing.Poll poll = service.poll("dev-123");

        server.verify();
        assertThat(poll.state()).isEqualTo(TurVigletPairingState.PENDING);
    }

    /**
     * The four RFC 8628 error strings, spelled as the managed service must spell
     * them. A typo on either side degrades silently into "denied", which reads to
     * the operator as a refusal they never made.
     */
    @Test
    void theErrorStringsMapToTheStatesTheConsoleShows() {
        assertThat(pollWithError("authorization_pending")).isEqualTo(TurVigletPairingState.PENDING);
        assertThat(pollWithError("slow_down")).isEqualTo(TurVigletPairingState.SLOW_DOWN);
        assertThat(pollWithError("expired_token")).isEqualTo(TurVigletPairingState.EXPIRED);
        assertThat(pollWithError("access_denied")).isEqualTo(TurVigletPairingState.DENIED);
    }

    /**
     * T965 — rotation is the one pairing call an already-bound install makes, so
     * it is the one that proves who it is: the headers travel, signed with the key
     * still in force, alongside the replacement it is asking to register.
     */
    @Test
    void rotateRequestCarriesTheAssertionAndTheReplacementKey() throws Exception {
        when(installIdentityService.isBound()).thenReturn(true);
        when(installIdentityService.mintReplacement())
                .thenReturn(KeyPairGenerator.getInstance("Ed25519").generateKeyPair());
        when(installIdentityService.assertionHeaders()).thenReturn(Map.of(
                TurVigletInstallIdentityService.INSTALL_HEADER, "install-uuid",
                TurVigletInstallIdentityService.ASSERTION_HEADER, "v1.install-uuid.99.nonce.sig"));

        server.expect(requestTo(PAIR_BASE + "/device/rotate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(TurVigletInstallIdentityService.INSTALL_HEADER, "install-uuid"))
                .andExpect(header(TurVigletInstallIdentityService.ASSERTION_HEADER,
                        "v1.install-uuid.99.nonce.sig"))
                .andExpect(jsonPath("$.install_id").value("install-uuid"))
                .andExpect(jsonPath("$.product").value("turing"))
                .andExpect(jsonPath("$.install_key_alg").value("Ed25519"))
                .andExpect(jsonPath("$.install_public_key").exists())
                .andRespond(withSuccess("{\"status\":\"accepted\"}", MediaType.APPLICATION_JSON));

        TurVigletPairing.Rotation rotation = service.rotateKey();

        server.verify();
        assertThat(rotation.rotated()).isTrue();
    }

    /**
     * The names the managed service parses on every `/v1` call. Asserted as
     * literals on purpose: renaming one is a one-character edit here and a total
     * authentication failure there, and nothing else in the build would notice.
     */
    @Test
    void theCallHeaderNamesAreTheOnesTheServiceParses() {
        assertThat(TurVigletInstallIdentityService.INSTALL_HEADER).isEqualTo("X-Viglet-Install");
        assertThat(TurVigletInstallIdentityService.ASSERTION_HEADER)
                .isEqualTo("X-Viglet-Install-Assertion");
        assertThat(TurVigletInstallIdentityService.KEY_ALGORITHM).isEqualTo("Ed25519");
    }

    /** The canonical payload both halves sign and verify, byte for byte. */
    @Test
    void theSignedPayloadIsTheOneTheServiceVerifies() {
        assertThat(TurVigletInstallIdentityService.payload("install-uuid", 1700000000L, "abc"))
                .isEqualTo("v1|turing|install-uuid|1700000000|abc");
    }

    private TurVigletPairingState pollWithError(String error) {
        server.reset();
        server.expect(requestTo(PAIR_BASE + "/device/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"" + error + "\"}"));
        return service.poll("dev-123").state();
    }
}
