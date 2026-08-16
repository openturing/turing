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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.llm.TurVigletLlmProvider;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T959 / §XIV.10.4 — mints the <b>signed</b> upgrade target a quota refusal
 * carries.
 *
 * <p>Exhaustion is the one moment where the operator has already seen the value
 * and hit the wall at the same time, so it is where the onboarding credit is
 * supposed to convert. A landing page cannot reach that moment; the refusal can —
 * but only if it names who is asking, which install, and which limit was reached,
 * in a form the managed service can trust.
 *
 * <p><b>Signed, never plain.</b> The link crosses from the managed service into a
 * self-hosted console; unsigned, it is an open redirect with a friendly name and
 * a forgeable tenant claim. The signature is an HMAC-SHA256 over a canonical,
 * field-delimited payload:
 *
 * <pre>
 *   v1|turing|{installId}|{tenantId}|{resource}|{exp}
 * </pre>
 *
 * <p><b>The key is the shared secret with Viglet</b>, resolved in this order:
 *
 * <ol>
 *   <li>{@code turing.viglet-ai.upgrade-signing-secret}, when an operator (or a
 *       Viglet staging environment) pins one explicitly;</li>
 *   <li>otherwise the credential this install was paired with in T957 — the
 *       managed service issued it, so it is already a secret both halves know,
 *       and no second provisioning step is invented for a link.</li>
 * </ol>
 *
 * <p>When neither exists — the ordinary BYOK install that never paired — there is
 * nothing the other side could verify, so {@link #upgradeUrl(String, String)}
 * returns {@link Optional#empty()} and the refusal simply ships without the paid
 * exit. That is the honest outcome: the banner still says what ran out and still
 * offers the operator's own key, which is the exit that install already has.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurVigletUpgradeTargetService {

    private static final String PAYLOAD_PREFIX = "v1";
    private static final String PRODUCT = "turing";
    private static final String DELIM = "|";
    private static final String HMAC_ALG = "HmacSHA256";

    private final TurVigletAiProperty vigletAiProperty;
    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurLLMInstanceRepository turLLMInstanceRepository;

    public TurVigletUpgradeTargetService(TurVigletAiProperty vigletAiProperty,
            TurGlobalSettingsService turGlobalSettingsService,
            TurSecretCryptoService turSecretCryptoService,
            TurLLMInstanceRepository turLLMInstanceRepository) {
        this.vigletAiProperty = vigletAiProperty;
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.turSecretCryptoService = turSecretCryptoService;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
    }

    /**
     * The signed upgrade URL for a refusal, or empty when this install has no
     * secret the managed service could verify a signature with.
     *
     * @param tenantId the tenant that hit the limit ({@code null} on a
     *                 single-tenant install — signed as the empty claim)
     * @param resource which limit was reached ({@code agents}, {@code sites},
     *                 {@code monthlyTokens})
     */
    public Optional<String> upgradeUrl(String tenantId, String resource) {
        String baseUrl = vigletAiProperty.getUpgradeBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return Optional.empty();
        }
        String secret = resolveSigningSecret();
        if (!StringUtils.hasText(secret)) {
            log.debug("[VigletUpgradeTarget] No shared secret with Viglet AI — "
                    + "the quota refusal ships without a paid exit.");
            return Optional.empty();
        }
        String installId = turGlobalSettingsService.getOrCreateVigletAiInstallId();
        String tenant = tenantId == null ? "" : tenantId;
        long exp = Instant.now().getEpochSecond()
                + Math.max(60, vigletAiProperty.getUpgradeTtlSeconds());
        String signature = hmacHex(secret, payload(installId, tenant, resource, exp));

        return Optional.of(normalize(baseUrl)
                + "?product=" + encode(PRODUCT)
                + "&install=" + encode(installId)
                + "&tenant=" + encode(tenant)
                + "&limit=" + encode(resource)
                + "&exp=" + exp
                + "&sig=" + signature);
    }

    /**
     * The configured secret when pinned, otherwise the paired managed credential.
     * Returns {@code null} when this install has neither.
     */
    private String resolveSigningSecret() {
        String configured = vigletAiProperty.getUpgradeSigningSecret();
        if (StringUtils.hasText(configured)) {
            return configured.trim();
        }
        return pairedCredential();
    }

    /** The T957 credential, decrypted, or {@code null} on an unpaired install. */
    private String pairedCredential() {
        List<TurLLMInstance> managed = turLLMInstanceRepository
                .findByVendorId(TurVigletLlmProvider.VENDOR_ID);
        if (managed.isEmpty()) {
            return null;
        }
        String stored = managed.get(0).getApiKeyEncrypted();
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        String decrypted = turSecretCryptoService.decrypt(stored);
        return StringUtils.hasText(decrypted) ? decrypted : null;
    }

    private static String payload(String installId, String tenantId, String resource, long exp) {
        return PAYLOAD_PREFIX + DELIM + PRODUCT + DELIM + installId + DELIM + tenantId
                + DELIM + resource + DELIM + exp;
    }

    private static String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Signing the Viglet AI upgrade target failed", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalize(String baseUrl) {
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
