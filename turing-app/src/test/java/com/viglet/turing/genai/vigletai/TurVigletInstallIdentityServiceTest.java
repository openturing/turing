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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Unit tests for {@link TurVigletInstallIdentityService} (T960 / §XIV.10.5) —
 * the keypair that binds a managed credential to the install that earned it.
 *
 * <p>The assertions here are the two properties the binding rests on: the
 * signature verifies against the public half the managed service registered at
 * pairing, and an install with no key sends no headers at all rather than
 * something that looks like proof.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurVigletInstallIdentityServiceTest {

    @Mock
    private TurGlobalSettingsService settings;

    private TurVigletAiProperty property;
    private TurVigletInstallIdentityService service;

    /** Mirrors the config-var pair the real settings service persists. */
    private final AtomicReference<String> privateKey = new AtomicReference<>("");
    private final AtomicReference<String> publicKey = new AtomicReference<>("");

    @BeforeEach
    void setUp() {
        property = new TurVigletAiProperty();
        service = new TurVigletInstallIdentityService(settings, property);

        lenient().when(settings.getVigletAiInstallPrivateKey()).thenAnswer(i -> privateKey.get());
        lenient().when(settings.getVigletAiInstallPublicKey()).thenAnswer(i -> publicKey.get());
        lenient().when(settings.getOrCreateVigletAiInstallId()).thenReturn("install-uuid");
        lenient().doAnswer(invocation -> {
            privateKey.set(invocation.getArgument(0));
            publicKey.set(invocation.getArgument(1));
            return null;
        }).when(settings).updateVigletAiInstallKeyPair(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void theKeyPairIsMintedOnceAndReused() {
        String first = service.getOrCreatePublicKey();
        String second = service.getOrCreatePublicKey();

        assertThat(first).isNotBlank().isEqualTo(second);
        verify(settings, times(1)).updateVigletAiInstallKeyPair(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void anUnpairedInstallSendsNoHeaders() {
        assertThat(service.isBound()).isFalse();
        assertThat(service.assertionHeaders()).isEmpty();
    }

    @Test
    void theAssertionVerifiesAgainstTheRegisteredPublicKey() throws Exception {
        String registered = service.getOrCreatePublicKey();

        Map<String, String> headers = service.assertionHeaders();

        assertThat(headers).containsEntry(TurVigletInstallIdentityService.INSTALL_HEADER,
                "install-uuid");
        String assertion = headers.get(TurVigletInstallIdentityService.ASSERTION_HEADER);
        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(5);
        assertThat(parts[0]).isEqualTo("v1");
        assertThat(parts[1]).isEqualTo("install-uuid");

        long exp = Long.parseLong(parts[2]);
        assertThat(exp).isGreaterThan(java.time.Instant.now().getEpochSecond());

        assertThat(verifies(registered,
                TurVigletInstallIdentityService.payload("install-uuid", exp, parts[3]),
                parts[4])).isTrue();
    }

    /** Two calls must not be byte-identical, or a captured header replays freely. */
    @Test
    void everyAssertionCarriesAFreshNonce() {
        service.getOrCreatePublicKey();

        String first = service.assertionHeaders()
                .get(TurVigletInstallIdentityService.ASSERTION_HEADER);
        String second = service.assertionHeaders()
                .get(TurVigletInstallIdentityService.ASSERTION_HEADER);

        assertThat(first).isNotEqualTo(second);
    }

    /** A misconfigured TTL must not sign assertions that are already expired. */
    @Test
    void theExpiryIsFloored() {
        property.setAssertionTtlSeconds(0);
        service.getOrCreatePublicKey();

        String assertion = service.assertionHeaders()
                .get(TurVigletInstallIdentityService.ASSERTION_HEADER);
        long exp = Long.parseLong(assertion.split("\\.")[2]);

        assertThat(exp).isGreaterThanOrEqualTo(java.time.Instant.now().getEpochSecond() + 29);
    }

    private static boolean verifies(String publicKeyBase64, String payload, String signatureB64Url)
            throws Exception {
        PublicKey key = KeyFactory.getInstance(TurVigletInstallIdentityService.KEY_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
        Signature signature = Signature.getInstance(TurVigletInstallIdentityService.KEY_ALGORITHM);
        signature.initVerify(key);
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getUrlDecoder().decode(signatureB64Url));
    }
}
