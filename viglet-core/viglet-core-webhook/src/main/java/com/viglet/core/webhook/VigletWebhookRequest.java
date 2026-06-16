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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One outbound webhook call, neutral of any product entity: a target URL, an
 * HTTP method, opaque body bytes, custom headers, and an optional (already
 * decrypted) HMAC signing secret.
 *
 * <p>The product owns body serialization (Jackson, templating) and secret
 * storage; it hands {@link VigletWebhookDispatcher} the finished bytes and the
 * plaintext secret. When {@link #signingSecret()} is non-blank and a body is
 * present, the dispatcher adds {@code <signatureHeaderName>: sha256=<hex>}
 * (see {@link VigletWebhookSigner}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record VigletWebhookRequest(
        String method,
        String url,
        byte[] body,
        Map<String, String> headers,
        String contentType,
        String signingSecret,
        String signatureHeaderName) {

    public VigletWebhookRequest {
        if (method == null || method.isBlank()) {
            method = "POST";
        }
        if (headers == null) {
            headers = Map.of();
        }
        if (signatureHeaderName == null || signatureHeaderName.isBlank()) {
            signatureHeaderName = VigletWebhookSigner.DEFAULT_SIGNATURE_HEADER;
        }
    }

    /** Whether this request should be HMAC-signed (non-blank secret + a body to sign). */
    public boolean signed() {
        return signingSecret != null && !signingSecret.isBlank() && body != null && body.length > 0;
    }

    /** Whether the HTTP method carries a body (POST/PUT/PATCH). */
    public boolean bodyBearing() {
        String m = method.toUpperCase(java.util.Locale.ROOT);
        return m.equals("POST") || m.equals("PUT") || m.equals("PATCH");
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder — most fields are optional, so a record canonical ctor would be unwieldy. */
    public static final class Builder {
        private String method = "POST";
        private String url;
        private byte[] body;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String contentType = "application/json";
        private String signingSecret;
        private String signatureHeaderName;

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder body(byte[] body) {
            this.body = body;
            return this;
        }

        public Builder header(String name, String value) {
            if (name != null && !name.isBlank()) {
                this.headers.put(name, value == null ? "" : value);
            }
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this::header);
            }
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder signingSecret(String signingSecret) {
            this.signingSecret = signingSecret;
            return this;
        }

        public Builder signatureHeaderName(String signatureHeaderName) {
            this.signatureHeaderName = signatureHeaderName;
            return this;
        }

        public VigletWebhookRequest build() {
            return new VigletWebhookRequest(method, url, body, new LinkedHashMap<>(headers),
                    contentType, signingSecret, signatureHeaderName);
        }
    }
}
