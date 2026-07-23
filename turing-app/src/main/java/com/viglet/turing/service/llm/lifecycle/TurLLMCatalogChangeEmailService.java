/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.lifecycle;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.email.TurEmailService;
import com.viglet.turing.service.llm.lifecycle.TurLLMCatalogChangeService.ChangeNotification;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import lombok.extern.slf4j.Slf4j;

/**
 * T788 / §LIII.4 (Block BE) — the optional email channel for the catalog change
 * feed. A daily scheduled job emails the configured admin recipient when the
 * public model catalog changes affect a model you have configured (a model was
 * removed, superseded, or changed). Opt-in via
 * {@code turing.model-catalog.change-feed.email-enabled} (default off) so no one
 * gets surprise mail; the in-UI banner (T788) is always on regardless.
 *
 * <p>Deduped by a signature of the notification set so an unchanged feed is not
 * re-mailed every day — only a change since the last successful send triggers a
 * new email. Sends through {@link TurEmailService}, which reads the sender /
 * recipient / API key from Global Settings and skips gracefully when they are
 * unconfigured.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLLMCatalogChangeEmailService {

    private static final String BG = "#0a0a0f";
    private static final String CARD = "#16161f";
    private static final String BORDER = "#1e1e2e";

    private final TurLLMCatalogChangeService changeService;
    private final TurEmailService emailService;

    @Value("${turing.model-catalog.change-feed.email-enabled:false}")
    private boolean emailEnabled;

    /** Signature of the last set of notifications actually emailed (dedupe guard). */
    private volatile String lastSignature = "";

    public TurLLMCatalogChangeEmailService(TurLLMCatalogChangeService changeService,
            TurEmailService emailService) {
        this.changeService = changeService;
        this.emailService = emailService;
    }

    /**
     * Daily change-feed email. No-op when disabled, when nothing in the feed
     * affects a used model, or when the same set was already emailed. Best-effort:
     * a failure is logged and the signature is left unchanged so it retries.
     */
    @Scheduled(
            initialDelayString = "${turing.model-catalog.change-feed.email-initial-delay-ms:45000}",
            fixedDelayString = "${turing.model-catalog.change-feed.email-interval-ms:86400000}")
    @SchedulerLock(name = "modelCatalogChangeFeedEmail", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    void emailChangeFeed() {
        if (!emailEnabled) {
            return;
        }
        try {
            List<ChangeNotification> notifications = changeService.notificationsForUsedModels();
            if (notifications.isEmpty()) {
                return;
            }
            String signature = signatureOf(notifications);
            if (signature.equals(lastSignature)) {
                return; // already emailed this exact set
            }
            emailService.sendEmail(
                    "Viglet Turing ES - Model catalog changes affecting your models",
                    renderHtml(notifications));
            lastSignature = signature;
            log.info("Emailed model-catalog change feed ({} notification(s))", notifications.size());
        } catch (Exception e) {
            log.warn("Model-catalog change-feed email failed: {}", e.getMessage());
        }
    }

    private static String signatureOf(List<ChangeNotification> notifications) {
        return notifications.stream()
                .map(n -> n.type() + ":" + n.vendorId() + ":" + n.modelId())
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String renderHtml(List<ChangeNotification> notifications) {
        StringBuilder rows = new StringBuilder();
        for (ChangeNotification n : notifications) {
            String extra = !n.replacementCandidates().isEmpty()
                    ? "Suggested replacement: " + String.join(", ", n.replacementCandidates())
                    : (n.detail() == null ? "" : escape(n.detail()));
            rows.append("""
                    <tr>
                      <td style="padding:10px 12px;border-bottom:1px solid %s;color:#e5e7eb;font-family:monospace;">%s / %s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid %s;color:#a5b4fc;">%s</td>
                      <td style="padding:10px 12px;border-bottom:1px solid %s;color:#9ca3af;">%s</td>
                    </tr>
                    """.formatted(BORDER, escape(n.vendorId()), escape(n.modelId()),
                    BORDER, n.type().name().toLowerCase(), BORDER, extra));
        }
        return """
                <div style="background:%s;padding:24px;font-family:Arial,Helvetica,sans-serif;">
                  <div style="max-width:640px;margin:0 auto;background:%s;border:1px solid %s;border-radius:12px;overflow:hidden;">
                    <div style="background:linear-gradient(135deg,#2563eb,#4f46e5);padding:20px 24px;">
                      <h1 style="margin:0;color:#fff;font-size:18px;">Model catalog changes</h1>
                      <p style="margin:6px 0 0;color:#e0e7ff;font-size:13px;">These catalog changes affect models you have configured in Viglet Turing ES.</p>
                    </div>
                    <table style="width:100%%;border-collapse:collapse;">%s</table>
                    <p style="padding:16px 24px;margin:0;color:#6b7280;font-size:12px;">Review these instances in the LLM section of your Turing admin. This is an automated notification; disable it with turing.model-catalog.change-feed.email-enabled=false.</p>
                  </div>
                </div>
                """.formatted(BG, CARD, BORDER, rows.toString());
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
