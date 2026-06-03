/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * HMAC-SHA256 signer for {@code /api/v2/workspace/file} URLs (T111).
 *
 * <p>The agent workspace (T111) serves byte-blobs through signed, time-limited
 * URLs. This signer mirrors {@code TurCodeInterpreterUrlSigner} (T79) and
 * <b>reuses the same server-side secret</b>
 * ({@link TurGlobalSettingsService#getCodeInterpreterUrlSigningSecret()}) so no
 * extra Global Settings row / Liquibase migration is needed — the secret is a
 * generic URL-signing key, not code-interpreter specific.
 *
 * <p>Three layers, same as T79:
 * <ul>
 *   <li><b>HMAC</b> — a guessed/leaked {@code key} alone is useless; the
 *       request must carry a {@code sig} computed with the server secret.</li>
 *   <li><b>Expiry</b> — even a valid copied URL stops working after
 *       {@code ttl-seconds} (default 1h).</li>
 *   <li><b>Conversation binding</b> — {@code agentId} and {@code conversationId}
 *       are part of the signed payload, so a URL minted for one conversation's
 *       workspace cannot be replayed against another's (no separate cookie
 *       needed: the conversation id rides in the URL and is signed).</li>
 * </ul>
 *
 * <h2>Canonical payload</h2>
 * <pre>
 *   v1|{agentId}|{conversationId}|{key}|{exp}
 * </pre>
 * Field-delimited with {@code |}. {@link TurAgentWorkspaceService#sanitizeKey}
 * rejects any key containing {@code |} (and {@code ..}, absolute paths) before
 * a URL is ever minted, so the delimiter is unambiguous.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurAgentWorkspaceUrlSigner {

    private static final String PAYLOAD_PREFIX = "v1";
    private static final String DELIM = "|";
    private static final String HMAC_ALG = "HmacSHA256";

    private final TurGlobalSettingsService settingsService;

    @Value("${turing.workspace.url-signing.enabled:true}")
    private boolean enabled;

    @Value("${turing.workspace.url-signing.ttl-seconds:3600}")
    private long ttlSeconds;

    public TurAgentWorkspaceUrlSigner(TurGlobalSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Builds the {@code ?exp=<ts>&sig=<hex>} suffix for a workspace key.
     * Returns an empty string when signing is disabled or the secret is
     * missing — the caller then emits the bare URL and {@link #verify} fails
     * open in the same state, so downloads still work.
     */
    public String signQueryString(String agentId, String conversationId, String key) {
        if (!enabled || !hasUsableSecret()) {
            return "";
        }
        long exp = Instant.now().getEpochSecond() + Math.max(60, ttlSeconds);
        String sig = hmacHex(payload(agentId, conversationId, key, exp));
        return "?exp=" + exp + "&sig=" + sig;
    }

    /**
     * Verifies a serve request. Returns {@link VerifyResult#OK} when signing
     * is disabled or no secret is configured (fail-open, matching the signing
     * side). The granular failure cases are for log diagnostics only — the
     * API maps every non-OK to a single 403.
     */
    public VerifyResult verify(String agentId, String conversationId, String key,
            String expParam, String sigParam) {
        if (!enabled || !hasUsableSecret()) {
            return VerifyResult.OK;
        }
        if (expParam == null || expParam.isBlank() || sigParam == null || sigParam.isBlank()) {
            return VerifyResult.MISSING_SIGNATURE;
        }
        long exp;
        try {
            exp = Long.parseLong(expParam.trim());
        } catch (NumberFormatException e) {
            return VerifyResult.MALFORMED_EXPIRY;
        }
        if (Instant.now().getEpochSecond() > exp) {
            return VerifyResult.EXPIRED;
        }
        String expected = hmacHex(payload(agentId, conversationId, key, exp));
        if (!constantTimeEquals(expected, sigParam.trim())) {
            return VerifyResult.INVALID;
        }
        return VerifyResult.OK;
    }

    /** Outcomes of {@link #verify}; the API collapses every non-OK to 403. */
    public enum VerifyResult {
        OK, MISSING_SIGNATURE, MALFORMED_EXPIRY, EXPIRED, INVALID
    }

    private static String payload(String agentId, String conversationId, String key, long exp) {
        return PAYLOAD_PREFIX + DELIM
                + (agentId == null ? "" : agentId) + DELIM
                + (conversationId == null ? "" : conversationId) + DELIM
                + (key == null ? "" : key) + DELIM
                + exp;
    }

    private final AtomicBoolean blankSecretWarned = new AtomicBoolean(false);

    private boolean hasUsableSecret() {
        String secret = settingsService.getCodeInterpreterUrlSigningSecret();
        boolean ok = secret != null && !secret.isBlank();
        if (!ok && blankSecretWarned.compareAndSet(false, true)) {
            log.error("[WorkspaceUrlSigner] HMAC secret is blank in Global Settings — "
                    + "workspace URL signing is DISABLED (downloads work, but the signature gate is off). "
                    + "Restart the app: TurConfigVarOnStartup auto-heals the row.");
        }
        return ok;
    }

    private String hmacHex(String payload) {
        String secret = settingsService.getCodeInterpreterUrlSigningSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "HMAC signing invoked with blank secret — hasUsableSecret() should have prevented this");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sig);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
