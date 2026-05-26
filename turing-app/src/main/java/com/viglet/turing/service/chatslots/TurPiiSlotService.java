/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T61 — privacy enforcement for slots whose name begins with the
 * conventional PII prefix ({@code pii_*}). Three guarantees:
 *
 * <ol>
 *   <li><b>Encryption at rest</b> — values for {@code pii_*} slots are
 *       serialized as {@code pii_enc:v1:<aes-gcm-base64>} into the
 *       {@code chat_flow_state.variablesJson} column. The envelope marker
 *       makes encrypted values self-describing — a row can be safely
 *       re-read by older code paths (they'll see the envelope string and
 *       NOT a plaintext value, which is the conservative failure mode).
 *   </li>
 *   <li><b>Redaction in logs / analytics</b> — {@link #redact(Map)} returns
 *       a copy with {@code pii_*} values replaced by {@code "[redacted]"};
 *       call sites that log a variables map use this view so raw PII
 *       never lands in log aggregators.</li>
 *   <li><b>Configurable TTL</b> — paired with the T60 audit log, a
 *       scheduled job ({@code TurPiiSlotTtlCleanupJob}) clears
 *       {@code pii_*} slot values whose most recent audit-log entry is
 *       older than {@link #getTtlHours()}. Default 24h. {@code 0} or
 *       negative disables retention enforcement.</li>
 * </ol>
 *
 * <p>The service is wired into the static utility {@code ChatFlowOps} via a
 * Spring-managed singleton accessed through {@link #getInstance()}, with the
 * underlying reference set on {@code @PostConstruct}. Pure unit tests that
 * don't bootstrap Spring see {@code getInstance() == null} and the helpers
 * degrade to identity functions — preserves the existing behaviour of tests
 * that operate on slot maps directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurPiiSlotService {

    public static final String PII_PREFIX = "pii_";
    public static final String ENCRYPTED_MARKER = "pii_enc:v1:";
    public static final String REDACTED_PLACEHOLDER = "[redacted]";

    private static volatile TurPiiSlotService instance;

    private final TurSecretCryptoService cryptoService;
    private final com.viglet.turing.system.TurGlobalSettingsService globalSettings;

    public TurPiiSlotService(TurSecretCryptoService cryptoService,
            com.viglet.turing.system.TurGlobalSettingsService globalSettings) {
        this.cryptoService = cryptoService;
        this.globalSettings = globalSettings;
    }

    @PostConstruct
    void register() {
        instance = this;
    }

    @PreDestroy
    void unregister() {
        if (instance == this) {
            instance = null;
        }
    }

    /**
     * Spring-managed singleton accessor. May return {@code null} in test
     * environments that don't bootstrap the full context — callers must
     * handle that as "PII features disabled" (transparent passthrough).
     */
    public static TurPiiSlotService getInstance() {
        return instance;
    }

    /**
     * Returns true when the slot name marks this slot as carrying PII.
     */
    public static boolean isPiiSlot(String slotName) {
        return slotName != null && slotName.startsWith(PII_PREFIX);
    }

    /**
     * Returns true when the stored value carries the encryption envelope —
     * i.e. it has already been encrypted by a prior write.
     */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_MARKER);
    }

    /**
     * Encrypts every {@code pii_*} slot in the map IN PLACE. Existing
     * encrypted envelopes are left untouched (idempotent). Empty / null
     * values are left untouched — the AES-GCM cipher rejects empty
     * plaintext, and an empty slot carries no PII to protect.
     */
    public Map<String, String> encryptInPlace(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return variables;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (!isPiiSlot(name) || value == null || value.isEmpty() || isEncrypted(value)) {
                continue;
            }
            try {
                String cipher = cryptoService.encrypt(value);
                if (cipher != null && !cipher.isEmpty()) {
                    e.setValue(ENCRYPTED_MARKER + cipher);
                }
            } catch (RuntimeException ex) {
                log.warn("[PiiSlot] encrypt failed for slot '{}': {}", name, ex.getMessage());
            }
        }
        return variables;
    }

    /**
     * Decrypts every {@code pii_*} slot in the map IN PLACE — only values
     * carrying the {@link #ENCRYPTED_MARKER} envelope are touched. Plain
     * values are left untouched so callers that mix legacy (pre-T61) plain
     * values with new envelopes stay working.
     */
    public Map<String, String> decryptInPlace(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return variables;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            String value = e.getValue();
            if (!isEncrypted(value)) continue;
            try {
                String plain = cryptoService.decrypt(value.substring(ENCRYPTED_MARKER.length()));
                if (plain != null) {
                    e.setValue(plain);
                }
            } catch (RuntimeException ex) {
                log.warn("[PiiSlot] decrypt failed for slot '{}': {}",
                        e.getKey(), ex.getMessage());
            }
        }
        return variables;
    }

    /**
     * Returns a defensive copy of {@code variables} where every
     * {@code pii_*} slot value is replaced by {@link #REDACTED_PLACEHOLDER}.
     * Used by every log statement / analytics writer that would otherwise
     * leak PII into log aggregators / event stores.
     */
    public static Map<String, String> redact(Map<String, String> variables) {
        if (variables == null) return null;
        Map<String, String> out = new LinkedHashMap<>(variables.size());
        for (Map.Entry<String, String> e : variables.entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (isPiiSlot(name) && value != null && !value.isEmpty()) {
                out.put(name, REDACTED_PLACEHOLDER);
            } else {
                out.put(name, value);
            }
        }
        return out;
    }

    /**
     * Returns a redacted view of {@code value} when {@code slotName} is a
     * PII slot — for one-off log statements that print a single value
     * rather than the whole map.
     */
    public static String redactValue(String slotName, String value) {
        return isPiiSlot(slotName) && value != null && !value.isEmpty()
                ? REDACTED_PLACEHOLDER : value;
    }

    /**
     * PII slot retention TTL in hours. {@code 0} or negative disables the
     * scheduled cleanup. Read from Global Settings; default 24h.
     */
    public int getTtlHours() {
        return globalSettings.getPiiSlotTtlHours();
    }
}
