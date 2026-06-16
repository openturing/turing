/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.webhook;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signing for outbound webhooks, shared across Viglet products.
 *
 * <p>The digest is computed over the <b>exact</b> request body bytes that are
 * sent on the wire (sign-then-send the same array, so no re-serialisation can
 * drift the signature) and emitted as {@code <prefix><lowercase hex>}, i.e.
 * {@code sha256=<hex>}. This matches the de-facto convention used by GitHub
 * ({@code X-Hub-Signature-256}), Stripe and the Shio {@code /api/revalidate}
 * receiver, so existing receivers verify Viglet-signed calls unchanged.
 *
 * <p>The header <i>name</i> is the product's choice ({@link #DEFAULT_SIGNATURE_HEADER}
 * is a neutral default); this class only computes the value.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletWebhookSigner {

    /** Neutral default header name carrying the signature value. */
    public static final String DEFAULT_SIGNATURE_HEADER = "X-Viglet-Signature";

    /** Prefix on the header value, identifying the digest algorithm. */
    public static final String SIGNATURE_PREFIX = "sha256=";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private VigletWebhookSigner() {
    }

    /**
     * Lowercase hex of {@code HMAC-SHA256(secret, body)}. A null body is treated
     * as empty; the secret must be non-empty (HMAC has no empty key) — callers
     * gate on a configured secret (see {@link VigletWebhookRequest#signed()}).
     */
    public static String hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body == null ? new byte[0] : body));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute webhook signature", e);
        }
    }

    /** The full {@code sha256=<hex>} header value the receiver expects. */
    public static String header(String secret, byte[] body) {
        return SIGNATURE_PREFIX + hex(secret, body);
    }
}
