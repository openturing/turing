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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.viglet.turing.persistence.model.agent.TurChatWebhook;
import com.viglet.turing.persistence.repository.agent.TurChatWebhookRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T62 / §VII.6.c — outbound webhook plumbing. Two responsibilities:
 *
 * <ol>
 *   <li><b>Catalog</b> — encrypt/decrypt the {@code authHeader} secret around
 *   the {@link TurChatWebhookRepository} so callers (the admin API, the
 *   dispatcher, the handoff service) never touch ciphertext directly.</li>
 *   <li><b>Dispatch</b> — POST a JSON payload (conversation transcript + slot
 *   snapshot) to the webhook's {@code targetUrl}, attaching the decrypted
 *   {@code Authorization} header. Used both as a handoff channel
 *   ({@link #dispatchHandoff}) and on automatic slot-write triggers
 *   ({@link #dispatchSlotWrite}).</li>
 * </ol>
 *
 * <p>The HTTP call is bounded by a 10s timeout; failures are surfaced for the
 * handoff path (the visitor clicked "send to CRM" and wants confirmation) but
 * swallowed for the slot-write path (a flaky CRM must never break a chat
 * turn).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatWebhookService {

    /** {@code event} field value for an explicit handoff POST. */
    public static final String EVENT_HANDOFF = "handoff";
    /** {@code event} field value for an automatic slot-write POST. */
    public static final String EVENT_SLOT_WRITE = "slot_write";
    /** {@code event} field value for a deterministic {@code webhook} chat-flow node POST. */
    public static final String EVENT_FLOW_NODE = "flow_node";

    /** Matches {@code {{slotName}}} placeholders in a payload template. */
    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {};

    /** Wildcard {@code slotTrigger} — fire on any slot write. */
    public static final String TRIGGER_ANY = "*";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final TurChatWebhookRepository repository;
    private final TurSecretCryptoService cryptoService;
    private final RestClient restClient;

    public TurChatWebhookService(TurChatWebhookRepository repository,
            TurSecretCryptoService cryptoService) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(TIMEOUT);
        rf.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(rf).build();
    }

    /** Outcome of a single dispatch — {@code url} is a {@code webhook://name} marker on success. */
    public record DispatchOutcome(boolean ok, String url, String error) {
        public static DispatchOutcome failure(String message) {
            return new DispatchOutcome(false, null, message);
        }
    }

    // ---- catalog (encrypt/decrypt around the repository) --------------------

    public List<TurChatWebhook> findAll() {
        return repository.findAll();
    }

    /**
     * Enabled webhooks that subscribe to slot writes (non-blank
     * {@code slotTrigger}). The dispatcher consults this on every slot event;
     * the underlying {@code findAll} is cached so the hot path stays off the
     * database.
     */
    public List<TurChatWebhook> findSlotTriggerWebhooks() {
        List<TurChatWebhook> result = new ArrayList<>();
        for (TurChatWebhook webhook : repository.findAll()) {
            if (webhook.isEnabled()
                    && webhook.getSlotTrigger() != null
                    && !webhook.getSlotTrigger().isBlank()) {
                result.add(webhook);
            }
        }
        return result;
    }

    /**
     * Persists a webhook, encrypting the plaintext {@code authHeader} the
     * caller set on the entity. When {@code keepExistingSecret} is true and
     * the entity's {@code authHeader} is blank, the previously-stored
     * ciphertext is preserved (admin edited the form without re-typing the
     * secret).
     */
    public TurChatWebhook save(TurChatWebhook webhook, String plaintextAuthHeader,
            boolean keepExistingSecret, String existingCipher) {
        if (plaintextAuthHeader != null && !plaintextAuthHeader.isBlank()) {
            webhook.setAuthHeader(cryptoService.encrypt(plaintextAuthHeader.trim()));
        } else if (keepExistingSecret) {
            webhook.setAuthHeader(existingCipher);
        } else {
            webhook.setAuthHeader(null);
        }
        return repository.save(webhook);
    }

    // ---- dispatch -----------------------------------------------------------

    /**
     * Explicit handoff: resolve the webhook by name, POST the rendered
     * transcript + filtered slots, and report success/failure so the handoff
     * endpoint can tell the visitor whether the lead reached the CRM.
     */
    public DispatchOutcome dispatchHandoff(String webhookName, String conversationId,
            String transcript, Map<String, String> slots) {
        return resolveAndDispatch(webhookName, conversationId, EVENT_HANDOFF, transcript, slots);
    }

    /**
     * Deterministic dispatch from a {@code webhook} chat-flow node (resolves
     * by name, no transcript). Returns the outcome so the engine can route the
     * {@code failure} edge when the author opted into {@code continueOnFailure}.
     */
    public DispatchOutcome dispatchFromFlowNode(String webhookName, String conversationId,
            Map<String, String> slots) {
        return resolveAndDispatch(webhookName, conversationId, EVENT_FLOW_NODE, null, slots);
    }

    /** Shared resolve-by-name + POST + outcome path for handoff and flow-node dispatch. */
    private DispatchOutcome resolveAndDispatch(String webhookName, String conversationId,
            String event, String transcript, Map<String, String> slots) {
        if (webhookName == null || webhookName.isBlank()) {
            return DispatchOutcome.failure("webhook name (destination) is required");
        }
        TurChatWebhook webhook = repository.findByName(webhookName.trim()).orElse(null);
        if (webhook == null) {
            return DispatchOutcome.failure("Unknown webhook: " + webhookName);
        }
        if (!webhook.isEnabled()) {
            return DispatchOutcome.failure("Webhook is disabled: " + webhookName);
        }
        Map<String, String> filtered = applyWhitelist(webhook.getIncludeSlots(), slots);
        Map<String, Object> envelope = buildPayload(event, conversationId, null,
                transcript, filtered, webhook.getName());
        try {
            post(webhook, resolveBody(webhook, envelope, filtered));
            log.info("[Webhook] {} conv={} webhook={} -> {}",
                    event, conversationId, webhook.getName(), webhook.getTargetUrl());
            return new DispatchOutcome(true, "webhook://" + webhook.getName(), null);
        } catch (Exception e) {
            log.warn("[Webhook] {} POST failed conv={} webhook={}: {}",
                    event, conversationId, webhook.getName(), e.getMessage());
            return DispatchOutcome.failure("Webhook delivery failed: " + e.getMessage());
        }
    }

    /**
     * Automatic slot-write fire. Best-effort: a failing CRM endpoint logs but
     * never propagates, so the chat turn that produced the slot write is never
     * disturbed. Called off the dispatcher's executor, not the write path.
     */
    public void dispatchSlotWrite(TurChatWebhook webhook, String conversationId,
            String slotName, Map<String, String> slots) {
        if (webhook == null || conversationId == null) {
            return;
        }
        Map<String, String> filtered = applyWhitelist(webhook.getIncludeSlots(), slots);
        Map<String, Object> payload = buildPayload(EVENT_SLOT_WRITE, conversationId, slotName,
                null, filtered, webhook.getName());
        try {
            post(webhook, resolveBody(webhook, payload, filtered));
            log.info("[Webhook] slot-write conv={} slot='{}' webhook={} -> {}",
                    conversationId, slotName, webhook.getName(), webhook.getTargetUrl());
        } catch (Exception e) {
            log.warn("[Webhook] slot-write POST failed conv={} slot='{}' webhook={}: {}",
                    conversationId, slotName, webhook.getName(), e.getMessage());
        }
    }

    /**
     * Whether {@code webhook} should fire for a write to {@code slotName}.
     * Matches the wildcard {@code *} or an exact (case-insensitive) slot name.
     */
    public static boolean matchesSlot(TurChatWebhook webhook, String slotName) {
        if (webhook == null || !webhook.isEnabled() || slotName == null) {
            return false;
        }
        String trigger = webhook.getSlotTrigger();
        if (trigger == null || trigger.isBlank()) {
            return false;
        }
        trigger = trigger.trim();
        return TRIGGER_ANY.equals(trigger) || trigger.equalsIgnoreCase(slotName.trim());
    }

    // ---- internals ----------------------------------------------------------

    Map<String, Object> buildPayload(String event, String conversationId, String slotName,
            String transcript, Map<String, String> slots, String webhookName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("webhook", webhookName);
        payload.put("conversationId", conversationId);
        if (slotName != null) {
            payload.put("slot", slotName);
        }
        if (transcript != null) {
            payload.put("transcript", transcript);
        }
        payload.put("slots", slots == null ? Map.of() : slots);
        return payload;
    }

    /**
     * Restricts {@code slots} to the comma-separated {@code includeSlots}
     * whitelist. Blank whitelist = pass everything through unchanged.
     */
    static Map<String, String> applyWhitelist(String includeSlots, Map<String, String> slots) {
        if (slots == null || slots.isEmpty()) {
            return Map.of();
        }
        if (includeSlots == null || includeSlots.isBlank()) {
            return slots;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : includeSlots.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            String value = slots.get(name);
            if (value != null) {
                result.put(name, value);
            }
        }
        return result;
    }

    /**
     * Picks the request body: a rendered JSON {@link String} when the webhook
     * carries a non-blank {@code payloadTemplate} (CRM-shaped), otherwise the
     * default {@code envelope} {@link Map}. A rendered String is sent verbatim
     * with {@code application/json} (Spring's String converter writes raw
     * bytes — no double-encoding); the Map is serialized by the Jackson
     * converter.
     */
    Object resolveBody(TurChatWebhook webhook, Map<String, Object> envelope,
            Map<String, String> slots) {
        String template = webhook.getPayloadTemplate();
        if (template == null || template.isBlank()) {
            return envelope;
        }
        return renderTemplate(template, slots);
    }

    /**
     * Substitutes {@code {{slot}}} placeholders in {@code template} with the
     * JSON-escaped slot value (unknown/blank slots → empty string). Because
     * values are escaped for a JSON string context, a slot containing quotes
     * or newlines can't corrupt the resulting JSON.
     */
    static String renderTemplate(String template, Map<String, String> slots) {
        Map<String, String> safe = slots == null ? Map.of() : slots;
        java.util.regex.Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = safe.getOrDefault(m.group(1), "");
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(jsonEscape(value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Escapes a value for embedding inside a JSON string literal (no surrounding quotes). */
    static String jsonEscape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private void post(TurChatWebhook webhook, Object body) {
        HttpMethod method = resolveMethod(webhook.getHttpMethod());
        var spec = restClient.method(method)
                .uri(webhook.getTargetUrl())
                .header("Content-Type", "application/json");
        // Custom headers first so we can detect an admin-supplied Authorization
        // and let it win over the encrypted authHeader fallback.
        Map<String, String> headers = parseHeaders(webhook.getHeadersJson());
        boolean customAuth = false;
        for (Map.Entry<String, String> h : headers.entrySet()) {
            if (h.getKey() == null || h.getKey().isBlank()) {
                continue;
            }
            spec = spec.header(h.getKey(), h.getValue() == null ? "" : h.getValue());
            if ("Authorization".equalsIgnoreCase(h.getKey())) {
                customAuth = true;
            }
        }
        if (!customAuth) {
            String auth = decryptAuth(webhook.getAuthHeader());
            if (auth != null && !auth.isBlank()) {
                spec = spec.header("Authorization", auth);
            }
        }
        // Body-bearing methods send the rendered payload (Map → Jackson;
        // rendered template String → raw bytes). GET/DELETE carry no body. We
        // don't care about the response body — just that the call returns.
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            spec.body(body).retrieve().toBodilessEntity();
        } else {
            spec.retrieve().toBodilessEntity();
        }
    }

    private static HttpMethod resolveMethod(String method) {
        if (method == null || method.isBlank()) {
            return HttpMethod.POST;
        }
        try {
            return HttpMethod.valueOf(method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return HttpMethod.POST;
        }
    }

    /** Parses the headersJson object into a map; blank/invalid → empty (logged). */
    Map<String, String> parseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = OBJECT_MAPPER.readValue(headersJson, HEADERS_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            log.warn("[Webhook] invalid headersJson, ignoring custom headers: {}", e.getMessage());
            return Map.of();
        }
    }

    private String decryptAuth(String cipher) {
        if (cipher == null || cipher.isBlank()) {
            return null;
        }
        try {
            return cryptoService.decrypt(cipher);
        } catch (Exception e) {
            // Key rotation / corrupt ciphertext — never break a dispatch over
            // it; send the call unauthenticated and let the receiver reject.
            log.warn("[Webhook] could not decrypt auth header (sending unauthenticated): {}",
                    e.getMessage());
            return null;
        }
    }

    /** Normalises a user-supplied trigger to the stored form (lower-cased, trimmed). */
    public static String normalizeTrigger(String trigger) {
        if (trigger == null) {
            return null;
        }
        String trimmed = trigger.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return TRIGGER_ANY.equals(trimmed) ? TRIGGER_ANY : trimmed.toLowerCase(Locale.ROOT);
    }
}
