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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.core.webhook.VigletWebhookDeliveryResult;
import com.viglet.core.webhook.VigletWebhookDispatcher;
import com.viglet.core.webhook.VigletWebhookRequest;
import com.viglet.core.webhook.VigletWebhookRetryPolicy;
import com.viglet.turing.genai.persona.match.dto.TurPersonaMatchDriftDto;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchSchedule;
import com.viglet.turing.spring.security.ssrf.TurSsrfGuard;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T827 / §LXI.3 (Block BN) — a scheduled run that regresses a project alerts an
 * opt-in webhook, and everything else stays quiet: no URL, no drift, a first run,
 * or a shallow slide under the configured margin. Delivery failures must never
 * escape (the analysis they follow already succeeded).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurPersonaMatchDriftAlertServiceTest {

    private static final String PROJECT = "p1";
    private static final String URL = "https://ops.example.com/hooks/persona-drift";
    private static final Instant RUN_1 = Instant.parse("2026-07-01T03:30:00Z");
    private static final Instant RUN_2 = Instant.parse("2026-07-02T03:30:00Z");

    @Mock
    private TurPersonaMatchHistoryService historyService;
    @Mock
    private TurPersonaMatchThresholdService thresholdService;
    @Mock
    private TurSecretCryptoService cryptoService;
    @Mock
    private VigletWebhookDispatcher webhookDispatcher;
    @Mock
    private TurSsrfGuard ssrfGuard;

    @InjectMocks
    private TurPersonaMatchDriftAlertService service;

    private static TurPersonaMatchProject project(String url, Double avgDrop) {
        TurPersonaMatchProject p = new TurPersonaMatchProject();
        p.setId(PROJECT);
        p.setName("Docs portfolio");
        p.setSchedule(TurPersonaMatchSchedule.DAILY);
        p.setDriftWebhookUrl(url);
        p.setDriftWebhookAvgDrop(avgDrop);
        return p;
    }

    private static TurPersonaMatchDriftDto.Entry entry(double previous, double current,
            boolean crossedBelow) {
        return new TurPersonaMatchDriftDto.Entry("s1", "a1", previous, current,
                current - previous, crossedBelow, false, false);
    }

    private static TurPersonaMatchDriftDto drift(int runCount, double previousAvg,
            double currentAvg, List<TurPersonaMatchDriftDto.Entry> entries) {
        return new TurPersonaMatchDriftDto(PROJECT, runCount, RUN_1, RUN_2, previousAvg,
                currentAvg, 75.0, (int) entries.stream().filter(e -> e.delta() < 0).count(),
                0, entries);
    }

    private void allowDelivery() {
        when(ssrfGuard.isAllowedUrl(URL)).thenReturn(true);
        when(thresholdService.forProject(any(TurPersonaMatchProject.class))).thenReturn(75.0);
        when(webhookDispatcher.deliver(any(VigletWebhookRequest.class),
                any(VigletWebhookRetryPolicy.class)))
                .thenReturn(VigletWebhookDeliveryResult.ok(200, 1));
    }

    @Test
    void aPairCrossingBelowTheBarFiresTheWebhook() {
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 80.0, 70.0, List.of(entry(80, 70, true))));
        allowDelivery();

        assertThat(service.alertAfterScheduledRun(project(URL, null))).isTrue();

        ArgumentCaptor<VigletWebhookRequest> sent =
                ArgumentCaptor.forClass(VigletWebhookRequest.class);
        verify(webhookDispatcher).deliver(sent.capture(), any(VigletWebhookRetryPolicy.class));
        assertThat(sent.getValue().url()).isEqualTo(URL);
        assertThat(sent.getValue().method()).isEqualTo("POST");
        assertThat(new String(sent.getValue().body()))
                .contains("persona_match_drift")
                .contains("crossed_below_fit")
                .contains("\"crossedBelowCount\":1");
    }

    @Test
    void aProjectWithNoWebhookUrlNeverDispatches() {
        assertThat(service.alertAfterScheduledRun(project(null, null))).isFalse();
        assertThat(service.alertAfterScheduledRun(null)).isFalse();
        verify(historyService, never()).drift(any());
        verify(webhookDispatcher, never()).deliver(any(), any());
    }

    @Test
    void aCleanRunStaysQuiet() {
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 70.0, 78.0, List.of(entry(70, 78, false))));

        assertThat(service.alertAfterScheduledRun(project(URL, null))).isFalse();
        verify(webhookDispatcher, never()).deliver(any(), any());
    }

    @Test
    void aFirstRunHasNothingToHaveDriftedFrom() {
        when(historyService.drift(PROJECT))
                .thenReturn(new TurPersonaMatchDriftDto(PROJECT, 1, null, RUN_1, 0, 0, 75.0,
                        0, 0, List.of()));

        assertThat(service.alertAfterScheduledRun(project(URL, 5.0))).isFalse();
        verify(webhookDispatcher, never()).deliver(any(), any());
    }

    @Test
    void theAverageDropMarginFiresEvenWhenNoPairCrossedTheBar() {
        // Everything stayed under the bar, so no crossing — but the whole project
        // slid 12 points, which is exactly what a per-pair flag cannot see.
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 62.0, 50.0, List.of(entry(62, 50, false))));
        allowDelivery();

        assertThat(service.alertAfterScheduledRun(project(URL, 10.0))).isTrue();
        verify(webhookDispatcher).deliver(any(), any());
    }

    @Test
    void aSlideSmallerThanTheMarginStaysQuiet() {
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 62.0, 58.0, List.of(entry(62, 58, false))));

        assertThat(service.alertAfterScheduledRun(project(URL, 10.0))).isFalse();
        verify(webhookDispatcher, never()).deliver(any(), any());
    }

    @Test
    void aBlockedEgressTargetIsSwallowedNotThrown() {
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 80.0, 70.0, List.of(entry(80, 70, true))));
        when(ssrfGuard.isAllowedUrl(URL)).thenReturn(false);

        assertThat(service.alertAfterScheduledRun(project(URL, null))).isFalse();
        verify(webhookDispatcher, never()).deliver(any(), any());
    }

    @Test
    void aFailedDeliveryIsSwallowedSoTheRunIsNeverAffected() {
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 80.0, 70.0, List.of(entry(80, 70, true))));
        when(ssrfGuard.isAllowedUrl(URL)).thenReturn(true);
        when(thresholdService.forProject(any(TurPersonaMatchProject.class))).thenReturn(75.0);
        when(webhookDispatcher.deliver(any(), any()))
                .thenReturn(VigletWebhookDeliveryResult.failed(502, 3, "bad gateway"));

        assertThat(service.alertAfterScheduledRun(project(URL, null))).isFalse();
    }

    @Test
    void theAuthHeaderIsDecryptedAtSendTimeAndAttached() {
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 80.0, 70.0, List.of(entry(80, 70, true))));
        allowDelivery();
        when(cryptoService.decrypt("cipher")).thenReturn("Bearer real-token");

        TurPersonaMatchProject p = project(URL, null);
        p.setDriftWebhookAuthHeader("cipher");
        assertThat(service.alertAfterScheduledRun(p)).isTrue();

        ArgumentCaptor<VigletWebhookRequest> sent =
                ArgumentCaptor.forClass(VigletWebhookRequest.class);
        verify(webhookDispatcher).deliver(sent.capture(), any());
        Map<String, String> headers = sent.getValue().headers();
        assertThat(headers).containsEntry("Authorization", "Bearer real-token");
    }

    @Test
    void anUndecryptableSecretStillDeliversUnauthenticated() {
        when(historyService.drift(PROJECT))
                .thenReturn(drift(2, 80.0, 70.0, List.of(entry(80, 70, true))));
        allowDelivery();
        when(cryptoService.decrypt("rotated")).thenThrow(new IllegalStateException("bad key"));

        TurPersonaMatchProject p = project(URL, null);
        p.setDriftWebhookAuthHeader("rotated");
        assertThat(service.alertAfterScheduledRun(p)).isTrue();

        ArgumentCaptor<VigletWebhookRequest> sent =
                ArgumentCaptor.forClass(VigletWebhookRequest.class);
        verify(webhookDispatcher).deliver(sent.capture(), any());
        assertThat(sent.getValue().headers()).doesNotContainKey("Authorization");
    }

    @Test
    void resolveAuthCipherKeepsTheStoredSecretWhenTheFormLeftItBlank() {
        when(cryptoService.encrypt("Bearer new")).thenReturn("enc(new)");

        assertThat(service.resolveAuthCipher("Bearer new", true, "old")).isEqualTo("enc(new)");
        assertThat(service.resolveAuthCipher("  ", true, "old")).isEqualTo("old");
        assertThat(service.resolveAuthCipher(null, false, "old")).isNull();
    }
}
