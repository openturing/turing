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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.workspace.TurAgentWorkspaceUrlSigner.VerifyResult;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Unit tests for {@link TurAgentWorkspaceUrlSigner} (T111).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurAgentWorkspaceUrlSignerTest {

    private static final String SECRET = "test-hmac-secret-0123456789abcdef";
    private static final String AGENT = "agent-1";
    private static final String CONV = "conv-9";
    private static final String KEY = "reports/lead-export.csv";

    @Mock
    private TurGlobalSettingsService settingsService;

    private TurAgentWorkspaceUrlSigner signer;

    @BeforeEach
    void setUp() throws Exception {
        signer = new TurAgentWorkspaceUrlSigner(settingsService);
        setField("enabled", true);
        setField("ttlSeconds", 3600L);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = TurAgentWorkspaceUrlSigner.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(signer, value);
    }

    private String[] expAndSig(String query) {
        // query is "?exp=<ts>&sig=<hex>"
        String body = query.substring(1);
        String[] parts = body.split("&");
        String exp = parts[0].substring("exp=".length());
        String sig = parts[1].substring("sig=".length());
        return new String[] {exp, sig};
    }

    @Test
    void signThenVerifyRoundTrips() {
        when(settingsService.getCodeInterpreterUrlSigningSecret()).thenReturn(SECRET);

        String query = signer.signQueryString(AGENT, CONV, KEY);
        assertTrue(query.startsWith("?exp="));
        String[] es = expAndSig(query);

        assertEquals(VerifyResult.OK, signer.verify(AGENT, CONV, KEY, es[0], es[1]));
    }

    @Test
    void verifyRejectsTamperedKey() {
        when(settingsService.getCodeInterpreterUrlSigningSecret()).thenReturn(SECRET);

        String[] es = expAndSig(signer.signQueryString(AGENT, CONV, KEY));
        assertEquals(VerifyResult.INVALID, signer.verify(AGENT, CONV, "other/key.csv", es[0], es[1]));
    }

    @Test
    void verifyRejectsTamperedConversation() {
        when(settingsService.getCodeInterpreterUrlSigningSecret()).thenReturn(SECRET);

        String[] es = expAndSig(signer.signQueryString(AGENT, CONV, KEY));
        assertEquals(VerifyResult.INVALID, signer.verify(AGENT, "conv-EVIL", KEY, es[0], es[1]));
    }

    @Test
    void verifyRejectsExpired() {
        when(settingsService.getCodeInterpreterUrlSigningSecret()).thenReturn(SECRET);

        String[] es = expAndSig(signer.signQueryString(AGENT, CONV, KEY));
        // exp in the past
        assertEquals(VerifyResult.EXPIRED, signer.verify(AGENT, CONV, KEY, "1", es[1]));
    }

    @Test
    void verifyRejectsMissingAndMalformed() {
        when(settingsService.getCodeInterpreterUrlSigningSecret()).thenReturn(SECRET);

        assertEquals(VerifyResult.MISSING_SIGNATURE, signer.verify(AGENT, CONV, KEY, null, null));
        assertEquals(VerifyResult.MISSING_SIGNATURE, signer.verify(AGENT, CONV, KEY, "", ""));
        assertEquals(VerifyResult.MALFORMED_EXPIRY, signer.verify(AGENT, CONV, KEY, "not-a-number", "deadbeef"));
    }

    @Test
    void disabledSignerEmitsNoSuffixAndVerifiesOpen() throws Exception {
        setField("enabled", false);
        assertEquals("", signer.signQueryString(AGENT, CONV, KEY));
        assertEquals(VerifyResult.OK, signer.verify(AGENT, CONV, KEY, null, null));
    }

    @Test
    void blankSecretFailsOpen() {
        when(settingsService.getCodeInterpreterUrlSigningSecret()).thenReturn("  ");
        // enabled but no usable secret → degrade to no-op (downloads still work)
        assertEquals("", signer.signQueryString(AGENT, CONV, KEY));
        assertEquals(VerifyResult.OK, signer.verify(AGENT, CONV, KEY, "123", "abc"));
    }

    @Test
    void distinctKeysProduceDistinctSignatures() {
        when(settingsService.getCodeInterpreterUrlSigningSecret()).thenReturn(SECRET);

        String sigA = expAndSig(signer.signQueryString(AGENT, CONV, "a.txt"))[1];
        String sigB = expAndSig(signer.signQueryString(AGENT, CONV, "b.txt"))[1];
        assertNotEquals(sigA, sigB);
    }
}
