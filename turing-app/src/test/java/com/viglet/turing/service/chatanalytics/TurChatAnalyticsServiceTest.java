/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Pure-helper unit tests for the {@link TurChatAnalyticsService} T110
 * parent-conversation parsing surface, plus a record-shape sanity check
 * for {@link TurChatSessionEvent#start(String, String, String, String,
 * String, String, String, String, String, Instant, String)} carrying
 * the new {@code parentConversationId} field through.
 *
 * <p>Store-side persistence (Mongo / Redis) is exercised end-to-end by
 * the engine-integration tests that bring up Testcontainers — out of
 * scope for this unit-level pin.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatAnalyticsServiceTest {

    @Test
    void extractParentConversationId_returnsValueFromPrefixedMessage() {
        String firstUserMessage = "[__parentConversationId=parent-abc]\nFind me a quote.";
        assertThat(TurChatAnalyticsService.extractParentConversationId(firstUserMessage))
                .isEqualTo("parent-abc");
    }

    @Test
    void extractParentConversationId_returnsNullForBlankValue() {
        assertThat(TurChatAnalyticsService.extractParentConversationId("[__parentConversationId=]\nbody"))
                .isNull();
    }

    @Test
    void extractParentConversationId_returnsNullWhenPrefixAbsent() {
        assertThat(TurChatAnalyticsService.extractParentConversationId("Find me a quote.")).isNull();
        assertThat(TurChatAnalyticsService.extractParentConversationId("[__something_else=x]\nbody"))
                .isNull();
    }

    @Test
    void extractParentConversationId_returnsNullForNullInput() {
        assertThat(TurChatAnalyticsService.extractParentConversationId(null)).isNull();
    }

    @Test
    void extractParentConversationId_returnsNullWhenClosingBracketMissing() {
        // Defensive: avoid grabbing the rest of the line if the marker is malformed
        assertThat(TurChatAnalyticsService.extractParentConversationId(
                "[__parentConversationId=parent-abc\nFind me a quote."))
                .isNull();
    }

    @Test
    void stripParentConversationIdPrefix_dropsPrefixAndTrailingNewline() {
        String firstUserMessage = "[__parentConversationId=parent-abc]\nFind me a quote.";
        assertThat(TurChatAnalyticsService.stripParentConversationIdPrefix(firstUserMessage))
                .isEqualTo("Find me a quote.");
    }

    @Test
    void stripParentConversationIdPrefix_passesThroughWhenPrefixAbsent() {
        assertThat(TurChatAnalyticsService.stripParentConversationIdPrefix("Just a message"))
                .isEqualTo("Just a message");
    }

    @Test
    void stripParentConversationIdPrefix_returnsNullForNullInput() {
        assertThat(TurChatAnalyticsService.stripParentConversationIdPrefix(null)).isNull();
    }

    @Test
    void stripParentConversationIdPrefix_handlesNoTrailingNewline() {
        String firstUserMessage = "[__parentConversationId=parent-abc]body";
        assertThat(TurChatAnalyticsService.stripParentConversationIdPrefix(firstUserMessage))
                .isEqualTo("body");
    }

    @Test
    void stripParentConversationIdPrefix_passesThroughOnMalformedPrefix() {
        // No closing bracket — the helper writes the raw marker, the rest of the
        // input is preserved (caller's analytics row will still show the marker;
        // best-effort).
        String firstUserMessage = "[__parentConversationId=oops\nbody";
        assertThat(TurChatAnalyticsService.stripParentConversationIdPrefix(firstUserMessage))
                .isEqualTo(firstUserMessage);
    }

    @Test
    void startFactory_threadsParentConversationIdThrough() {
        Instant now = Instant.now();
        TurChatSessionEvent event = TurChatSessionEvent.start(
                "conv-child", "agent-1", null, "llm-1", null, null,
                "user-1", "pt-BR", "Find me a quote.", now, "conv-parent");

        assertThat(event.conversationId()).isEqualTo("conv-child");
        assertThat(event.parentConversationId()).isEqualTo("conv-parent");
        assertThat(event.firstUserMessage()).isEqualTo("Find me a quote.");
        assertThat(event.outcome()).isEqualTo(TurChatSessionOutcome.IN_PROGRESS);
    }

    @Test
    void startFactory_legacyOverload_setsNullParentConversationId() {
        Instant now = Instant.now();
        TurChatSessionEvent event = TurChatSessionEvent.start(
                "conv-root", "agent-1", null, "llm-1", null, null,
                "user-1", "pt-BR", "Hello", now);

        assertThat(event.parentConversationId()).isNull();
        // T74 — legacy overloads leave the cohort fields null.
        assertThat(event.timezone()).isNull();
        assertThat(event.deviceType()).isNull();
    }

    @Test
    void startFactory_initializesToolLatenciesToEmptyList() {
        // T88 — the start record never carries samples; they accrue at end.
        TurChatSessionEvent event = TurChatSessionEvent.start(
                "conv-1", "agent-1", null, "llm-1", null, null,
                "user-1", "pt-BR", "Hello", Instant.now());
        assertThat(event.toolLatencies()).isNotNull().isEmpty();
    }

    @Test
    void compactCtor_normalizesNullToolLatenciesToEmptyList() {
        // T88 — a null array (e.g. legacy callers) must not blow up consumers.
        TurChatSessionEvent event = new TurChatSessionEvent(
                "conv-1", "agent-1", null, "llm-1", null, null, "user-1", "pt-BR",
                null, Instant.now(), Instant.now(), TurChatSessionOutcome.COMPLETED,
                3, 100L, 200L, 1500L, 2, 0, 80L, null, null, null, null, null, null);
        assertThat(event.toolLatencies()).isNotNull().isEmpty();
    }

    @Test
    void compactCtor_copiesProvidedToolLatencies() {
        // T88 — supplied samples are preserved (defensively copied).
        TurChatSessionEvent event = new TurChatSessionEvent(
                "conv-1", "agent-1", null, "llm-1", null, null, "user-1", "pt-BR",
                null, Instant.now(), Instant.now(), TurChatSessionOutcome.COMPLETED,
                3, 100L, 200L, 1500L, 2, 0, 80L, null, null, null, null, null,
                java.util.List.of(
                        new TurToolLatencySample("search_site", 40, true),
                        new TurToolLatencySample("code_exec", 1200, false)));
        assertThat(event.toolLatencies()).hasSize(2);
        assertThat(event.toolLatencies().get(0).tool()).isEqualTo("search_site");
        assertThat(event.toolLatencies().get(1).success()).isFalse();
    }

    @Test
    void startFactory_cohortOverload_threadsTimezoneAndDeviceThrough() {
        Instant now = Instant.now();
        TurChatSessionEvent event = TurChatSessionEvent.start(
                "conv-1", "agent-1", null, "llm-1", null, null,
                "user-1", "pt-BR", "Hello", now, null,
                "America/Sao_Paulo", "mobile");

        assertThat(event.locale()).isEqualTo("pt-BR");
        assertThat(event.timezone()).isEqualTo("America/Sao_Paulo");
        assertThat(event.deviceType()).isEqualTo("mobile");
        assertThat(event.parentConversationId()).isNull();
        assertThat(event.outcome()).isEqualTo(TurChatSessionOutcome.IN_PROGRESS);
    }
}
