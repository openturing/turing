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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.tool.TurCodeInterpreterUrlSigner.VerifyResult;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Unit tests for the HMAC + cookie-binding URL signer. Exercises the
 * full matrix: happy-path verify, tampered query, expired token, missing
 * params, cookie mismatch, disabled-state passthrough, and cookie-bound
 * vs cookie-bound-off behavior.
 *
 * <p>The signer reads its config from {@code @Value} fields — we set
 * them by reflection rather than spinning up Spring just to flip booleans.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
class TurCodeInterpreterUrlSignerTest {

    private static final String SECRET = "test-secret-do-not-use-in-prod-aaaaaaaaaaaaaaaa";
    private static final String SID = "abc12345";
    private static final String FILE = "report.pdf";
    private static final String CONV = "conv-marina-alex-123";

    private TurGlobalSettingsService settings;
    private TurCodeInterpreterUrlSigner signer;

    @BeforeEach
    void setUp() throws Exception {
        settings = mock(TurGlobalSettingsService.class);
        when(settings.getCodeInterpreterUrlSigningSecret()).thenReturn(SECRET);
        signer = new TurCodeInterpreterUrlSigner(settings);
        setField("enabled", true);
        setField("ttlSeconds", 3600L);
        setField("cookieBound", true);
    }

    // ───────────────────────── Happy path ─────────────────────────

    @Test
    void sign_thenVerify_succeedsWithMatchingCookie() {
        String query = signer.signQueryString(SID, FILE, CONV);
        assertThat(query).startsWith("?exp=").contains("&sig=");

        String[] parts = parseQuery(query);
        VerifyResult r = signer.verify(SID, FILE, parts[0], parts[1], CONV);
        assertThat(r).isEqualTo(VerifyResult.OK);
    }

    @Test
    void verify_passesWithoutCookieWhenCookieBindingOff() throws Exception {
        setField("cookieBound", false);
        // With cookie-binding off, the operator's call site signs with an
        // empty convId (the cookie isn't used so binding to one is moot).
        // Verify accepts ANY cookie value because the cookie is ignored.
        String query = signer.signQueryString(SID, FILE, "");
        String[] parts = parseQuery(query);

        assertThat(signer.verify(SID, FILE, parts[0], parts[1], null))
                .as("no cookie + binding off → OK").isEqualTo(VerifyResult.OK);
        assertThat(signer.verify(SID, FILE, parts[0], parts[1], "irrelevant-conv"))
                .as("wrong cookie + binding off → OK (cookie ignored)").isEqualTo(VerifyResult.OK);
    }

    // ───────────────────────── Tamper / replay ─────────────────────────

    @Test
    void verify_rejectsTamperedSignature() {
        String query = signer.signQueryString(SID, FILE, CONV);
        String[] parts = parseQuery(query);
        // Flip last hex char of the signature.
        String tampered = parts[1].substring(0, parts[1].length() - 1)
                + (parts[1].endsWith("0") ? "1" : "0");

        VerifyResult r = signer.verify(SID, FILE, parts[0], tampered, CONV);
        assertThat(r).isEqualTo(VerifyResult.COOKIE_MISMATCH);
        // Note: when cookie-binding is on, a tampered sig can't be
        // distinguished from a cookie mismatch at the verify level — both
        // map to the same observable outcome (HMAC fails). The enum case
        // is COOKIE_MISMATCH because that's the more security-relevant
        // diagnostic and the caller logs it identically.
    }

    @Test
    void verify_rejectsTamperedSessionId() {
        String query = signer.signQueryString(SID, FILE, CONV);
        String[] parts = parseQuery(query);

        VerifyResult r = signer.verify("zzzzzzzz", FILE, parts[0], parts[1], CONV);
        assertThat(r).isIn(VerifyResult.COOKIE_MISMATCH, VerifyResult.INVALID);
    }

    @Test
    void verify_rejectsTamperedFilename() {
        String query = signer.signQueryString(SID, FILE, CONV);
        String[] parts = parseQuery(query);

        VerifyResult r = signer.verify(SID, "other.pdf", parts[0], parts[1], CONV);
        assertThat(r).isIn(VerifyResult.COOKIE_MISMATCH, VerifyResult.INVALID);
    }

    // ───────────────────────── Expiry ─────────────────────────

    @Test
    void verify_rejectsExpiredToken() {
        // Use a fixed long-in-the-past exp, then forge the sig with the
        // real key to isolate "expiry check fires BEFORE signature check".
        long pastExp = 1_000_000L; // 1970-01-12
        String sigForExpired = computeSig(SID, FILE, pastExp, CONV);
        VerifyResult r = signer.verify(SID, FILE, String.valueOf(pastExp), sigForExpired, CONV);
        assertThat(r).isEqualTo(VerifyResult.EXPIRED);
    }

    @Test
    void verify_rejectsMalformedExpiry() {
        VerifyResult r = signer.verify(SID, FILE, "not-a-number", "deadbeef", CONV);
        assertThat(r).isEqualTo(VerifyResult.MALFORMED_EXPIRY);
    }

    // ───────────────────────── Missing parts ─────────────────────────

    @Test
    void verify_rejectsMissingSignature() {
        VerifyResult r = signer.verify(SID, FILE, "9999999999", null, CONV);
        assertThat(r).isEqualTo(VerifyResult.MISSING_SIGNATURE);
    }

    @Test
    void verify_rejectsMissingExpiry() {
        VerifyResult r = signer.verify(SID, FILE, null, "deadbeef", CONV);
        assertThat(r).isEqualTo(VerifyResult.MISSING_SIGNATURE);
    }

    @Test
    void verify_rejectsBlankParts() {
        VerifyResult r = signer.verify(SID, FILE, "  ", "  ", CONV);
        assertThat(r).isEqualTo(VerifyResult.MISSING_SIGNATURE);
    }

    // ───────────────────────── Cookie binding ─────────────────────────

    @Test
    void verify_rejectsCookieMismatch_whenCookieBound() {
        String query = signer.signQueryString(SID, FILE, CONV);
        String[] parts = parseQuery(query);

        VerifyResult r = signer.verify(SID, FILE, parts[0], parts[1], "different-conv");
        assertThat(r).isEqualTo(VerifyResult.COOKIE_MISMATCH);
    }

    @Test
    void verify_rejectsMissingCookie_whenCookieBound() {
        String query = signer.signQueryString(SID, FILE, CONV);
        String[] parts = parseQuery(query);

        VerifyResult r = signer.verify(SID, FILE, parts[0], parts[1], null);
        assertThat(r).isEqualTo(VerifyResult.COOKIE_MISMATCH);
    }

    // ───────────────────────── Disabled / passthrough ─────────────────────────

    @Test
    void sign_returnsEmptyWhenDisabled() throws Exception {
        setField("enabled", false);
        assertThat(signer.signQueryString(SID, FILE, CONV)).isEmpty();
    }

    @Test
    void verify_passesWhenDisabled_regardlessOfInput() throws Exception {
        setField("enabled", false);
        VerifyResult r = signer.verify(SID, FILE, null, null, null);
        assertThat(r)
                .as("disabled = no enforcement; the operator opted out")
                .isEqualTo(VerifyResult.OK);
    }

    // ───────────────────────── Helpers ─────────────────────────

    /** Splits "?exp=N&sig=H" into [N, H]. */
    private static String[] parseQuery(String query) {
        String stripped = query.startsWith("?") ? query.substring(1) : query;
        String exp = null;
        String sig = null;
        for (String kv : stripped.split("&")) {
            int eq = kv.indexOf('=');
            String k = kv.substring(0, eq);
            String v = kv.substring(eq + 1);
            if ("exp".equals(k)) exp = v;
            else if ("sig".equals(k)) sig = v;
        }
        return new String[] { exp, sig };
    }

    /** Reimplements the signer's HMAC for "forge a sig with a chosen exp" tests. */
    private static String computeSig(String sid, String file, long exp, String conv) {
        try {
            String payload = "v1|" + sid + "|" + file + "|" + exp + "|" + conv;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] b = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(b);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(String name, Object value) throws Exception {
        Field f = TurCodeInterpreterUrlSigner.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(signer, value);
    }
}
