/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.agent.TurChatWebhook;

/**
 * REST projection for {@link TurChatWebhook} behind {@code /api/genai/webhook}.
 *
 * <p>The {@code authHeader} is <b>write-only</b>: the admin form posts it in
 * plaintext, the API encrypts + stores it, and reads NEVER echo it back —
 * {@link #from(TurChatWebhook)} always emits {@code authHeader=null} and a
 * boolean {@link #hasAuthHeader} so the UI can show "configured" without
 * leaking the secret. A blank {@code authHeader} on update means "leave the
 * stored credential untouched"; only an explicit value rotates it.
 *
 * <p>The optional HMAC {@code signingSecret} (T378) follows the exact same
 * write-only convention — reads emit {@code signingSecret=null} +
 * {@link #hasSigningSecret}, and a blank value on update keeps the stored key.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurChatWebhookDto(
        String id,
        String name,
        String description,
        String targetUrl,
        String httpMethod,
        String headersJson,
        String slotTrigger,
        String includeSlots,
        String payloadTemplate,
        String authHeader,
        boolean hasAuthHeader,
        String signingSecret,
        boolean hasSigningSecret,
        String signatureHeader,
        Boolean enabled) {

    public static TurChatWebhookDto from(TurChatWebhook webhook) {
        if (webhook == null) {
            return null;
        }
        boolean hasAuth = webhook.getAuthHeader() != null && !webhook.getAuthHeader().isBlank();
        boolean hasSigning = webhook.getSigningSecret() != null && !webhook.getSigningSecret().isBlank();
        return new TurChatWebhookDto(
                webhook.getId(),
                webhook.getName(),
                webhook.getDescription(),
                webhook.getTargetUrl(),
                webhook.getHttpMethod(),
                webhook.getHeadersJson(),
                webhook.getSlotTrigger(),
                webhook.getIncludeSlots(),
                webhook.getPayloadTemplate(),
                // Never echo a secret — only signal its presence.
                null,
                hasAuth,
                null,
                hasSigning,
                webhook.getSignatureHeader(),
                webhook.isEnabled());
    }
}
