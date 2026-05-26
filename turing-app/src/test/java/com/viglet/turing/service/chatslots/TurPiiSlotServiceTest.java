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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Pins the T61 PII helper contract: encrypt-in-place leaves non-PII untouched
 * and idempotently skips already-enveloped values, decrypt-in-place only
 * touches values bearing the envelope marker, redact returns "[redacted]" for
 * PII keys without mutating the input, and TTL is read from Global Settings.
 */
@ExtendWith(MockitoExtension.class)
class TurPiiSlotServiceTest {

    @Mock
    private TurSecretCryptoService cryptoService;

    @Mock
    private TurGlobalSettingsService globalSettings;

    @InjectMocks
    private TurPiiSlotService service;

    @Test
    void isPiiSlotMatchesPrefix() {
        assertThat(TurPiiSlotService.isPiiSlot("pii_email")).isTrue();
        assertThat(TurPiiSlotService.isPiiSlot("pii_")).isTrue();
        assertThat(TurPiiSlotService.isPiiSlot("name")).isFalse();
        assertThat(TurPiiSlotService.isPiiSlot("Pii_email")).isFalse(); // case-sensitive
        assertThat(TurPiiSlotService.isPiiSlot(null)).isFalse();
        assertThat(TurPiiSlotService.isPiiSlot("")).isFalse();
    }

    @Test
    void isEncryptedDetectsMarker() {
        assertThat(TurPiiSlotService.isEncrypted("pii_enc:v1:xyz")).isTrue();
        assertThat(TurPiiSlotService.isEncrypted("plain")).isFalse();
        assertThat(TurPiiSlotService.isEncrypted(null)).isFalse();
        assertThat(TurPiiSlotService.isEncrypted("")).isFalse();
    }

    @Test
    void encryptInPlaceOnlyTouchesPiiSlots() {
        when(cryptoService.encrypt("alex@example.com")).thenReturn("CIPHER");
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", "Alex");
        vars.put("pii_email", "alex@example.com");

        service.encryptInPlace(vars);

        assertThat(vars).containsEntry("name", "Alex");
        assertThat(vars).containsEntry("pii_email", "pii_enc:v1:CIPHER");
        verify(cryptoService, never()).encrypt("Alex");
    }

    @Test
    void encryptInPlaceSkipsAlreadyEncrypted() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("pii_email", "pii_enc:v1:ALREADY");

        service.encryptInPlace(vars);

        assertThat(vars).containsEntry("pii_email", "pii_enc:v1:ALREADY");
        verify(cryptoService, never()).encrypt(any());
    }

    @Test
    void encryptInPlaceSkipsEmptyValue() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("pii_email", "");

        service.encryptInPlace(vars);

        assertThat(vars).containsEntry("pii_email", "");
        verify(cryptoService, never()).encrypt(any());
    }

    @Test
    void encryptInPlaceTolerantToCryptoFailures() {
        when(cryptoService.encrypt(any())).thenThrow(new RuntimeException("HSM offline"));
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("pii_email", "alex@example.com");

        service.encryptInPlace(vars);

        // Value left as-is on failure rather than nulling it out.
        assertThat(vars).containsEntry("pii_email", "alex@example.com");
    }

    @Test
    void decryptInPlaceOnlyTouchesEnvelopedValues() {
        when(cryptoService.decrypt("CIPHER")).thenReturn("alex@example.com");
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", "Alex");
        vars.put("pii_email", "pii_enc:v1:CIPHER");
        vars.put("pii_legacy", "stillPlain"); // pre-T61 row, left alone

        service.decryptInPlace(vars);

        assertThat(vars).containsEntry("name", "Alex");
        assertThat(vars).containsEntry("pii_email", "alex@example.com");
        assertThat(vars).containsEntry("pii_legacy", "stillPlain");
        verify(cryptoService, times(1)).decrypt(eq("CIPHER"));
    }

    @Test
    void decryptInPlaceTolerantToCryptoFailures() {
        when(cryptoService.decrypt("CIPHER")).thenThrow(new RuntimeException("Key rotated"));
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("pii_email", "pii_enc:v1:CIPHER");

        service.decryptInPlace(vars);

        // Envelope kept on failure — caller sees the encrypted form rather
        // than mistaking plaintext.
        assertThat(vars).containsEntry("pii_email", "pii_enc:v1:CIPHER");
    }

    @Test
    void redactReplacesPiiValuesInCopy() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", "Alex");
        vars.put("pii_email", "alex@example.com");
        vars.put("pii_cpf", "");

        Map<String, String> redacted = TurPiiSlotService.redact(vars);

        assertThat(redacted).containsEntry("name", "Alex");
        assertThat(redacted).containsEntry("pii_email", "[redacted]");
        assertThat(redacted).containsEntry("pii_cpf", ""); // empty stays empty
        // Input was not mutated.
        assertThat(vars).containsEntry("pii_email", "alex@example.com");
    }

    @Test
    void redactValueHelperReplacesPiiOnly() {
        assertThat(TurPiiSlotService.redactValue("pii_cpf", "12345678901")).isEqualTo("[redacted]");
        assertThat(TurPiiSlotService.redactValue("name", "Alex")).isEqualTo("Alex");
        assertThat(TurPiiSlotService.redactValue("pii_cpf", null)).isNull();
        assertThat(TurPiiSlotService.redactValue("pii_cpf", "")).isEmpty();
    }

    @Test
    void getTtlHoursReadsFromGlobalSettings() {
        when(globalSettings.getPiiSlotTtlHours()).thenReturn(48);
        assertThat(service.getTtlHours()).isEqualTo(48);
    }

    @Test
    void instanceRegistersOnPostConstruct() {
        service.register();
        assertThat(TurPiiSlotService.getInstance()).isSameAs(service);
        service.unregister();
        assertThat(TurPiiSlotService.getInstance()).isNull();
    }
}
