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

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.llm.TurVigletLlmProvider;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Unit tests for {@link TurVigletUpgradeTargetService} (T959 / §XIV.10.4) — the
 * signed upgrade target a quota refusal carries.
 *
 * <p>The point of every case here is the same: the link either carries a
 * signature the managed service can verify, or it does not exist. There is no
 * third state, because an unsigned target crossing into a self-hosted console is
 * an open redirect with a forgeable tenant claim.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurVigletUpgradeTargetServiceTest {

    private static final Pattern EXP = Pattern.compile("[?&]exp=(\\d+)");
    private static final Pattern SIG = Pattern.compile("[?&]sig=([0-9a-f]+)");

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;
    @Mock
    private TurSecretCryptoService turSecretCryptoService;
    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;

    private TurVigletAiProperty property;
    private TurVigletUpgradeTargetService service;

    @BeforeEach
    void setUp() {
        property = new TurVigletAiProperty();
        lenient().when(turGlobalSettingsService.getOrCreateVigletAiInstallId())
                .thenReturn("install-42");
        service = new TurVigletUpgradeTargetService(property, turGlobalSettingsService,
                turSecretCryptoService, turLLMInstanceRepository);
    }

    @Test
    void noTargetWhenTheUpgradeUrlIsUnset() {
        property.setUpgradeBaseUrl("");
        property.setUpgradeSigningSecret("s3cret");

        assertThat(service.upgradeUrl("t-1", "agents")).isEmpty();
    }

    @Test
    void noTargetWhenTheInstallSharesNoSecret() {
        when(turLLMInstanceRepository.findByVendorId(TurVigletLlmProvider.VENDOR_ID))
                .thenReturn(List.of());

        assertThat(service.upgradeUrl("t-1", "agents")).isEmpty();
    }

    @Test
    void configuredSecretSignsTheCanonicalPayload() {
        property.setUpgradeBaseUrl("https://viglet.cloud/upgrade/");
        property.setUpgradeSigningSecret("s3cret");

        String url = service.upgradeUrl("t-1", "monthlyTokens").orElseThrow();

        assertThat(url).startsWith("https://viglet.cloud/upgrade?")
                .contains("product=turing")
                .contains("install=install-42")
                .contains("tenant=t-1")
                .contains("limit=monthlyTokens");
        assertThat(signatureOf(url))
                .isEqualTo(hmacHex("s3cret",
                        "v1|turing|install-42|t-1|monthlyTokens|" + expiryOf(url)));
    }

    /**
     * The credential T957 paired with is a secret both halves already know, so it
     * is the signing key when no override is pinned — no second provisioning step
     * invented for a link.
     */
    @Test
    void thePairedCredentialSignsWhenNoSecretIsPinned() {
        TurLLMInstance managed = new TurLLMInstance();
        managed.setApiKeyEncrypted("enc(tok)");
        when(turLLMInstanceRepository.findByVendorId(TurVigletLlmProvider.VENDOR_ID))
                .thenReturn(List.of(managed));
        when(turSecretCryptoService.decrypt("enc(tok)")).thenReturn("paired-token");

        String url = service.upgradeUrl(null, "sites").orElseThrow();

        assertThat(url).contains("tenant=");
        assertThat(signatureOf(url))
                .isEqualTo(hmacHex("paired-token",
                        "v1|turing|install-42|" + "|sites|" + expiryOf(url)));
    }

    /** A short TTL is raised to the floor rather than minting an already-dead link. */
    @Test
    void expiryNeverLandsInThePast() {
        property.setUpgradeSigningSecret("s3cret");
        property.setUpgradeTtlSeconds(1);

        long exp = expiryOf(service.upgradeUrl("t-1", "agents").orElseThrow());

        assertThat(exp).isGreaterThanOrEqualTo(java.time.Instant.now().getEpochSecond() + 59);
    }

    private static Optional<String> group(Pattern pattern, String url) {
        Matcher matcher = pattern.matcher(url);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static long expiryOf(String url) {
        return Long.parseLong(group(EXP, url).orElseThrow());
    }

    private static String signatureOf(String url) {
        return group(SIG, url).orElseThrow();
    }

    private static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
