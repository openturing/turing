/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.core.webhook.VigletWebhookDeliveryResult;
import com.viglet.core.webhook.VigletWebhookDispatcher;
import com.viglet.core.webhook.VigletWebhookRequest;
import com.viglet.core.webhook.VigletWebhookRetryPolicy;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchDriftDto;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.spring.security.ssrf.TurSsrfGuard;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * T827 / §LXI.3 (Block BN) — pushes a Persona Match project's drift report to an
 * opt-in webhook after a <strong>scheduled</strong> re-analysis regressed it.
 *
 * <p>T700 re-runs a DAILY project at 03:30 and T704 records exactly what moved —
 * into a table. Until now, a content that fell out of fit for its audience
 * overnight stayed that way until a human happened to open the studio, which
 * defeats the point of scheduling continuous tuning. Manual runs are deliberately
 * <em>not</em> alerted: the person who clicked "Run analysis" is already looking at
 * the result.
 *
 * <p>Alert condition (either is enough):
 * <ul>
 *   <li>at least one {@code (content, persona)} pair <b>crossed below</b> the
 *   project's T826 good-fit threshold, or</li>
 *   <li>the project average dropped by more than the project's configured margin
 *   ({@code driftWebhookAvgDrop}) — the broad, shallow slide no per-pair flag
 *   catches.</li>
 * </ul>
 *
 * <p><b>Webhook-only, and fail-soft.</b> {@code turing-app} has no mail
 * dependency and this block does not add one. Delivery follows the T62
 * {@code TurChatWebhookDispatcher} pattern rather than reinventing it: the shared
 * {@link VigletWebhookDispatcher} with a bounded retry/timeout policy, the
 * {@code Authorization} value decrypted from
 * {@link TurSecretCryptoService} at send time, and the target URL gated through
 * {@link TurSsrfGuard}. Every failure — bad URL, dead endpoint, undecryptable
 * secret — is logged and swallowed: an alert that could fail the nightly analysis
 * would be a worse bug than the silence it fixes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaMatchDriftAlertService {

    /** {@code event} field value of the POSTed payload. */
    public static final String EVENT_DRIFT = "persona_match_drift";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Same bounded policy the chat webhooks use: 3 attempts, 10s each, 2s back-off. */
    private static final VigletWebhookRetryPolicy RETRY_POLICY = new VigletWebhookRetryPolicy(
            3, Duration.ofSeconds(10), Duration.ofSeconds(2));

    private final TurPersonaMatchHistoryService historyService;
    private final TurPersonaMatchThresholdService thresholdService;
    private final TurSecretCryptoService cryptoService;
    private final VigletWebhookDispatcher webhookDispatcher;
    private final TurSsrfGuard ssrfGuard;

    public TurPersonaMatchDriftAlertService(TurPersonaMatchHistoryService historyService,
            TurPersonaMatchThresholdService thresholdService,
            TurSecretCryptoService cryptoService,
            VigletWebhookDispatcher webhookDispatcher,
            TurSsrfGuard ssrfGuard) {
        this.historyService = historyService;
        this.thresholdService = thresholdService;
        this.cryptoService = cryptoService;
        this.webhookDispatcher = webhookDispatcher;
        this.ssrfGuard = ssrfGuard;
    }

    /**
     * Evaluate a just-finished scheduled run and POST the drift report when it
     * regressed the project. Never throws: the caller is a scheduled job whose
     * analysis has already succeeded.
     *
     * @return {@code true} when a payload was actually delivered (for tests/logs)
     */
    public boolean alertAfterScheduledRun(TurPersonaMatchProject project) {
        if (project == null || StringUtils.isBlank(project.getDriftWebhookUrl())) {
            return false; // opt-in: no URL, no alert
        }
        try {
            TurPersonaMatchDriftDto drift = historyService.drift(project.getId());
            List<String> reasons = triggerReasons(project, drift);
            if (reasons.isEmpty()) {
                return false;
            }
            deliver(project, buildPayload(project, drift, reasons));
            log.info("[PersonaMatch] drift alert sent project={} reasons={} -> {}",
                    project.getId(), reasons, project.getDriftWebhookUrl());
            return true;
        } catch (Exception e) {
            // Fail-soft by design — the run itself already succeeded.
            log.warn("[PersonaMatch] drift alert failed for project={}: {}",
                    project.getId(), e.getMessage());
            return false;
        }
    }

    // ---- decision -----------------------------------------------------------

    /**
     * Why this run is worth an alert, empty when it is not. Returned as a list so
     * the payload can say which rule fired instead of leaving the receiver to
     * re-derive it.
     */
    List<String> triggerReasons(TurPersonaMatchProject project, TurPersonaMatchDriftDto drift) {
        List<String> reasons = new ArrayList<>(2);
        if (drift == null || drift.runCount() < 2) {
            return reasons; // a first run has nothing to have drifted from
        }
        long crossedBelow = countCrossedBelow(drift);
        if (crossedBelow > 0) {
            reasons.add("crossed_below_fit");
        }
        Double margin = project.getDriftWebhookAvgDrop();
        if (margin != null && margin > 0
                && drift.previousAvg() - drift.currentAvg() > margin) {
            reasons.add("average_dropped");
        }
        return reasons;
    }

    private static long countCrossedBelow(TurPersonaMatchDriftDto drift) {
        return drift.entries().stream().filter(TurPersonaMatchDriftDto.Entry::crossedBelowFit)
                .count();
    }

    // ---- payload ------------------------------------------------------------

    /**
     * The alert envelope: run/threshold context, why it fired, and the T704 drift
     * report verbatim — the same shape {@code GET /api/persona-match/{id}/drift}
     * already returns, so there is no second serialization to keep in sync.
     */
    Map<String, Object> buildPayload(TurPersonaMatchProject project,
            TurPersonaMatchDriftDto drift, List<String> reasons) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", EVENT_DRIFT);
        payload.put("projectId", project.getId());
        payload.put("projectName", project.getName());
        payload.put("schedule", project.getSchedule() == null ? null
                : project.getSchedule().name());
        payload.put("fitThreshold", thresholdService.forProject(project));
        payload.put("reasons", reasons);
        payload.put("previousRunAt", drift.previousRunAt());
        payload.put("currentRunAt", drift.currentRunAt());
        payload.put("previousAvg", drift.previousAvg());
        payload.put("currentAvg", drift.currentAvg());
        payload.put("avgDelta", round(drift.currentAvg() - drift.previousAvg()));
        payload.put("crossedBelowCount", countCrossedBelow(drift));
        payload.put("regressedCount", drift.regressedCount());
        payload.put("improvedCount", drift.improvedCount());
        payload.put("drift", drift);
        return payload;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ---- delivery -----------------------------------------------------------

    private void deliver(TurPersonaMatchProject project, Map<String, Object> payload)
            throws java.io.IOException {
        String url = project.getDriftWebhookUrl().trim();
        // A project's webhook URL is operator-supplied but still egress: gate it
        // exactly like the T643 chat-webhook path does.
        if (!ssrfGuard.isAllowedUrl(url)) {
            throw new java.io.IOException(
                    "Drift webhook URL is not an allowed egress target: " + url);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        String auth = decryptAuth(project.getDriftWebhookAuthHeader());
        if (StringUtils.isNotBlank(auth)) {
            headers.put("Authorization", auth);
        }
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .method("POST")
                .url(url)
                .contentType("application/json")
                .headers(headers)
                .body(OBJECT_MAPPER.writeValueAsBytes(payload))
                .build();

        VigletWebhookDeliveryResult result = webhookDispatcher.deliver(request, RETRY_POLICY);
        if (!result.delivered()) {
            throw new java.io.IOException(result.error());
        }
    }

    /**
     * Encrypts a plaintext header for storage, keeping the stored ciphertext when
     * the admin saved the form without re-typing the secret (blank +
     * {@code keepExisting}) — the write-only-secret contract T62 established.
     */
    public String resolveAuthCipher(String plaintext, boolean keepExisting, String existingCipher) {
        if (StringUtils.isNotBlank(plaintext)) {
            return cryptoService.encrypt(plaintext.trim());
        }
        return keepExisting ? existingCipher : null;
    }

    private String decryptAuth(String cipher) {
        if (StringUtils.isBlank(cipher)) {
            return null;
        }
        try {
            return cryptoService.decrypt(cipher);
        } catch (Exception e) {
            // Key rotation / corrupt ciphertext: send unauthenticated and let the
            // receiver reject, rather than losing the alert entirely.
            log.warn("[PersonaMatch] could not decrypt drift webhook auth header "
                    + "(sending unauthenticated): {}", e.getMessage());
            return null;
        }
    }
}
