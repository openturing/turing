/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.core.webhook.VigletWebhookDispatcher;
import com.viglet.core.webhook.VigletWebhookRequest;
import com.viglet.core.webhook.VigletWebhookRetryPolicy;
import com.viglet.turing.email.TurEmailMessage;
import com.viglet.turing.email.TurEmailService;
import com.viglet.turing.service.chatslots.TurChatWebhookService;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T119 / §IX.5.a — dispatches the notification raised by a {@code humanApproval}
 * chat-flow node across the three supported channels. Each call carries the
 * already-rendered approval prompt and the absolute resume URL the approver
 * clicks to resolve it:
 *
 * <ul>
 *   <li><b>email</b> — {@code target} is the recipient address; the body is the
 *       prompt plus a link, sent through {@link TurEmailService} with the
 *       sender configured in Global Settings.</li>
 *   <li><b>slack</b> — {@code target} is a Slack incoming-webhook URL; a
 *       {@code {"text": …}} payload is POSTed through the shared
 *       {@link VigletWebhookDispatcher}.</li>
 *   <li><b>webhook</b> — {@code target} names an admin-declared
 *       {@link com.viglet.turing.persistence.model.agent.TurChatWebhook}; it is
 *       fired through {@link TurChatWebhookService#dispatchFromFlowNode} with
 *       the resume URL and prompt added to the slot map so the webhook's own
 *       payload template can reference them.</li>
 * </ul>
 *
 * <p>Delivery is best-effort: a failure is logged but never propagated, so a
 * flaky channel can't break the chat turn that parked the conversation. The
 * pending-approval record still exists and the resume URL still works.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurHumanApprovalNotifier {

    /** Slot keys the webhook channel injects so a payload template can use them. */
    public static final String SLOT_RESUME_URL = "__approvalResumeUrl";
    public static final String SLOT_PROMPT = "__approvalPrompt";

    private static final VigletWebhookRetryPolicy RETRY_POLICY =
            new VigletWebhookRetryPolicy(3, Duration.ofSeconds(10), Duration.ofSeconds(2));

    private final TurEmailService emailService;
    private final TurChatWebhookService webhookService;
    private final TurGlobalSettingsService globalSettingsService;
    private final VigletWebhookDispatcher webhookDispatcher;

    public TurHumanApprovalNotifier(TurEmailService emailService,
            TurChatWebhookService webhookService,
            TurGlobalSettingsService globalSettingsService,
            VigletWebhookDispatcher webhookDispatcher) {
        this.emailService = emailService;
        this.webhookService = webhookService;
        this.globalSettingsService = globalSettingsService;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * Fires the notification. {@code channel} is matched case-insensitively;
     * an unknown/blank channel is logged and skipped (the approval is still
     * resolvable through its resume URL).
     */
    public void notify(String channel, String target, String prompt, String resumeUrl,
            String conversationId, Map<String, String> slots) {
        String ch = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        if (target == null || target.isBlank()) {
            log.warn("[HumanApproval] channel '{}' has no target — skipping notification (conv '{}')",
                    ch, conversationId);
            return;
        }
        try {
            switch (ch) {
                case "email" -> sendEmail(target, prompt, resumeUrl);
                case "slack" -> sendSlack(target, prompt, resumeUrl);
                case "webhook" -> sendWebhook(target, prompt, resumeUrl, conversationId, slots);
                default -> log.warn("[HumanApproval] unknown channel '{}' — skipping notification (conv '{}')",
                        channel, conversationId);
            }
        } catch (RuntimeException e) {
            log.warn("[HumanApproval] notification on channel '{}' failed for conv '{}': {}",
                    ch, conversationId, e.getMessage());
        }
    }

    private void sendEmail(String recipient, String prompt, String resumeUrl) {
        String senderEmail = globalSettingsService.getSenderEmail();
        String senderName = globalSettingsService.getSenderName();
        if (senderEmail == null || senderEmail.isBlank()) {
            log.warn("[HumanApproval] sender e-mail not configured in Global Settings — skipping e-mail");
            return;
        }
        String html = "<p>" + escapeHtml(prompt) + "</p>"
                + "<p><a href=\"" + escapeHtml(resumeUrl) + "\">Review &amp; respond</a></p>"
                + "<p style=\"color:#64748b;font-size:12px\">Viglet Turing ES</p>";
        TurEmailMessage message = TurEmailMessage.builder()
                .senderEmail(senderEmail)
                .senderName(senderName == null || senderName.isBlank() ? senderEmail : senderName)
                .recipientEmail(recipient)
                .subject("Approval required — Viglet Turing ES")
                .htmlContent(html)
                .build();
        emailService.sendEmail(message);
    }

    private void sendSlack(String slackWebhookUrl, String prompt, String resumeUrl) {
        String text = (prompt == null ? "Approval required" : prompt) + "\n" + resumeUrl;
        String body = "{\"text\":\"" + jsonEscape(text) + "\"}";
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .method("POST")
                .url(slackWebhookUrl)
                .contentType("application/json")
                .headers(Map.of())
                .body(body.getBytes(StandardCharsets.UTF_8))
                .build();
        webhookDispatcher.deliver(request, RETRY_POLICY);
    }

    private void sendWebhook(String webhookName, String prompt, String resumeUrl,
            String conversationId, Map<String, String> slots) {
        Map<String, String> augmented = new LinkedHashMap<>(slots == null ? Map.of() : slots);
        augmented.put(SLOT_RESUME_URL, resumeUrl);
        augmented.put(SLOT_PROMPT, prompt == null ? "" : prompt);
        TurChatWebhookService.DispatchOutcome outcome =
                webhookService.dispatchFromFlowNode(webhookName, conversationId, augmented);
        if (!outcome.ok()) {
            log.warn("[HumanApproval] webhook '{}' delivery failed for conv '{}': {}",
                    webhookName, conversationId, outcome.error());
        }
    }

    /** Minimal JSON-string escape for the Slack {@code text} payload. */
    static String jsonEscape(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
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

    /** Minimal HTML escape for the e-mail body. */
    static String escapeHtml(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
