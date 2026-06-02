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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.agent.TurChatWebhook;
import com.viglet.turing.persistence.repository.agent.TurChatWebhookRepository;
import com.viglet.turing.service.chatslots.TurChatWebhookService.DispatchOutcome;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Pure-Mockito unit tests for {@link TurChatWebhookService} — the catalog
 * encrypt/decrypt seam and the dispatch pre-flight branches (the actual HTTP
 * POST is exercised by integration coverage, not here). Plus the stateless
 * helpers ({@code buildPayload}, {@code applyWhitelist}, {@code matchesSlot},
 * {@code normalizeTrigger}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatWebhookServiceTest {

    @Mock
    private TurChatWebhookRepository repository;
    @Mock
    private TurSecretCryptoService cryptoService;
    @InjectMocks
    private TurChatWebhookService service;

    private static TurChatWebhook webhook(String name, String trigger, boolean enabled) {
        TurChatWebhook w = new TurChatWebhook();
        w.setName(name);
        w.setTargetUrl("https://crm.example.com/hook");
        w.setSlotTrigger(trigger);
        w.setEnabled(enabled);
        return w;
    }

    // ---- normalizeTrigger ---------------------------------------------------

    @Test
    void normalizeTriggerBlankBecomesNull() {
        assertNull(TurChatWebhookService.normalizeTrigger(null));
        assertNull(TurChatWebhookService.normalizeTrigger("   "));
    }

    @Test
    void normalizeTriggerWildcardPreserved() {
        assertEquals("*", TurChatWebhookService.normalizeTrigger("*"));
    }

    @Test
    void normalizeTriggerLowercasesSlotName() {
        assertEquals("email", TurChatWebhookService.normalizeTrigger("  EMAIL "));
    }

    // ---- matchesSlot --------------------------------------------------------

    @Test
    void matchesSlotWildcardFiresForAny() {
        assertTrue(TurChatWebhookService.matchesSlot(webhook("w", "*", true), "anything"));
    }

    @Test
    void matchesSlotExactCaseInsensitive() {
        assertTrue(TurChatWebhookService.matchesSlot(webhook("w", "email", true), "EMAIL"));
        assertFalse(TurChatWebhookService.matchesSlot(webhook("w", "email", true), "name"));
    }

    @Test
    void matchesSlotDisabledOrBlankTriggerNeverFires() {
        assertFalse(TurChatWebhookService.matchesSlot(webhook("w", "*", false), "email"));
        assertFalse(TurChatWebhookService.matchesSlot(webhook("w", "", true), "email"));
        assertFalse(TurChatWebhookService.matchesSlot(webhook("w", null, true), "email"));
        assertFalse(TurChatWebhookService.matchesSlot(null, "email"));
        assertFalse(TurChatWebhookService.matchesSlot(webhook("w", "*", true), null));
    }

    // ---- applyWhitelist -----------------------------------------------------

    @Test
    void applyWhitelistBlankPassesThrough() {
        Map<String, String> slots = Map.of("name", "Ada", "email", "ada@x.com");
        assertSame(slots, TurChatWebhookService.applyWhitelist("", slots));
        assertSame(slots, TurChatWebhookService.applyWhitelist(null, slots));
    }

    @Test
    void applyWhitelistFiltersAndSkipsMissing() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("name", "Ada");
        slots.put("email", "ada@x.com");
        slots.put("secret", "shh");
        Map<String, String> result = TurChatWebhookService.applyWhitelist("name, email, absent", slots);
        assertEquals(2, result.size());
        assertEquals("Ada", result.get("name"));
        assertEquals("ada@x.com", result.get("email"));
        assertFalse(result.containsKey("secret"));
    }

    @Test
    void applyWhitelistEmptySlotsYieldsEmpty() {
        assertTrue(TurChatWebhookService.applyWhitelist("name", Map.of()).isEmpty());
    }

    // ---- buildPayload -------------------------------------------------------

    @Test
    void buildPayloadHandoffShape() {
        Map<String, Object> payload = service.buildPayload(
                TurChatWebhookService.EVENT_HANDOFF, "conv-1", null,
                "• name: Ada", Map.of("name", "Ada"), "push_crm");
        assertEquals("handoff", payload.get("event"));
        assertEquals("push_crm", payload.get("webhook"));
        assertEquals("conv-1", payload.get("conversationId"));
        assertEquals("• name: Ada", payload.get("transcript"));
        assertFalse(payload.containsKey("slot"));
        assertEquals(Map.of("name", "Ada"), payload.get("slots"));
    }

    @Test
    void buildPayloadSlotWriteCarriesSlotNameNoTranscript() {
        Map<String, Object> payload = service.buildPayload(
                TurChatWebhookService.EVENT_SLOT_WRITE, "conv-2", "email",
                null, Map.of("email", "ada@x.com"), "push_crm");
        assertEquals("slot_write", payload.get("event"));
        assertEquals("email", payload.get("slot"));
        assertFalse(payload.containsKey("transcript"));
        assertEquals(Map.of("email", "ada@x.com"), payload.get("slots"));
    }

    // ---- save (encrypt seam) ------------------------------------------------

    @Test
    void saveEncryptsPlaintextAuthHeader() {
        TurChatWebhook entity = webhook("w", null, true);
        when(cryptoService.encrypt("Bearer xyz")).thenReturn("CIPHER");
        when(repository.save(entity)).thenReturn(entity);

        service.save(entity, "Bearer xyz", false, null);

        assertEquals("CIPHER", entity.getAuthHeader());
        verify(repository).save(entity);
    }

    @Test
    void saveKeepsExistingSecretWhenBlankAndKeepRequested() {
        TurChatWebhook entity = webhook("w", null, true);
        when(repository.save(entity)).thenReturn(entity);

        service.save(entity, "  ", true, "OLD_CIPHER");

        assertEquals("OLD_CIPHER", entity.getAuthHeader());
        verify(cryptoService, never()).encrypt(anyString());
    }

    @Test
    void saveClearsSecretWhenBlankAndNoKeep() {
        TurChatWebhook entity = webhook("w", null, true);
        entity.setAuthHeader("STALE");
        when(repository.save(entity)).thenReturn(entity);

        service.save(entity, null, false, null);

        assertNull(entity.getAuthHeader());
    }

    // ---- dispatchHandoff pre-flight failures --------------------------------

    @Test
    void dispatchHandoffBlankNameFails() {
        DispatchOutcome outcome = service.dispatchHandoff("  ", "conv", "t", Map.of());
        assertFalse(outcome.ok());
        assertTrue(outcome.error().contains("required"));
    }

    @Test
    void dispatchHandoffUnknownWebhookFails() {
        when(repository.findByName("nope")).thenReturn(Optional.empty());
        DispatchOutcome outcome = service.dispatchHandoff("nope", "conv", "t", Map.of());
        assertFalse(outcome.ok());
        assertTrue(outcome.error().contains("Unknown webhook"));
    }

    @Test
    void dispatchHandoffDisabledWebhookFails() {
        when(repository.findByName("push_crm")).thenReturn(Optional.of(webhook("push_crm", null, false)));
        DispatchOutcome outcome = service.dispatchHandoff("push_crm", "conv", "t", Map.of());
        assertFalse(outcome.ok());
        assertTrue(outcome.error().contains("disabled"));
    }

    // ---- findSlotTriggerWebhooks -------------------------------------------

    // ---- renderTemplate / jsonEscape ---------------------------------------

    @Test
    void renderTemplateInterpolatesSlots() {
        String rendered = TurChatWebhookService.renderTemplate(
                "{\"email\":\"{{email}}\",\"name\":\"{{name}}\"}",
                Map.of("email", "ada@x.com", "name", "Ada"));
        assertEquals("{\"email\":\"ada@x.com\",\"name\":\"Ada\"}", rendered);
    }

    @Test
    void renderTemplateUnknownSlotBecomesEmpty() {
        String rendered = TurChatWebhookService.renderTemplate(
                "{\"x\":\"{{absent}}\"}", Map.of("email", "ada@x.com"));
        assertEquals("{\"x\":\"\"}", rendered);
    }

    @Test
    void renderTemplateJsonEscapesQuotesAndNewlines() {
        String rendered = TurChatWebhookService.renderTemplate(
                "{\"note\":\"{{note}}\"}", Map.of("note", "say \"hi\"\nbye"));
        // The injected value must not corrupt the JSON — quotes/newline escaped.
        assertEquals("{\"note\":\"say \\\"hi\\\"\\nbye\"}", rendered);
    }

    @Test
    void resolveBodyReturnsEnvelopeWhenNoTemplate() {
        TurChatWebhook w = webhook("w", null, true);
        Map<String, Object> envelope = Map.of("event", "handoff");
        assertSame(envelope, service.resolveBody(w, envelope, Map.of("email", "a@x.com")));
    }

    @Test
    void resolveBodyRendersTemplateWhenPresent() {
        TurChatWebhook w = webhook("w", null, true);
        w.setPayloadTemplate("{\"e\":\"{{email}}\"}");
        Object body = service.resolveBody(w, Map.of("event", "handoff"), Map.of("email", "a@x.com"));
        assertEquals("{\"e\":\"a@x.com\"}", body);
    }

    // ---- parseHeaders -------------------------------------------------------

    @Test
    void parseHeadersValidJsonObject() {
        Map<String, String> headers = service.parseHeaders("{\"X-Api-Key\":\"abc\",\"X-Source\":\"turing\"}");
        assertEquals(2, headers.size());
        assertEquals("abc", headers.get("X-Api-Key"));
    }

    @Test
    void parseHeadersBlankOrInvalidYieldsEmpty() {
        assertTrue(service.parseHeaders(null).isEmpty());
        assertTrue(service.parseHeaders("   ").isEmpty());
        assertTrue(service.parseHeaders("not json").isEmpty());
    }

    // ---- dispatchFromFlowNode pre-flight ------------------------------------

    @Test
    void dispatchFromFlowNodeUnknownWebhookFails() {
        when(repository.findByName("nope")).thenReturn(Optional.empty());
        DispatchOutcome outcome = service.dispatchFromFlowNode("nope", "conv", Map.of());
        assertFalse(outcome.ok());
        assertTrue(outcome.error().contains("Unknown webhook"));
    }

    @Test
    void findSlotTriggerWebhooksFiltersDisabledAndBlankTrigger() {
        lenient().when(repository.findAll()).thenReturn(java.util.List.of(
                webhook("a", "email", true),     // kept
                webhook("b", "*", true),         // kept
                webhook("c", "email", false),    // disabled
                webhook("d", null, true),        // handoff-only (blank trigger)
                webhook("e", "  ", true)));       // blank trigger
        var result = service.findSlotTriggerWebhooks();
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(w -> "a".equals(w.getName())));
        assertTrue(result.stream().anyMatch(w -> "b".equals(w.getName())));
    }
}
