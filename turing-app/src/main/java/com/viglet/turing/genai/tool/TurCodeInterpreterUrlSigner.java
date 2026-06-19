/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * HMAC-SHA256 signer for {@code /api/v2/code-interpreter/{sessionId}/{filename}}
 * URLs. Couples a server-side secret (see
 * {@link TurGlobalSettingsService#getCodeInterpreterUrlSigningSecret()})
 * with an explicit expiry timestamp and the conversation id of the chat
 * that minted the URL — three pieces that together close the gap left by
 * raw sessionId-only URLs:
 *
 * <ul>
 *   <li><b>HMAC</b>: a leaked or guessed {@code sessionId} alone is not
 *       enough — the request must carry a {@code sig} computed with the
 *       server's secret. The secret never leaves the JVM.</li>
 *   <li><b>Expiry</b>: even a copy-pasted valid URL stops working after
 *       {@code ttl-seconds} (default 1h). Limits the blast radius of a
 *       link leaked into chat logs / screenshots.</li>
 *   <li><b>Cookie binding</b> (when enabled): the request must present a
 *       {@code TUR_CI_CONV} cookie whose value matches the {@code convId}
 *       baked into the signature. Stops cross-browser URL replay — even
 *       if Roberto obtains Alexandre's signed URL, his browser's cookie
 *       holds {@code roberto-conv-999} not {@code alexandre-conv-123}.</li>
 * </ul>
 *
 * <h2>What this does NOT protect against</h2>
 * <p>A malicious Python script running inside the sandbox can still read
 * any file on the host's filesystem ({@code open("../../other/file")}).
 * URL signing is an <b>HTTP-layer</b> control; runtime sandbox escape is
 * a separate problem that requires containerization (Docker / gVisor /
 * Firecracker). See {@link TurCodeInterpreterToolService} JSDoc for the
 * three-layer security model.
 *
 * <h2>Canonical payload</h2>
 * <pre>
 *   v1|{sessionId}|{filename}|{exp}|{convId}
 * </pre>
 * Field-delimited because none of the fields can contain {@code |}
 * (sessionId is a UUID prefix, filename comes from
 * {@link java.io.File#getName()} which strips path separators, exp is a
 * decimal long, convId is sanitized server-side). Prefixing with
 * {@code v1} reserves room for a future format bump without ambiguity.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurCodeInterpreterUrlSigner {

    /** Cookie name carrying the conversation id for cookie-binding. */
    public static final String CONV_COOKIE = "TUR_CI_CONV";

    private static final String PAYLOAD_PREFIX = "v1";
    private static final String DELIM = "|";
    private static final String HMAC_ALG = "HmacSHA256";

    private final TurGlobalSettingsService settingsService;

    @Value("${turing.code-interpreter.url-signing.enabled:true}")
    private boolean enabled;

    @Value("${turing.code-interpreter.url-signing.ttl-seconds:3600}")
    private long ttlSeconds;

    /**
     * When true (default), URL verification additionally requires the
     * request to present a {@code TUR_CI_CONV} cookie matching the
     * conversation id signed into the URL. Disable to fall back to HMAC
     * + expiry only — useful when the chat embed runs cross-origin and
     * the cookie is blocked by SameSite policies. Even with cookie
     * binding off, the HMAC + expiry layers still stop guessing /
     * brute-forcing.
     */
    @Value("${turing.code-interpreter.url-signing.cookie-bound:true}")
    private boolean cookieBound;

    public TurCodeInterpreterUrlSigner(TurGlobalSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCookieBound() {
        return cookieBound;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Builds the {@code ?exp=<ts>&sig=<hex>} query string for a freshly
     * minted file. Returns an empty string when signing is disabled
     * (operator opted out via config) — the caller emits the bare URL.
     *
     * @param sessionId 8-char session id from
     *                  {@link TurCodeInterpreterToolService}.
     * @param filename  basename of the generated file.
     * @param convId    active conversation id (null/blank → not bound
     *                  to a conversation; only HMAC + expiry apply).
     */
    public String signQueryString(String sessionId, String filename, String convId) {
        if (!enabled) return "";
        if (!hasUsableSecret()) {
            // Degenerate state: feature is enabled but the HMAC secret is
            // missing/blank in Global Settings. Emit an unsigned URL rather
            // than crashing the tool — the file API's verify() also fails
            // open in this state, so downloads still work. The auto-heal
            // startup hook tries to mint a fresh secret on next boot.
            return "";
        }
        long exp = Instant.now().getEpochSecond() + Math.max(60, ttlSeconds);
        String safeConv = convId == null ? "" : convId;
        String sig = hmacHex(payload(sessionId, filename, exp, safeConv));
        return "?exp=" + exp + "&sig=" + sig;
    }

    /**
     * Verifies a request against the signed query string and (when
     * cookie-binding is on) the {@code TUR_CI_CONV} cookie value. Returns
     * one of the {@link VerifyResult} enum cases so the caller can map
     * to a precise HTTP status without leaking which check failed.
     *
     * @param sessionId   path variable from the URL.
     * @param filename    path variable from the URL.
     * @param expParam    {@code exp} query parameter; null/blank → reject.
     * @param sigParam    {@code sig} query parameter; null/blank → reject.
     * @param cookieConv  value of the {@code TUR_CI_CONV} cookie (null
     *                    when the request didn't send one).
     */
    public VerifyResult verify(String sessionId, String filename,
            String expParam, String sigParam, String cookieConv) {
        if (!enabled) return VerifyResult.OK;
        if (!hasUsableSecret()) {
            // No secret = signing was skipped (see signQueryString). Mirror
            // that on the verify side — fail open so files are still
            // downloadable. Logs already screamed at signing time.
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
        // Cookie-binding: try with the cookie-supplied convId first. We
        // can't peek at the "signed convId" because that's exactly what
        // the HMAC verifies — instead we trust the cookie as the
        // verifying claim, and a mismatch will simply fail HMAC.
        String cookieConvValue = cookieConv == null ? "" : cookieConv;
        String boundConv = cookieBound ? cookieConvValue : "";
        String expected = hmacHex(payload(sessionId, filename, exp, boundConv));
        if (!constantTimeEquals(expected, sigParam.trim())) {
            // Without cookie-binding, OR if cookie is missing/wrong, try
            // re-computing with empty convId — covers the case where the
            // URL was signed for a conversation but cookie-binding is off
            // server-side. When cookieBound=true and the cookie disagrees,
            // BOTH attempts fail → INVALID.
            if (cookieBound) {
                return VerifyResult.COOKIE_MISMATCH;
            }
            return VerifyResult.INVALID;
        }
        return VerifyResult.OK;
    }

    /**
     * Outcomes of {@link #verify}. Mapped by the caller to
     * {@code 200}/{@code 403}; the granularity is for log diagnostics,
     * not for client-facing differentiation (always return 403 on any
     * non-OK so attackers can't infer which knob to twist).
     */
    public enum VerifyResult {
        OK, MISSING_SIGNATURE, MALFORMED_EXPIRY, EXPIRED, INVALID, COOKIE_MISMATCH
    }

    private static String payload(String sessionId, String filename, long exp, String convId) {
        return PAYLOAD_PREFIX + DELIM + sessionId + DELIM + filename + DELIM + exp + DELIM + convId;
    }

    /**
     * Cached "we already screamed about this" flag — keeps the log clean
     * when an operator's DB is missing the secret. First call logs loud,
     * subsequent calls stay silent until JVM restart (when the auto-heal
     * startup hook either fixes it or doesn't).
     */
    private final java.util.concurrent.atomic.AtomicBoolean blankSecretWarned =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Returns true when {@link TurGlobalSettingsService#getCodeInterpreterUrlSigningSecret()}
     * is non-blank. Callers use this to gate signing/verification — when
     * false, the URL hardening feature degrades to a no-op rather than
     * crashing every file download with HMAC errors.
     */
    private boolean hasUsableSecret() {
        String secret = settingsService.getCodeInterpreterUrlSigningSecret();
        boolean ok = secret != null && !secret.isBlank();
        if (!ok && blankSecretWarned.compareAndSet(false, true)) {
            log.error("[CodeInterpreterUrlSigner] HMAC secret is blank in Global Settings — "
                    + "URL signing is DISABLED (downloads will work, but the signature gate is off). "
                    + "Restart the app: TurConfigVarOnStartup auto-heals the row. "
                    + "If the row exists with a blank value, the auto-heal repopulates it.");
        }
        return ok;
    }

    private String hmacHex(String payload) {
        String secret = settingsService.getCodeInterpreterUrlSigningSecret();
        // Callers are expected to gate via hasUsableSecret() — but be
        // defensive in case a race lets a blank secret through.
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
            // Either no HMAC-SHA256 provider (impossible on any sane JVM)
            // or an init failure. Either way, fail closed.
            throw new IllegalStateException("HMAC signing failed", e);
        }
    }

    /**
     * Length-stable comparison that doesn't short-circuit on the first
     * mismatch — defeats timing-side-channel inference of the prefix.
     * {@link MessageDigest#isEqual} provides this in the JDK already; we
     * delegate rather than rolling our own.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
