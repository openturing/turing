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

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T960 / §XIV.10.5 — who this install is to the managed service, cryptographically.
 *
 * <p><b>Why binding, and not just a credential.</b> A bearer token that works
 * from anywhere is an inference API. The moment the credential can be lifted out
 * of the install and used with {@code curl}, the offering stops being "Turing
 * comes with AI included" and becomes a free tier of a hosted model service — a
 * market with incumbents, no moat and negative margin, and the one this block
 * exists not to enter.
 *
 * <p>So every call to the managed endpoint carries two headers beside the
 * credential: the install id from pairing, and a short-lived assertion signed
 * with an <b>Ed25519 key generated at pairing time that never leaves this
 * install</b>. The managed side holds only the public half, registered during
 * pairing, and refuses a credential presented without a matching assertion — so
 * a leaked key alone buys nothing.
 *
 * <p>The same binding is the abuse control. A monthly token cap does not stop
 * farming, because a thousand installs is a thousand caps; one live binding per
 * credit is what makes the cap mean something. And it makes revocation possible
 * at the granularity that matters — an install, not an account, so one abusive
 * deployment does not cost a legitimate operator their other environments.
 *
 * <p><b>Assertion wire format</b> (the server half lives in {@code viglet/cloud}):
 * <pre>
 *   X-Viglet-Install:           {installId}
 *   X-Viglet-Install-Assertion: v1.{installId}.{exp}.{nonce}.{base64url-signature}
 *   signed payload:             v1|turing|{installId}|{exp}|{nonce}
 * </pre>
 * The nonce is there so two calls in the same second are not byte-identical
 * (replay is bounded by {@code exp}, which is short by default), and the payload
 * is field-delimited because none of the fields can contain {@code |}.
 *
 * <p>None of this touches the BYOK path: only the {@code viglet} provider asks
 * for these headers, and an unpaired install has no key to sign with, so
 * {@link #assertionHeaders()} simply answers empty.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurVigletInstallIdentityService {

    /** Header carrying the install id, so the managed side knows whose key to check. */
    public static final String INSTALL_HEADER = "X-Viglet-Install";
    /** Header carrying the signed, short-lived proof that the call comes from that install. */
    public static final String ASSERTION_HEADER = "X-Viglet-Install-Assertion";
    /** The signing algorithm, named on the wire so the pair can be rotated later. */
    public static final String KEY_ALGORITHM = "Ed25519";

    private static final String ASSERTION_VERSION = "v1";
    private static final String PRODUCT = "turing";
    private static final String DELIM = "|";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurVigletAiProperty vigletAiProperty;

    public TurVigletInstallIdentityService(TurGlobalSettingsService turGlobalSettingsService,
            TurVigletAiProperty vigletAiProperty) {
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.vigletAiProperty = vigletAiProperty;
    }

    /**
     * The install's public key (X.509, Base64), minting the pair on first call.
     * Called at pairing time so the managed service can register the binding
     * before it issues a credential.
     */
    public synchronized String getOrCreatePublicKey() {
        String existing = turGlobalSettingsService.getVigletAiInstallPublicKey();
        if (StringUtils.hasText(existing)
                && StringUtils.hasText(turGlobalSettingsService.getVigletAiInstallPrivateKey())) {
            return existing;
        }
        KeyPair pair = generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        turGlobalSettingsService.updateVigletAiInstallKeyPair(
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()), publicKey);
        log.info("Minted the Viglet AI install signing key ({}) — the private half stays here.",
                KEY_ALGORITHM);
        return publicKey;
    }

    /** Whether this install can sign an assertion (i.e. it has paired). */
    public boolean isBound() {
        return StringUtils.hasText(turGlobalSettingsService.getVigletAiInstallPrivateKey());
    }

    /**
     * The headers a managed call carries, or an empty map when this install has
     * no key — an unpaired install must still be able to reach the endpoint and
     * be refused by it, rather than failing here with an exception the operator
     * cannot act on.
     */
    public Map<String, String> assertionHeaders() {
        String privateKeyBase64 = turGlobalSettingsService.getVigletAiInstallPrivateKey();
        if (!StringUtils.hasText(privateKeyBase64)) {
            return Map.of();
        }
        String installId = turGlobalSettingsService.getOrCreateVigletAiInstallId();
        long exp = Instant.now().getEpochSecond()
                + Math.max(30, vigletAiProperty.getAssertionTtlSeconds());
        String nonce = nonce();
        String signature = sign(privateKeyBase64, payload(installId, exp, nonce));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(INSTALL_HEADER, installId);
        headers.put(ASSERTION_HEADER, String.join(".", ASSERTION_VERSION, installId,
                Long.toString(exp), nonce, signature));
        return headers;
    }

    /** The canonical payload both halves agree on. Package-private so a test can re-derive it. */
    static String payload(String installId, long exp, String nonce) {
        return ASSERTION_VERSION + DELIM + PRODUCT + DELIM + installId + DELIM + exp + DELIM + nonce;
    }

    private static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(KEY_ALGORITHM).generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot mint the Viglet AI install signing key: " + KEY_ALGORITHM
                            + " is unavailable on this JVM.", e);
        }
    }

    private static String sign(String privateKeyBase64, String payload) {
        try {
            PrivateKey privateKey = KeyFactory.getInstance(KEY_ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(
                            Base64.getDecoder().decode(privateKeyBase64)));
            Signature signature = Signature.getInstance(KEY_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Signing the Viglet AI install assertion failed", e);
        }
    }

    private static String nonce() {
        byte[] bytes = new byte[12];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
