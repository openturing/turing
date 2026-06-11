/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T62 / §VII.6.c — an admin-declared outbound webhook. A webhook
 * {@code POST}s the conversation transcript + slot snapshot as JSON to a
 * configured CRM endpoint (Salesforce / HubSpot / Pipedrive, or any HTTP
 * receiver / iPaaS such as Zapier / Make).
 *
 * <p>Two firing modes, distinguished by {@link #slotTrigger}:
 * <ul>
 *   <li><b>Handoff channel</b> — when {@code slotTrigger} is blank, the
 *   webhook is only fired explicitly via {@code POST /chat/handoff} with
 *   {@code channel=WEBHOOK} and {@code destination=<webhook name>}. This is
 *   the "send this lead to the CRM" button.</li>
 *   <li><b>Slot subscription</b> — when {@code slotTrigger} is set to a slot
 *   name (e.g. {@code email}) or the wildcard {@code *}, the
 *   {@code TurChatWebhookDispatcher} fires the webhook automatically the
 *   moment that slot is written through any of the instrumented slot write
 *   paths (it edge-detects changes off the {@code TurChatSlotEventBus}).</li>
 * </ul>
 *
 * <p>The optional {@link #authHeader} carries a full {@code Authorization}
 * header value (e.g. {@code "Bearer xyz"}) so the receiver can authenticate
 * the call. It is stored encrypted at rest via {@code TurSecretCryptoService}
 * and never echoed back through the REST DTO — the admin UI surfaces only a
 * {@code hasAuthHeader} flag.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "tur_chat_webhook")
public class TurChatWebhook implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    /** Stable, admin-visible name. Unique; also the handoff {@code destination}. */
    @Column(name = "name", nullable = false, length = 128, unique = true)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    /** Absolute URL the transcript/slot payload is sent to. */
    @Column(name = "targetUrl", nullable = false, length = 1024)
    private String targetUrl;

    /**
     * HTTP method used for the call: {@code POST} (default), {@code PUT},
     * {@code PATCH}, {@code GET}, or {@code DELETE}. Body-bearing methods
     * (POST/PUT/PATCH) send the rendered payload; {@code GET}/{@code DELETE}
     * send headers only (no body).
     */
    @Column(name = "httpMethod", nullable = false, length = 8)
    private String httpMethod = "POST";

    /**
     * Optional custom request headers as a JSON object string
     * (e.g. {@code {"X-Api-Key":"abc","X-Source":"turing"}}). Applied on top
     * of the auto {@code Content-Type} and the {@link #authHeader}-derived
     * {@code Authorization}; a custom {@code Authorization} here wins.
     */
    @Column(name = "headersJson", columnDefinition = "longtext")
    private String headersJson;

    /**
     * Slot name that triggers an automatic fire, the wildcard {@code *} to
     * fire on any slot write, or blank to make this webhook handoff-only
     * (fired explicitly via the handoff endpoint, never on slot writes).
     */
    @Column(name = "slotTrigger", length = 128)
    private String slotTrigger;

    /**
     * Comma-separated whitelist of slot names to include in the payload's
     * {@code slots} map. Blank = include every non-internal slot.
     */
    @Column(name = "includeSlots", length = 1024)
    private String includeSlots;

    /**
     * Encrypted {@code Authorization} header value attached to every POST
     * (e.g. ciphertext of {@code "Bearer xyz"}). {@code null} = no auth.
     * Encrypted via {@code TurSecretCryptoService}; never exposed in the DTO.
     */
    @Column(name = "authHeader", length = 2048)
    private String authHeader;

    /**
     * Optional JSON body template shaping the payload to the CRM's own
     * schema, with {@code &#123;&#123;slot&#125;&#125;} placeholders
     * interpolated (JSON-escaped) from the conversation's slot map. Blank =
     * send the default envelope ({@code event/webhook/conversationId/slot/
     * transcript/slots}). Lets an admin POST e.g. HubSpot's
     * {@code {"properties":{"email":"&#123;&#123;email&#125;&#125;"}}} directly.
     */
    @Column(name = "payloadTemplate", columnDefinition = "longtext")
    private String payloadTemplate;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
