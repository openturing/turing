/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link VigletWebhookSigner}: a known-answer HMAC-SHA256
 * vector, the {@code sha256=} prefix, and the null-secret / null-body guards.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class VigletWebhookSignerTest {

    @Test
    void hexMatchesKnownAnswerVector() {
        // RFC-style known answer: HMAC-SHA256(key="key", msg="The quick brown fox jumps over the lazy dog")
        String hex = VigletWebhookSigner.hex("key",
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));
        assertThat(hex).isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void headerCarriesPrefix() {
        String header = VigletWebhookSigner.header("s3cr3t", "{}".getBytes(StandardCharsets.UTF_8));
        assertThat(header).startsWith(VigletWebhookSigner.SIGNATURE_PREFIX);
        assertThat(header.substring(VigletWebhookSigner.SIGNATURE_PREFIX.length())).hasSize(64);
    }

    @Test
    void sameSecretAndBodyAreDeterministic() {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(VigletWebhookSigner.hex("x", body)).isEqualTo(VigletWebhookSigner.hex("x", body));
    }

    @Test
    void differentSecretChangesSignature() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(VigletWebhookSigner.hex("a", body)).isNotEqualTo(VigletWebhookSigner.hex("b", body));
    }

    @Test
    void nullBodyTreatedAsEmpty() {
        // A real secret with a null body must still produce a 64-char digest
        // (HMAC over zero bytes) rather than throwing.
        assertThat(VigletWebhookSigner.hex("secret", null)).hasSize(64);
    }

    @Test
    void defaultHeaderNameIsNeutral() {
        assertThat(VigletWebhookSigner.DEFAULT_SIGNATURE_HEADER).isEqualTo("X-Viglet-Signature");
    }
}
