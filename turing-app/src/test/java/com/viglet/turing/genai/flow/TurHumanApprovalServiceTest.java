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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.viglet.turing.genai.flow.strategy.ChatFlowOps;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.agent.TurChatHumanApproval;
import com.viglet.turing.persistence.model.agent.TurHumanApprovalStatus;
import com.viglet.turing.persistence.repository.agent.TurChatHumanApprovalRepository;
import com.viglet.turing.properties.TurConfigProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for the T119 {@code humanApproval} service: first-entry raises a
 * PENDING record + fires the notification, re-entry is idempotent, decisions
 * and timeouts resolve correctly, and the resume URL respects the base-url
 * setting.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurHumanApprovalServiceTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String NODE_ID = "ha-1";

    @Test
    @DisplayName("first entry: persists PENDING record, renders template, fires notification with absolute resume URL")
    void firstEntry_raisesAndNotifies() {
        TurChatHumanApprovalRepository repo = mock(TurChatHumanApprovalRepository.class);
        TurHumanApprovalNotifier notifier = mock(TurHumanApprovalNotifier.class);
        when(repo.findFirstByConversationIdAndNodeIdAndStatus(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TurHumanApprovalService service = new TurHumanApprovalService(repo, notifier, propsWithBaseUrl("https://t.example.com/"));
        TurChatFlowState state = stateWithSlots(Map.of("name", "Acme"));

        service.ensurePending(state, node("email", "ops@example.com",
                "Lead {{name}} ready", "operator_decision", 3600, "auto_reject"));

        ArgumentCaptor<TurChatHumanApproval> saved = ArgumentCaptor.forClass(TurChatHumanApproval.class);
        verify(repo).save(saved.capture());
        TurChatHumanApproval approval = saved.getValue();
        assertThat(approval.getStatus()).isEqualTo(TurHumanApprovalStatus.PENDING);
        assertThat(approval.getApprovalSlot()).isEqualTo("operator_decision");
        assertThat(approval.getPromptText()).isEqualTo("Lead Acme ready");
        assertThat(approval.getExpiresAt()).isNotNull();
        assertThat(approval.getResumeToken()).isNotBlank();

        ArgumentCaptor<String> resumeUrl = ArgumentCaptor.forClass(String.class);
        verify(notifier).notify(eq("email"), eq("ops@example.com"), eq("Lead Acme ready"),
                resumeUrl.capture(), eq("conv-test"), anyMap());
        assertThat(resumeUrl.getValue())
                .isEqualTo("https://t.example.com/api/genai/approval/" + approval.getResumeToken());
    }

    @Test
    @DisplayName("re-entry while pending: no second record, no second notification (idempotent)")
    void reEntry_isIdempotent() {
        TurChatHumanApprovalRepository repo = mock(TurChatHumanApprovalRepository.class);
        TurHumanApprovalNotifier notifier = mock(TurHumanApprovalNotifier.class);
        when(repo.findFirstByConversationIdAndNodeIdAndStatus(anyString(), anyString(), any()))
                .thenReturn(Optional.of(new TurChatHumanApproval()));

        TurHumanApprovalService service = new TurHumanApprovalService(repo, notifier, propsWithBaseUrl(""));
        service.ensurePending(stateWithSlots(Map.of()),
                node("email", "ops@example.com", "x", "operator_decision", 0, "auto_reject"));

        verify(repo, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("relative resume URL when base-url is blank")
    void blankBaseUrl_yieldsRelativeResumeUrl() {
        TurChatHumanApprovalRepository repo = mock(TurChatHumanApprovalRepository.class);
        TurHumanApprovalNotifier notifier = mock(TurHumanApprovalNotifier.class);
        when(repo.findFirstByConversationIdAndNodeIdAndStatus(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TurHumanApprovalService service = new TurHumanApprovalService(repo, notifier, propsWithBaseUrl(""));
        service.ensurePending(stateWithSlots(Map.of()),
                node("slack", "https://hooks.slack/x", "msg", null, null, null));

        ArgumentCaptor<String> resumeUrl = ArgumentCaptor.forClass(String.class);
        verify(notifier).notify(eq("slack"), anyString(), anyString(), resumeUrl.capture(), anyString(), anyMap());
        assertThat(resumeUrl.getValue()).startsWith("/api/genai/approval/");
    }

    @Test
    @DisplayName("effectiveApprovalSlot: explicit > outputVariable > default")
    void effectiveApprovalSlot_resolution() {
        assertThat(TurHumanApprovalService.effectiveApprovalSlot(
                node("email", "a", "t", "explicit_slot", null, null))).isEqualTo("explicit_slot");
        assertThat(TurHumanApprovalService.effectiveApprovalSlot(
                nodeWithOutputVariable("from_output"))).isEqualTo("from_output");
        assertThat(TurHumanApprovalService.effectiveApprovalSlot(
                node("email", "a", "t", null, null, null))).isEqualTo("operator_decision");
    }

    @Test
    @DisplayName("recordDecision: PENDING → DECIDED with decision; already-resolved is returned unchanged")
    void recordDecision_marksDecided() {
        TurChatHumanApprovalRepository repo = mock(TurChatHumanApprovalRepository.class);
        TurHumanApprovalService service = new TurHumanApprovalService(repo,
                mock(TurHumanApprovalNotifier.class), propsWithBaseUrl(""));

        TurChatHumanApproval pending = new TurChatHumanApproval();
        pending.setStatus(TurHumanApprovalStatus.PENDING);
        when(repo.findByResumeToken("tok")).thenReturn(Optional.of(pending));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TurChatHumanApproval out = service.recordDecision("tok", "approve").orElseThrow();
        assertThat(out.getStatus()).isEqualTo(TurHumanApprovalStatus.DECIDED);
        assertThat(out.getDecision()).isEqualTo("approve");
        assertThat(out.getDecidedAt()).isNotNull();

        TurChatHumanApproval already = new TurChatHumanApproval();
        already.setStatus(TurHumanApprovalStatus.DECIDED);
        when(repo.findByResumeToken("done")).thenReturn(Optional.of(already));
        assertThat(service.recordDecision("done", "reject").orElseThrow().getStatus())
                .isEqualTo(TurHumanApprovalStatus.DECIDED);
    }

    @Test
    @DisplayName("markTimedOut: auto_reject → reject, auto_approve → approve; status TIMED_OUT")
    void markTimedOut_decisionFromBehavior() {
        TurChatHumanApprovalRepository repo = mock(TurChatHumanApprovalRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TurHumanApprovalService service = new TurHumanApprovalService(repo,
                mock(TurHumanApprovalNotifier.class), propsWithBaseUrl(""));

        TurChatHumanApproval rejectRec = new TurChatHumanApproval();
        rejectRec.setTimeoutBehavior("auto_reject");
        assertThat(service.markTimedOut(rejectRec)).isEqualTo("reject");
        assertThat(rejectRec.getStatus()).isEqualTo(TurHumanApprovalStatus.TIMED_OUT);

        TurChatHumanApproval approveRec = new TurChatHumanApproval();
        approveRec.setTimeoutBehavior("auto_approve");
        assertThat(service.markTimedOut(approveRec)).isEqualTo("approve");
    }

    // ─────────────────────── helpers ───────────────────────

    private static TurConfigProperties propsWithBaseUrl(String baseUrl) {
        TurConfigProperties props = new TurConfigProperties();
        props.getGenai().getHumanApproval().setBaseUrl(baseUrl);
        return props;
    }

    private static TurChatFlowState stateWithSlots(Map<String, String> slots) {
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId("conv-test");
        ChatFlowOps.writeVariables(state, new LinkedHashMap<>(slots));
        return state;
    }

    private static ChatFlowNode node(String channel, String target, String template,
            String approvalSlot, Integer timeoutSeconds, String timeoutBehavior) {
        Map<String, Object> ha = new LinkedHashMap<>();
        ha.put("channel", channel);
        ha.put("target", target);
        ha.put("template", template);
        if (approvalSlot != null) {
            ha.put("approvalSlot", approvalSlot);
        }
        if (timeoutSeconds != null) {
            ha.put("timeoutSeconds", timeoutSeconds);
        }
        if (timeoutBehavior != null) {
            ha.put("timeoutBehavior", timeoutBehavior);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", "Approve");
        data.put("type", "humanApproval");
        data.put("humanApproval", ha);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", NODE_ID);
        root.put("type", "humanApproval");
        root.put("data", data);
        return OM.convertValue(root, ChatFlowNode.class);
    }

    private static ChatFlowNode nodeWithOutputVariable(String outputVariable) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", "Approve");
        data.put("type", "humanApproval");
        data.put("outputVariable", outputVariable);
        data.put("humanApproval", Map.of("channel", "email", "target", "a"));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", NODE_ID);
        root.put("type", "humanApproval");
        root.put("data", data);
        return OM.convertValue(root, ChatFlowNode.class);
    }
}
