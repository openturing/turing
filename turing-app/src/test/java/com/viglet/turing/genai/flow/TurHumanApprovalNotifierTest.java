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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.core.webhook.VigletWebhookDeliveryResult;
import com.viglet.core.webhook.VigletWebhookDispatcher;
import com.viglet.core.webhook.VigletWebhookRequest;
import com.viglet.turing.email.TurEmailMessage;
import com.viglet.turing.email.TurEmailService;
import com.viglet.turing.service.chatslots.TurChatWebhookService;
import com.viglet.turing.service.chatslots.TurChatWebhookService.DispatchOutcome;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Unit tests for the T119 approval notifier channel routing: email →
 * {@link TurEmailService}, slack → {@link VigletWebhookDispatcher}, webhook →
 * {@link TurChatWebhookService} (with resume URL injected into the slot map).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurHumanApprovalNotifierTest {

    @Test
    @DisplayName("email channel: builds a message to the target with sender from Global Settings")
    void emailChannel_sendsToTarget() {
        TurEmailService email = mock(TurEmailService.class);
        TurGlobalSettingsService settings = mock(TurGlobalSettingsService.class);
        when(settings.getSenderEmail()).thenReturn("noreply@turing.example");
        when(settings.getSenderName()).thenReturn("Turing");
        TurHumanApprovalNotifier notifier = new TurHumanApprovalNotifier(
                email, mock(TurChatWebhookService.class), settings, mock(VigletWebhookDispatcher.class));

        notifier.notify("email", "ops@example.com", "Please review", "https://t/x", "conv-1", Map.of());

        ArgumentCaptor<TurEmailMessage> msg = ArgumentCaptor.forClass(TurEmailMessage.class);
        verify(email).sendEmail(msg.capture());
        assertThat(msg.getValue().getRecipientEmail()).isEqualTo("ops@example.com");
        assertThat(msg.getValue().getHtmlContent()).contains("Please review").contains("https://t/x");
    }

    @Test
    @DisplayName("slack channel: POSTs a {\"text\":…} payload to the target URL via the dispatcher")
    void slackChannel_postsToUrl() {
        VigletWebhookDispatcher dispatcher = mock(VigletWebhookDispatcher.class);
        when(dispatcher.deliver(any(), any())).thenReturn(VigletWebhookDeliveryResult.ok(200, 1));
        TurHumanApprovalNotifier notifier = new TurHumanApprovalNotifier(
                mock(TurEmailService.class), mock(TurChatWebhookService.class),
                mock(TurGlobalSettingsService.class), dispatcher);

        notifier.notify("slack", "https://hooks.slack/abc", "Approve?", "https://t/x", "conv-1", Map.of());

        ArgumentCaptor<VigletWebhookRequest> req = ArgumentCaptor.forClass(VigletWebhookRequest.class);
        verify(dispatcher).deliver(req.capture(), any());
        assertThat(req.getValue().url()).isEqualTo("https://hooks.slack/abc");
        assertThat(new String(req.getValue().body())).contains("Approve?").contains("https://t/x");
    }

    @Test
    @DisplayName("webhook channel: fires the named webhook with resume URL injected into the slot map")
    void webhookChannel_injectsResumeUrl() {
        TurChatWebhookService webhookService = mock(TurChatWebhookService.class);
        when(webhookService.dispatchFromFlowNode(eq("crm-approve"), eq("conv-1"), anyMap()))
                .thenReturn(new DispatchOutcome(true, "webhook://crm-approve", null));
        TurHumanApprovalNotifier notifier = new TurHumanApprovalNotifier(
                mock(TurEmailService.class), webhookService,
                mock(TurGlobalSettingsService.class), mock(VigletWebhookDispatcher.class));

        notifier.notify("webhook", "crm-approve", "msg", "https://t/x", "conv-1", Map.of("name", "Acme"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> slots = ArgumentCaptor.forClass(Map.class);
        verify(webhookService).dispatchFromFlowNode(eq("crm-approve"), eq("conv-1"), slots.capture());
        assertThat(slots.getValue())
                .containsEntry("name", "Acme")
                .containsEntry(TurHumanApprovalNotifier.SLOT_RESUME_URL, "https://t/x")
                .containsEntry(TurHumanApprovalNotifier.SLOT_PROMPT, "msg");
    }

    @Test
    @DisplayName("blank target / unknown channel: no dispatch, no exception")
    void blankOrUnknown_isNoOp() {
        TurEmailService email = mock(TurEmailService.class);
        TurChatWebhookService webhookService = mock(TurChatWebhookService.class);
        VigletWebhookDispatcher dispatcher = mock(VigletWebhookDispatcher.class);
        TurHumanApprovalNotifier notifier = new TurHumanApprovalNotifier(
                email, webhookService, mock(TurGlobalSettingsService.class), dispatcher);

        notifier.notify("email", "  ", "msg", "https://t/x", "conv-1", Map.of());
        notifier.notify("carrier-pigeon", "somewhere", "msg", "https://t/x", "conv-1", Map.of());

        verifyNoInteractions(email, webhookService, dispatcher);
    }
}
