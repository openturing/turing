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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.viglet.turing.api.exception.TurProviderException;
import com.viglet.turing.genai.provider.llm.TurVigletLlmProvider;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T957 / §XIV.10.2 — the <b>client half</b> of device-code pairing with the
 * managed Viglet AI service (Block J.10).
 *
 * <p>A credential embedded in the distribution is public on the first day, and
 * one pasted by hand is the friction this block exists to remove. Device-code
 * pairing is the third option, and it is the one every CLI facing this problem
 * already uses: the install posts its id and version and gets back a short user
 * code plus a verification URL; the console shows both; the operator opens the
 * URL, signs in and approves; the install polls until the credential arrives.
 *
 * <p><b>Completion has to be whole</b>, because a half-paired install is worse
 * than an unpaired one. It therefore runs in one transaction ({@link
 * TransactionTemplate}, deliberately not {@code @Transactional} on the class —
 * the polling HTTP call must not hold a database connection): the credential is
 * encrypted through {@link TurSecretCryptoService}, the {@link TurLLMInstance}
 * on the {@code viglet} provider is created with the opaque alias, and
 * {@code DEFAULT_LLM} is set — after which chat, RAG, agents and semantic
 * navigation work with nothing else configured. An install that already has a
 * default keeps it: pairing adds an instance and does not take the operator's
 * choice away. Re-pairing refreshes the existing managed instance rather than
 * stacking a second one beside it.
 *
 * <p>T960 / §XIV.10.5 — the attempt also registers this install's <b>public
 * key</b>. The credential the flow ends with is bound to it: every later call to
 * the managed endpoint carries an assertion signed with the private half, which
 * never leaves the install, so a credential copied out of the database buys
 * nothing on its own (see {@link TurVigletInstallIdentityService}).
 *
 * <p><b>Wire contract</b> (RFC 8628 shaped; the server half lives in the
 * {@code viglet/cloud} repository):
 * <pre>
 * POST {pairingBaseUrl}/device/code
 *   {"install_id": "...", "install_version": "...", "product": "turing",
 *    "install_public_key": "&lt;base64 X.509&gt;", "install_key_alg": "Ed25519"}
 * → {"device_code", "user_code", "verification_uri",
 *    "verification_uri_complete"?, "interval"?, "expires_in"?}
 *
 * POST {pairingBaseUrl}/device/token
 *   {"install_id": "...", "device_code": "..."}
 * → 200 {"access_token": "..."}
 * → 4xx {"error": "authorization_pending"|"slow_down"|"access_denied"|"expired_token"}
 * </pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurVigletPairingService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private static final String PROVIDER = "viglet";
    private static final String PRODUCT = "turing";
    private static final String INSTALL_ID = "install_id";
    private static final String DEVICE_CODE = "device_code";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String INSTANCE_TITLE = "Viglet AI";
    private static final String INSTANCE_DESCRIPTION =
            "Managed Viglet AI endpoint, paired with this install. The endpoint and model are fixed.";

    /** RFC 8628 defaults, used when the service omits them. */
    private static final int DEFAULT_INTERVAL_SECONDS = 5;
    private static final int DEFAULT_EXPIRES_IN_SECONDS = 900;

    private final TurVigletAiProperty vigletAiProperty;
    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurLLMVendorRepository turLLMVendorRepository;
    private final TurVigletInstallIdentityService installIdentityService;
    private final TransactionTemplate transactionTemplate;

    public TurVigletPairingService(TurVigletAiProperty vigletAiProperty,
            TurGlobalSettingsService turGlobalSettingsService,
            TurSecretCryptoService turSecretCryptoService,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMVendorRepository turLLMVendorRepository,
            TurVigletInstallIdentityService installIdentityService,
            PlatformTransactionManager transactionManager) {
        this.vigletAiProperty = vigletAiProperty;
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.turSecretCryptoService = turSecretCryptoService;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turLLMVendorRepository = turLLMVendorRepository;
        this.installIdentityService = installIdentityService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Whether this install is already paired, and who it is to the managed service. */
    public TurVigletPairing.Status status() {
        String installId = turGlobalSettingsService.getOrCreateVigletAiInstallId();
        TurLLMInstance managed = findManagedInstance();
        if (managed == null) {
            return new TurVigletPairing.Status(false, null, false, installId);
        }
        boolean isDefault = managed.getId().equals(turGlobalSettingsService.getDefaultLlmId());
        return new TurVigletPairing.Status(true, managed.getId(), isDefault, installId);
    }

    /**
     * Starts an attempt: posts this install's id and version, and returns the
     * user code plus verification URL for the console to show.
     *
     * @throws TurProviderException when the pairing endpoint is unreachable or
     *         answers without a device code
     */
    public TurVigletPairing.Session start() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(INSTALL_ID, turGlobalSettingsService.getOrCreateVigletAiInstallId());
        payload.put("install_version", installVersion());
        payload.put("product", PRODUCT);
        // T960 — register the binding before a credential exists to bind. The
        // public half travels; the private half is minted here and stays here.
        payload.put("install_public_key", installIdentityService.getOrCreatePublicKey());
        payload.put("install_key_alg", TurVigletInstallIdentityService.KEY_ALGORITHM);

        Map<String, Object> body = post("/device/code", payload).getBody();
        String deviceCode = stringValue(body, DEVICE_CODE);
        String userCode = stringValue(body, "user_code");
        String verificationUri = stringValue(body, "verification_uri");
        if (!StringUtils.hasText(deviceCode) || !StringUtils.hasText(userCode)
                || !StringUtils.hasText(verificationUri)) {
            throw new TurProviderException(PROVIDER,
                    "Viglet AI pairing did not return a device code, user code and verification URL.");
        }
        return new TurVigletPairing.Session(deviceCode, userCode, verificationUri,
                stringValue(body, "verification_uri_complete"),
                intValue(body, "interval", DEFAULT_INTERVAL_SECONDS),
                intValue(body, "expires_in", DEFAULT_EXPIRES_IN_SECONDS));
    }

    /**
     * Polls one attempt. On approval the whole completion has already run by the
     * time this returns, so an {@code APPROVED} poll is a paired install.
     *
     * @param deviceCode the handle {@link #start()} returned
     * @throws IllegalArgumentException when {@code deviceCode} is blank
     * @throws TurProviderException     when the pairing endpoint is unreachable
     */
    public TurVigletPairing.Poll poll(String deviceCode) {
        if (!StringUtils.hasText(deviceCode)) {
            throw new IllegalArgumentException("deviceCode is required to poll a Viglet AI pairing.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(INSTALL_ID, turGlobalSettingsService.getOrCreateVigletAiInstallId());
        payload.put(DEVICE_CODE, deviceCode.trim());

        ResponseEntity<Map<String, Object>> response = post("/device/token", payload);
        Map<String, Object> body = response.getBody();
        String credential = stringValue(body, ACCESS_TOKEN);
        if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(credential)) {
            return complete(credential);
        }
        if (response.getStatusCode().is2xxSuccessful()) {
            throw new TurProviderException(PROVIDER,
                    "Viglet AI pairing approved the install but returned no credential.");
        }
        return TurVigletPairing.Poll.pending(mapError(stringValue(body, "error")));
    }

    /**
     * Stores the credential, creates (or refreshes) the managed instance and
     * registers it as the default LLM — all in one transaction, so the install is
     * never left half-paired.
     */
    TurVigletPairing.Poll complete(String credential) {
        TurVigletPairing.Poll result = transactionTemplate.execute(status -> {
            TurLLMVendor vendor = turLLMVendorRepository.findById(TurVigletLlmProvider.VENDOR_ID)
                    .orElse(null);
            if (vendor == null) {
                throw new TurProviderException(PROVIDER,
                        "Cannot complete Viglet AI pairing: the '" + TurVigletLlmProvider.VENDOR_ID
                                + "' vendor row is missing.");
            }
            TurLLMInstance instance = findManagedInstance();
            if (instance == null) {
                instance = new TurLLMInstance();
                instance.setTitle(INSTANCE_TITLE);
                instance.setDescription(INSTANCE_DESCRIPTION);
                instance.setTurLLMVendor(vendor);
                instance.setEnabled(1);
                instance.setToolsEnabled(true);
                // GLOBAL instance (tenantId left null) — usable by every tenant.
            }
            // The endpoint and the model are the provider's to pin; the alias is
            // stored anyway so the admin form and the model picker show the id the
            // managed service actually serves.
            instance.setModelName(vigletAiProperty.getModelAlias());
            instance.setApiKeyEncrypted(turSecretCryptoService.encrypt(credential.trim()));
            turLLMInstanceRepository.save(instance);

            // An install that already has a default keeps it: pairing adds an
            // instance, it does not take the operator's choice away.
            boolean defaultLlmSet = false;
            if (!StringUtils.hasText(turGlobalSettingsService.getDefaultLlmId())) {
                turGlobalSettingsService.updateDefaultLlmId(instance.getId());
                defaultLlmSet = true;
            }
            return new TurVigletPairing.Poll(TurVigletPairingState.APPROVED, instance.getId(), defaultLlmSet);
        });
        log.info("Viglet AI pairing completed: managed LLM instance '{}'{}.",
                result == null ? "?" : result.llmInstanceId(),
                result != null && result.defaultLlmSet() ? " (set as the global default LLM)" : "");
        return result;
    }

    /** The managed instance for this install, or {@code null} when unpaired. */
    private TurLLMInstance findManagedInstance() {
        List<TurLLMInstance> managed = turLLMInstanceRepository
                .findByVendorId(TurVigletLlmProvider.VENDOR_ID);
        return managed.isEmpty() ? null : managed.get(0);
    }

    /**
     * POSTs to the pairing endpoint, keeping 4xx bodies instead of throwing —
     * RFC 8628 reports {@code authorization_pending} and friends as a 400 with an
     * {@code error} field, which is a normal step of the flow, not a failure.
     *
     * <p>{@code protected} so a test can stub the one network hop and exercise the
     * flow's real branching (pending / slow down / expired / approved) without a
     * live managed service.
     */
    protected ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> payload) {
        String baseUrl = vigletAiProperty.getPairingBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new TurProviderException(PROVIDER,
                    "Missing turing.viglet-ai.pairing-base-url — cannot pair with the managed service.");
        }
        try {
            return RestClient.create(normalize(baseUrl)).post()
                    .uri(path)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        // Handled by the caller from the body.
                    })
                    .toEntity(MAP_TYPE);
        } catch (Exception e) {
            throw new TurProviderException(PROVIDER,
                    "Viglet AI pairing endpoint is unreachable: " + e.getMessage(), e);
        }
    }

    /** Maps an RFC 8628 token-endpoint error to a state; anything unknown is a denial. */
    private static TurVigletPairingState mapError(String error) {
        String normalized = error == null ? "" : error.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "authorization_pending" -> TurVigletPairingState.PENDING;
            case "slow_down" -> TurVigletPairingState.SLOW_DOWN;
            case "expired_token" -> TurVigletPairingState.EXPIRED;
            default -> TurVigletPairingState.DENIED;
        };
    }

    /** The running build's version, as the jar manifest reports it. */
    private String installVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    private static String stringValue(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Map<String, Object> body, String key, int fallback) {
        Object value = body == null ? null : body.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String normalize(String baseUrl) {
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
