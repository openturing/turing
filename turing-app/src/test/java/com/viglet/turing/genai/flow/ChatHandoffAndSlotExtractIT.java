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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlotType;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.service.chatslots.TurChatHandoffService;
import com.viglet.turing.service.chatslots.TurChatHandoffService.Channel;
import com.viglet.turing.service.chatslots.TurChatHandoffService.HandoffRequest;
import com.viglet.turing.service.chatslots.TurChatHandoffService.HandoffResult;
import com.viglet.turing.service.chatslots.TurChatSlotExtractionService;
import com.viglet.turing.service.chatslots.TurChatSlotExtractionService.SlotExtractionResult;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * IT for the two visitor-facing chat endpoints that touch slots without
 * driving a turn: {@link TurChatHandoffService} (deep-link composer for
 * channels: WhatsApp / Email / SMS / Telegram / Slack) and
 * {@link TurChatSlotExtractionService} (Tika + LLM upload-to-slots).
 *
 * <p>Handoff is fully exercised — no LLM round-trip is involved, so all
 * five channels run on the real Spring-wired service. Slot extraction
 * is exercised at the boundaries that don't require a configured LLM
 * (empty/no-state guards, slot resolution from agent catalog); the
 * happy-path LLM call is covered by the service's own contract since a
 * real OpenAI client + seeded {@code TurLLMInstance} would balloon
 * setup beyond what this guard suite needs. The aim of this IT is to
 * catch wiring regressions on the two endpoints — not to re-validate
 * the LLM prompt.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatHandoffAndSlotExtractIT extends AbstractTuringSpringIT {

    @Autowired private TurChatHandoffService handoffService;
    @Autowired private TurChatSlotExtractionService slotExtractionService;
    @Autowired private TurChatFlowEngineService engine;
    @Autowired private TurAIAgentRepository agentRepository;
    @Autowired private TurAIAgentSlotRepository slotRepository;
    @Autowired private TurChatFlowRepository chatFlowRepository;
    @Autowired private TurChatFlowStateRepository stateRepository;

    private TurAIAgent agent;
    private TurChatFlow flow;
    private String conversationId;

    @BeforeEach
    void seedFlowAndState(TestInfo info) {
        System.out.println();
        System.out.println("=== " + info.getDisplayName() + " ===");

        TurAIAgent a = new TurAIAgent();
        a.setTitle("handoff-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);

        // Minimal flow + state row so writeSlot / listSlotsForConversation
        // have somewhere to write/read. We're not driving the engine, just
        // exercising the slot read/write paths the two endpoints use.
        TurChatFlow f = new TurChatFlow();
        f.setName("handoff-it-flow");
        f.setEnabled(1);
        f.setTurAIAgent(agent);
        f.setDefinitionJson("{\"nodes\":[],\"edges\":[]}");
        flow = chatFlowRepository.save(f);

        conversationId = "conv-" + UUID.randomUUID();
        TurChatFlowState state = new TurChatFlowState();
        state.setConversationId(conversationId);
        state.setFlow(flow);
        state.setCurrentNodeId("idle");
        state.setVariablesJson("{}");
        stateRepository.save(state);

        // Populate a realistic Programa-Match-style slot snapshot.
        engine.writeSlot(conversationId, "name", "Alexandre");
        engine.writeSlot(conversationId, "cargo_atual", "Gerente sênior numa fintech");
        engine.writeSlot(conversationId, "objetivo", "virar CFO em 3 anos");
        engine.writeSlot(conversationId, "area", "financas");
        engine.writeSlot(conversationId, "area_label", "Finanças & Investimentos");
        // Internal flags — handoff should skip these by default.
        engine.writeSlot(conversationId, "color", "#0066ff");
        engine.writeSlot(conversationId, "cta_visible", "true");
    }

    // ─────────────────────── Handoff ───────────────────────

    @Test
    void handoff_whatsapp_buildsWaMeUrlWithEncodedTranscript() {
        HandoffResult result = handoffService.buildLink(conversationId,
                new HandoffRequest(Channel.WHATSAPP, "+55 11 91234-5678",
                        "Oi! Mandando o resumo do que conversamos com a Marina.",
                        List.of("name", "cargo_atual", "objetivo", "area_label")));

        assertThat(result.error()).isNull();
        assertThat(result.url())
                .as("WhatsApp uses wa.me universal link with digits-only phone")
                .startsWith("https://wa.me/5511912345678?text=");
        assertThat(result.slotsIncluded()).isEqualTo(4);

        String decoded = decodeQueryParam(result.url(), "text");
        assertThat(decoded)
                .contains("Alexandre")
                .contains("Gerente sênior")
                .contains("virar CFO em 3 anos")
                .contains("Finanças & Investimentos");
        assertThat(result.transcript()).contains("• name: Alexandre");

        // Side effect: handoff_status slot written so the React UI can
        // hide the CTA once clicked.
        TurChatSessionSlotsDto after = engine.listSlotsForConversation(conversationId);
        assertThat(after.slots()).containsEntry("handoff_status", "whatsapp_requested");
    }

    @Test
    void handoff_email_buildsMailtoWithSubjectAndBody() {
        HandoffResult result = handoffService.buildLink(conversationId,
                new HandoffRequest(Channel.EMAIL, "consultor@education.example.com",
                        null, null));

        assertThat(result.error()).isNull();
        assertThat(result.url())
                .startsWith("mailto:consultor@education.example.com?subject=")
                .contains("&body=");
        // Default slot filter: skips internal flags (color, cta_visible).
        String body = decodeQueryParam(result.url(), "body");
        assertThat(body).contains("Alexandre").doesNotContain("cta_visible");
    }

    @Test
    void handoff_sms_buildsSmsUrlPreservingPlusInCountryCode() {
        HandoffResult result = handoffService.buildLink(conversationId,
                new HandoffRequest(Channel.SMS, "+55 11 99999-0000", null, null));

        assertThat(result.error()).isNull();
        assertThat(result.url())
                .as("SMS link must preserve the leading + on country code")
                .startsWith("sms:+5511999990000?body=");
    }

    @Test
    void handoff_telegram_acceptsUsernameAndPhone() {
        HandoffResult username = handoffService.buildLink(conversationId,
                new HandoffRequest(Channel.TELEGRAM, "@education_ee", null, null));
        assertThat(username.url())
                .as("Username form strips the @ and goes to t.me/{username}")
                .startsWith("https://t.me/education_ee?text=");

        HandoffResult phone = handoffService.buildLink(conversationId,
                new HandoffRequest(Channel.TELEGRAM, "+55 11 91234-5678", null, null));
        assertThat(phone.url())
                .as("Phone form goes to t.me/+digits")
                .startsWith("https://t.me/+5511912345678?text=");
    }

    @Test
    void handoff_slack_buildsAppRedirectWithTeamAndChannel() {
        HandoffResult result = handoffService.buildLink(conversationId,
                new HandoffRequest(Channel.SLACK, "T01ABCDEF/U05XYZ123", null, null));

        assertThat(result.error()).isNull();
        assertThat(result.url())
                .startsWith("https://slack.com/app_redirect?team=T01ABCDEF&channel=U05XYZ123&message=");
    }

    @Test
    void handoff_missingDestination_returnsErrorWithoutSideEffect() {
        HandoffResult result = handoffService.buildLink(conversationId,
                new HandoffRequest(Channel.WHATSAPP, "", null, null));

        assertThat(result.error()).isNotNull().containsIgnoringCase("destination");
        assertThat(result.url()).isNull();
        // No tracking slot written when the request was rejected.
        TurChatSessionSlotsDto after = engine.listSlotsForConversation(conversationId);
        assertThat(after.slots()).doesNotContainKey("handoff_status");
    }

    // ─────────────────────── Slot extraction ───────────────────────

    @Test
    void slotExtract_emptyFile_returnsEmptyResultWithoutSlotWrites() {
        // Declare a slot on the agent so the catalog isn't empty — the
        // empty-file guard fires before slot resolution, but we want to
        // prove the side-effect-free contract end-to-end.
        seedSlot("name", "Visitor name");

        MockMultipartFile empty = new MockMultipartFile(
                "file", "blank.txt", "text/plain", new byte[0]);

        SlotExtractionResult result = slotExtractionService.extract(
                empty, agent, conversationId, List.of("name"));

        assertThat(result.extracted()).isEmpty();
        assertThat(result.slotsWritten()).isZero();
        assertThat(result.extractedTextChars()).isZero();
    }

    @Test
    void slotExtract_noConversationId_returnsEmptyResult() {
        seedSlot("name", "Visitor name");

        MockMultipartFile cv = new MockMultipartFile(
                "file", "cv.txt", "text/plain",
                "Alexandre Oliveira\nGerente sênior".getBytes(StandardCharsets.UTF_8));

        SlotExtractionResult result = slotExtractionService.extract(
                cv, agent, "  ", List.of("name"));

        assertThat(result.extracted()).isEmpty();
        assertThat(result.slotsWritten()).isZero();
    }

    @Test
    void slotExtract_agentWithoutLlmInstance_returnsEmptyResult() {
        // Agent has slots declared but no TurLLMInstance attached → the
        // chat model can't be built and the service returns empty rather
        // than crashing. This is the dominant unconfigured-agent path
        // in production deployments.
        seedSlot("name", "Visitor name");
        seedSlot("cargo_atual", "Current role of the visitor");

        MockMultipartFile cv = new MockMultipartFile(
                "file", "cv.txt", "text/plain",
                ("Alexandre Oliveira\n"
                        + "Gerente sênior numa fintech, há 4 anos\n"
                        + "Objetivo: virar CFO em 3 anos.").getBytes(StandardCharsets.UTF_8));

        SlotExtractionResult result = slotExtractionService.extract(
                cv, agent, conversationId, List.of("name", "cargo_atual"));

        assertThat(result.extracted()).isEmpty();
        assertThat(result.slotsWritten()).isZero();
        // Tika ran successfully — the guard fires AFTER text extraction,
        // so the extractedTextChars budget is zero only because the
        // service short-circuits before reporting it. Real behavior is
        // that the request is rejected post-Tika; we just assert the
        // public contract.
    }

    private void seedSlot(String name, String description) {
        TurAIAgentSlot slot = new TurAIAgentSlot();
        slot.setName(name);
        slot.setDescription(description);
        slot.setType(TurAIAgentSlotType.STRING);
        slot.setTurAIAgent(agent);
        slotRepository.save(slot);
    }

    private static String decodeQueryParam(String url, String paramName) {
        int idx = url.indexOf(paramName + "=");
        if (idx < 0) return "";
        int start = idx + paramName.length() + 1;
        int end = url.indexOf('&', start);
        String raw = end < 0 ? url.substring(start) : url.substring(start, end);
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
