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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds channel-specific handoff links from the captured chat slots —
 * the bridge between an AI-driven conversation and a human consultant.
 * The visitor clicks a button in the React UI, the SDK calls the
 * handoff endpoint, the service composes a URL whose payload carries
 * the conversation context (name, cargo, area, objetivo, etc.) so the
 * consultant doesn't re-ask the same questions.
 *
 * <p>Generic over channels: V1 ships WhatsApp via {@code wa.me} universal
 * links, but the abstraction lets future channels (Email mailto, SMS
 * "sms:", Slack webhook, Telegram t.me) plug in by adding a new
 * {@link Channel} case + builder. The slot transcript is identical
 * across channels — only the URL envelope differs.
 *
 * <p>Side effects: writes a tracking slot {@code handoff_status} =
 * {@code "{channel}_requested"} so the React UI can hide the CTA once
 * clicked + the analytics dashboard can compare handoff rates across
 * A/B variants. The slot write goes through
 * {@link TurChatFlowEngineService#writeSlot} so the SSE channel updates
 * subscribers immediately.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurChatHandoffService {

    /** WhatsApp's universal link domain — works on web + mobile, redirects to native app when installed. */
    private static final String WHATSAPP_LINK_BASE = "https://wa.me/";

    /** Tracking slot written after a handoff so the UI/analytics can react. */
    private static final String HANDOFF_STATUS_SLOT = "handoff_status";

    /** Default ordering of slots in the transcript when caller doesn't specify. Empty list = include all. */
    private static final List<String> DEFAULT_INCLUDED_SLOTS = List.of();

    /**
     * Supported handoff channels. Add a case + matching builder in
     * {@link #buildLink} to extend.
     */
    public enum Channel {
        /** WhatsApp universal link — opens chat with the consultant's phone. */
        WHATSAPP,
        /** {@code mailto:} link — opens user's email client. */
        EMAIL,
        /** {@code sms:} link — opens user's SMS app. */
        SMS,
        /**
         * Telegram {@code t.me} link — opens chat with the consultant's
         * username/phone. {@code destination} should be the Telegram
         * username (without the {@code @}) or phone number with country code.
         */
        TELEGRAM,
        /**
         * Slack deep link — opens the consultant's DM in the Slack app.
         * {@code destination} format: {@code "{teamId}/{userId}"} (both
         * Slack opaque ids, e.g. {@code "T01ABCDEF/U05XYZ123"}). Falls
         * back to the web client if Slack isn't installed.
         */
        SLACK,
        /**
         * Server-side webhook (T62). Unlike the deep-link channels above,
         * this one does not produce a URL the browser opens — it POSTs the
         * transcript + slot snapshot to a configured CRM endpoint
         * (Salesforce / HubSpot / Pipedrive) right here on the server.
         * {@code destination} is the admin-declared webhook name; the
         * payload + credentials come from the {@code TurChatWebhook}
         * configuration, not from the request.
         */
        WEBHOOK
    }

    private final TurChatFlowEngineService engineService;
    private final TurChatWebhookService webhookService;

    public TurChatHandoffService(TurChatFlowEngineService engineService,
            TurChatWebhookService webhookService) {
        this.engineService = engineService;
        this.webhookService = webhookService;
    }

    /**
     * One handoff URL build + side-effects. Inputs and outputs are
     * channel-agnostic — {@link #url} is the absolute string the client
     * should redirect/window-open; {@link #transcript} is the rendered
     * key-value block embedded inside that URL's message body, returned
     * verbatim so the UI can preview what the consultant will see.
     */
    public record HandoffResult(String url, String transcript, int slotsIncluded, String error) {
        public static HandoffResult failure(String message) {
            return new HandoffResult(null, null, 0, message);
        }
    }

    public record HandoffRequest(
            Channel channel,
            String destination,
            String intro,
            List<String> slotsToInclude) {
    }

    /**
     * Composes the handoff URL for the conversation. Reads the current
     * slot snapshot via the engine (same data the dashboard sees),
     * filters to the requested subset (or includes all when the caller
     * leaves the list empty), encodes the body for the chosen channel,
     * and writes the {@code handoff_status} tracking slot.
     */
    public HandoffResult buildLink(String conversationId, HandoffRequest request) {
        if (conversationId == null || conversationId.isBlank()) {
            return HandoffResult.failure("conversationId is required");
        }
        if (request == null || request.channel() == null) {
            return HandoffResult.failure("channel is required");
        }
        if (request.destination() == null || request.destination().isBlank()) {
            return HandoffResult.failure("destination is required");
        }

        TurChatSessionSlotsDto snapshot = engineService.listSlotsForConversation(conversationId);
        Map<String, String> filtered = filterSlots(snapshot.slots(),
                request.slotsToInclude() == null ? DEFAULT_INCLUDED_SLOTS : request.slotsToInclude());
        String transcript = renderTranscript(request.intro(), filtered);

        // Webhook is server-side: POST the transcript to the configured CRM
        // endpoint instead of handing the browser a deep link. A delivery
        // failure is reported back to the visitor (they explicitly asked to
        // be sent to a human), and the tracking slot is only written once the
        // POST succeeds.
        if (request.channel() == Channel.WEBHOOK) {
            TurChatWebhookService.DispatchOutcome outcome = webhookService.dispatchHandoff(
                    request.destination(), conversationId, transcript, filtered);
            if (!outcome.ok()) {
                return HandoffResult.failure(outcome.error());
            }
            engineService.writeSlot(conversationId, HANDOFF_STATUS_SLOT, "webhook_requested");
            log.info("[Handoff] conv={} channel=WEBHOOK slots-included={} -> {}",
                    conversationId, filtered.size(), outcome.url());
            return new HandoffResult(outcome.url(), transcript, filtered.size(), null);
        }

        String url = switch (request.channel()) {
            case WHATSAPP -> buildWhatsAppUrl(request.destination(), transcript);
            case EMAIL    -> buildMailtoUrl(request.destination(), transcript);
            case SMS      -> buildSmsUrl(request.destination(), transcript);
            case TELEGRAM -> buildTelegramUrl(request.destination(), transcript);
            case SLACK    -> buildSlackUrl(request.destination(), transcript);
            case WEBHOOK  -> throw new IllegalStateException("WEBHOOK handled before switch");
        };

        // Tracking slot — lower-cased channel name + "_requested" suffix
        // so the React UI can show a "Handoff em andamento" state and the
        // analytics dashboard can group by this value.
        String statusValue = request.channel().name().toLowerCase(Locale.ROOT) + "_requested";
        engineService.writeSlot(conversationId, HANDOFF_STATUS_SLOT, statusValue);

        log.info("[Handoff] conv={} channel={} slots-included={} url-length={}",
                conversationId, request.channel(), filtered.size(), url.length());
        return new HandoffResult(url, transcript, filtered.size(), null);
    }

    private static Map<String, String> filterSlots(Map<String, String> allSlots,
            List<String> requested) {
        if (allSlots == null || allSlots.isEmpty()) return Map.of();
        if (requested == null || requested.isEmpty()) {
            return defaultSlots(allSlots);
        }
        return requestedSlots(allSlots, requested);
    }

    /**
     * Default safe set: skip internal-flag slots whose values are meaningless to
     * a human consultant (cta_visible, color, …). Keeps human-readable values; if
     * the caller wants the rest they can pass an explicit slot list.
     */
    private static Map<String, String> defaultSlots(Map<String, String> allSlots) {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : allSlots.entrySet()) {
            String name = entry.getKey();
            if (isInternalFlag(name)) continue;
            String value = entry.getValue();
            if (value != null && !value.isBlank()) defaults.put(name, value);
        }
        return defaults;
    }

    /** Keeps only the requested slots that carry a non-blank value. */
    private static Map<String, String> requestedSlots(Map<String, String> allSlots,
            List<String> requested) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : requested) {
            if (name == null || name.isBlank()) continue;
            String value = allSlots.get(name.trim());
            if (value != null && !value.isBlank()) result.put(name.trim(), value);
        }
        return result;
    }

    /**
     * Heuristic — internal/control slots are not transcript-worthy.
     * Right list will grow as more bot-internal flags appear; centralised
     * here so the rule stays consistent across channels.
     */
    private static boolean isInternalFlag(String slotName) {
        return "cta_visible".equals(slotName)
                || "color".equals(slotName)
                || "career_path".equals(slotName)
                || "programas_match".equals(slotName)
                || "share_url".equals(slotName)
                || "welcome_personalized".equals(slotName)
                || "stat_pitch".equals(slotName)
                || "final_pitch".equals(slotName)
                || "lead_status".equals(slotName)
                || HANDOFF_STATUS_SLOT.equals(slotName);
    }

    private static String renderTranscript(String intro, Map<String, String> slots) {
        StringBuilder sb = new StringBuilder();
        if (intro != null && !intro.isBlank()) {
            sb.append(intro.trim()).append("\n\n");
        }
        for (Map.Entry<String, String> entry : slots.entrySet()) {
            sb.append("• ").append(entry.getKey().replace('_', ' ')).append(": ");
            sb.append(entry.getValue()).append('\n');
        }
        return sb.toString().trim();
    }

    private static String buildWhatsAppUrl(String phone, String body) {
        // Strip non-digits — WhatsApp's wa.me expects digits-only (with
        // country code). Tolerant of "+55 11 99999-9999" / "(11) 99999-9999".
        String digits = phone.replaceAll("\\D", "");
        return WHATSAPP_LINK_BASE + digits + "?text=" + urlEncode(body);
    }

    private static String buildMailtoUrl(String emailAddress, String body) {
        // RFC 6068 mailto: addresses are encoded loosely — the subject +
        // body are query-string-encoded.
        return "mailto:" + emailAddress
                + "?subject=" + urlEncode("Continuação do atendimento")
                + "&body=" + urlEncode(body);
    }

    private static String buildSmsUrl(String phone, String body) {
        // sms: is the iOS/Android scheme. iOS uses "&body=" while Android
        // historically used "?body=" — modern iOS + Android both accept
        // "?body=" as of 2023, so we standardize on that.
        String digits = phone.replaceAll("[^0-9+]", "");
        return "sms:" + digits + "?body=" + urlEncode(body);
    }

    /**
     * Telegram universal link. Accepts either {@code @username} (strip the
     * @) or a digits-only phone number with country code. Telegram's
     * {@code t.me/{username}?text=...} works on web + mobile, redirects
     * to the native Telegram app when installed.
     */
    private static String buildTelegramUrl(String destination, String body) {
        String clean = destination.trim();
        if (clean.startsWith("@")) clean = clean.substring(1);
        // Phones get sent to t.me/+digits; usernames go to t.me/username.
        if (clean.matches("\\+?[0-9 ()\\-]+")) {
            clean = "+" + clean.replaceAll("\\D", "");
        }
        return "https://t.me/" + clean + "?text=" + urlEncode(body);
    }

    /**
     * Slack deep link. Expects {@code "{teamId}/{userId}"} — the team's
     * opaque id (T-prefix, e.g. {@code T01ABCDEF}) and the user's opaque
     * id (U-prefix). The {@code slack://} scheme opens the desktop/mobile
     * app; we wrap in {@code https://slack.com/app_redirect?...} so the
     * link gracefully falls back to the web client when Slack isn't
     * installed.
     */
    private static String buildSlackUrl(String destination, String body) {
        String[] parts = destination.trim().split("/", 2);
        String teamId = parts.length > 0 ? parts[0] : "";
        String userId = parts.length > 1 ? parts[1] : "";
        // app_redirect supports team+channel; for DM the channel is the
        // user id. Slack pre-fills the message draft from the `message`
        // query param.
        return "https://slack.com/app_redirect?team=" + urlEncode(teamId)
                + "&channel=" + urlEncode(userId)
                + "&message=" + urlEncode(body);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
