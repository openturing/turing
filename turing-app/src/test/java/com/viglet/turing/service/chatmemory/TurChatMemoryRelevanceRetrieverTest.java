/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * Tests for T30 / §IV.4 conversation-relevance retrieval.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurChatMemoryRelevanceRetrieverTest {

    private static final String CONV_ID = "conv-test-1";
    private static final Instant BASE_TIME = Instant.parse("2026-05-01T10:00:00Z");

    private TurChatMemoryStore store;
    private TurChatMemoryRelevanceRetriever retriever;
    private TurAIAgent agent;

    @BeforeEach
    void setUp() {
        store = mock(TurChatMemoryStore.class);
        when(store.isEnabled()).thenReturn(true);
        when(store.getEngine()).thenReturn(TurChatMemoryEngine.MONGODB);
        // T32 observation wires through Micrometer; passing null to the
        // constructor lets it fall back to SimpleMeterRegistry so unit
        // tests don't need the full observability stack.
        retriever = new TurChatMemoryRelevanceRetriever(store,
                new TurChatPipelineObservation(null));
        agent = newAgentWithRelevanceOn();
    }

    @Test
    void returnsInputUnchangedWhenAgentDisablesChatMemory() {
        agent.setChatMemoryEnabled(false);
        List<ChatMessageItem> history = recentHistory();

        List<ChatMessageItem> out = retriever.enrich(agent, CONV_ID, "anything", history);

        assertThat(out).isSameAs(history);
        verify(store, never()).findMessages(anyString(), anyInt());
    }

    @Test
    void returnsInputUnchangedWhenRelevanceDisabled() {
        agent.setChatMemoryRelevanceEnabled(false);
        List<ChatMessageItem> history = recentHistory();

        List<ChatMessageItem> out = retriever.enrich(agent, CONV_ID, "anything", history);

        assertThat(out).isSameAs(history);
        verify(store, never()).findMessages(anyString(), anyInt());
    }

    @Test
    void returnsInputUnchangedWhenStoreDisabled() {
        when(store.isEnabled()).thenReturn(false);
        List<ChatMessageItem> history = recentHistory();

        List<ChatMessageItem> out = retriever.enrich(agent, CONV_ID, "tell me more", history);

        assertThat(out).isSameAs(history);
        verify(store, never()).findMessages(anyString(), anyInt());
    }

    @Test
    void returnsInputUnchangedWhenConversationIdBlank() {
        List<ChatMessageItem> history = recentHistory();

        assertThat(retriever.enrich(agent, null, "q", history)).isSameAs(history);
        assertThat(retriever.enrich(agent, "   ", "q", history)).isSameAs(history);
        verify(store, never()).findMessages(anyString(), anyInt());
    }

    @Test
    void returnsInputUnchangedWhenUserMessageBlank() {
        List<ChatMessageItem> history = recentHistory();

        assertThat(retriever.enrich(agent, CONV_ID, null, history)).isSameAs(history);
        assertThat(retriever.enrich(agent, CONV_ID, "   ", history)).isSameAs(history);
        verify(store, never()).findMessages(anyString(), anyInt());
    }

    @Test
    void returnsInputUnchangedWhenPersistedSizeWithinRecentWindow() {
        // Recent-N window is 10; persisted has only 8 messages → no
        // "older" pool to score.
        agent.setChatMemoryRecentN(10);
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages()))
                .thenReturn(persistedTurns(8, "anything"));
        List<ChatMessageItem> history = recentHistory();

        List<ChatMessageItem> out = retriever.enrich(agent, CONV_ID, "shipping cost", history);

        assertThat(out).isSameAs(history);
    }

    @Test
    void returnsInputUnchangedWhenBm25HasNoOverlap() {
        // 30 persisted, recent-N=10 → 20 older; user asks about something
        // none of them mention.
        agent.setChatMemoryRecentN(10);
        agent.setChatMemoryRelevanceTopK(3);
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages()))
                .thenReturn(persistedTurns(30, "weather forecast tomorrow"));
        List<ChatMessageItem> history = recentHistory();

        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "blockchain consensus algorithms", history);

        assertThat(out).isSameAs(history);
    }

    @Test
    void prependsRelevantOlderTurnsWithPositionAndTimestampMarkers() {
        // Build a 25-turn persisted log where turn #2 is the only one
        // mentioning "discount coupon". Recent-N=10 leaves turns 0..14
        // (positions) as the older pool — turn #2 must be retrieved.
        // Filler content shares zero terms with the query so BM25
        // unambiguously ranks the target on top.
        agent.setChatMemoryRecentN(10);
        agent.setChatMemoryRelevanceTopK(2);

        List<Map<String, Object>> persisted = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String content = (i == 2)
                    ? "I want to apply a discount coupon to my order"
                    : "weather rainy temperature dropping " + i;
            persisted.add(turn("user", content, BASE_TIME.plusSeconds(60L * i)));
        }
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages())).thenReturn(persisted);

        List<ChatMessageItem> history = recentHistory();
        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "remind me about discount coupon", history);

        // Result must be longer than the input (something was prepended)
        assertThat(out).hasSizeGreaterThan(history.size());
        assertThat(out.subList(out.size() - history.size(), out.size()))
                .as("tail equals the original history verbatim")
                .containsExactlyElementsOf(history);

        // The first message must be the retrieved hit, tagged with the
        // earlier-turn marker including position (#3 = 0-indexed 2 + 1)
        // and timestamp.
        ChatMessageItem first = out.get(0);
        assertThat(first.role()).isEqualTo("user");
        assertThat(first.content())
                .startsWith("[earlier turn #3 at ")
                .contains("] ")
                .contains("discount coupon");
    }

    @Test
    void retrievedHitsSortedChronologicallyAfterScoring() {
        // Plant TWO relevant older turns at positions 0 and 5; BM25 might
        // rank either first depending on length, but the retriever must
        // re-sort by chronological position so the LLM sees them in the
        // order they originally happened.
        agent.setChatMemoryRecentN(5);
        agent.setChatMemoryRelevanceTopK(3);

        List<Map<String, Object>> persisted = new ArrayList<>();
        persisted.add(turn("user", "first time we discussed shipping address logistics",
                BASE_TIME));
        for (int i = 1; i < 5; i++) {
            persisted.add(turn("user", "unrelated filler turn " + i,
                    BASE_TIME.plusSeconds(60L * i)));
        }
        persisted.add(turn("user", "second mention of shipping address details",
                BASE_TIME.plusSeconds(60L * 5)));
        for (int i = 6; i < 15; i++) {
            persisted.add(turn("user", "more filler " + i,
                    BASE_TIME.plusSeconds(60L * i)));
        }
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages())).thenReturn(persisted);

        List<ChatMessageItem> history = recentHistory();
        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "what was the shipping address again", history);

        // Find prepended turns (everything before the original history)
        List<ChatMessageItem> prepended = out.subList(0, out.size() - history.size());
        assertThat(prepended).hasSize(2);
        // First retrieved must be the earlier position (#1, BASE_TIME).
        assertThat(prepended.get(0).content()).startsWith("[earlier turn #1 at ");
        // Second retrieved must be the later position (#6, BASE_TIME + 5min).
        assertThat(prepended.get(1).content()).startsWith("[earlier turn #6 at ");
    }

    @Test
    void capsRetrievedCountAtConfiguredTopK() {
        // Plant 5 strong matches; configure top-K=2 → exactly 2 prepended.
        agent.setChatMemoryRecentN(5);
        agent.setChatMemoryRelevanceTopK(2);

        List<Map<String, Object>> persisted = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            String content = i < 5
                    ? "billing question on invoice number " + (1000 + i)
                    : "unrelated chat " + i;
            persisted.add(turn("user", content, BASE_TIME.plusSeconds(60L * i)));
        }
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages())).thenReturn(persisted);

        List<ChatMessageItem> history = recentHistory();
        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "open my latest billing invoice", history);

        int prepended = out.size() - history.size();
        assertThat(prepended).isEqualTo(2);
    }

    @Test
    void hardCapsTopKAtTwentyEvenWhenAgentMisconfigured() {
        // Operator sets top-K to 999; retriever must clamp at 20.
        agent.setChatMemoryRecentN(1);
        agent.setChatMemoryRelevanceTopK(999);

        // 50 user-typed strong matches → BM25 wants all 50, but cap=20.
        List<Map<String, Object>> persisted = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            persisted.add(turn("user", "billing invoice number " + (1000 + i),
                    BASE_TIME.plusSeconds(60L * i)));
        }
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages())).thenReturn(persisted);

        List<ChatMessageItem> history = recentHistory();
        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "open my latest billing invoice", history);

        int prepended = out.size() - history.size();
        assertThat(prepended).isLessThanOrEqualTo(TurChatMemoryRelevanceRetriever.RELEVANCE_TOP_K_MAX);
    }

    @Test
    void preservesRoleOnRetrievedTurns() {
        // Mix user + assistant messages in the older pool; assert the
        // retrieved role survives the round-trip.
        agent.setChatMemoryRecentN(3);
        agent.setChatMemoryRelevanceTopK(2);

        List<Map<String, Object>> persisted = new ArrayList<>();
        persisted.add(turn("user", "what is your refund policy",
                BASE_TIME));
        persisted.add(turn("assistant", "Our refund policy allows returns within 30 days",
                BASE_TIME.plusSeconds(30)));
        for (int i = 2; i < 10; i++) {
            persisted.add(turn(i % 2 == 0 ? "user" : "assistant",
                    "unrelated chat about pizza " + i,
                    BASE_TIME.plusSeconds(60L * i)));
        }
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages())).thenReturn(persisted);

        List<ChatMessageItem> history = recentHistory();
        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "tell me again about your refund policy", history);

        List<ChatMessageItem> prepended = out.subList(0, out.size() - history.size());
        assertThat(prepended).isNotEmpty();
        // Roles must be drawn from the persisted store (user OR assistant),
        // not all coerced to "user".
        List<String> roles = prepended.stream().map(ChatMessageItem::role).toList();
        assertThat(roles).containsAnyOf("user", "assistant");
    }

    @Test
    void returnsEmptyListWhenHistoryIsNull() {
        assertThat(retriever.enrich(agent, CONV_ID, "anything", null))
                .isEmpty();
    }

    @Test
    void storeReadFailureFallsBackToOriginalHistory() {
        when(store.findMessages(anyString(), anyInt()))
                .thenThrow(new RuntimeException("backend down"));
        List<ChatMessageItem> history = recentHistory();

        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "any query", history);

        assertThat(out).isSameAs(history);
    }

    @Test
    void skipsPersistedRowsWithBlankRoleOrContent() {
        // Inject malformed rows from the store; retriever must tolerate
        // them without crashing.
        agent.setChatMemoryRecentN(3);
        agent.setChatMemoryRelevanceTopK(2);

        List<Map<String, Object>> persisted = new ArrayList<>();
        persisted.add(turn("user", "real query about coupons", BASE_TIME));
        persisted.add(turn(null, "content with null role", BASE_TIME.plusSeconds(60)));
        persisted.add(turn("user", null, BASE_TIME.plusSeconds(120)));
        persisted.add(turn("", "", BASE_TIME.plusSeconds(180)));
        for (int i = 4; i < 10; i++) {
            persisted.add(turn("assistant", "filler " + i,
                    BASE_TIME.plusSeconds(60L * i)));
        }
        when(store.findMessages(CONV_ID, agent.getChatMemoryMaxMessages())).thenReturn(persisted);

        List<ChatMessageItem> history = recentHistory();
        List<ChatMessageItem> out = retriever.enrich(
                agent, CONV_ID, "remind me about coupons", history);

        // At least the one good row should have surfaced
        assertThat(out).hasSizeGreaterThan(history.size());
        assertThat(out.get(0).content()).contains("coupons");
    }

    @Test
    void markupHasFallbackWhenTimestampMissing() {
        TurChatMemoryRelevanceRetriever.PersistedMessage m =
                new TurChatMemoryRelevanceRetriever.PersistedMessage(
                        0, "user", "hello", null);
        assertThat(TurChatMemoryRelevanceRetriever.markup(m))
                .isEqualTo("[earlier turn #1] hello");
    }

    @Test
    void markupNormalisesIsoTimestamp() {
        TurChatMemoryRelevanceRetriever.PersistedMessage m =
                new TurChatMemoryRelevanceRetriever.PersistedMessage(
                        4, "assistant", "ok", "2026-05-01T10:00:00Z");
        assertThat(TurChatMemoryRelevanceRetriever.markup(m))
                .isEqualTo("[earlier turn #5 at 2026-05-01T10:00:00Z] ok");
    }

    @Test
    void markupHandlesEpochMillis() {
        long epoch = Instant.parse("2026-05-01T10:00:00Z").toEpochMilli();
        TurChatMemoryRelevanceRetriever.PersistedMessage m =
                new TurChatMemoryRelevanceRetriever.PersistedMessage(
                        7, "user", "epoch ts", Long.toString(epoch));
        assertThat(TurChatMemoryRelevanceRetriever.markup(m))
                .isEqualTo("[earlier turn #8 at 2026-05-01T10:00:00Z] epoch ts");
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private TurAIAgent newAgentWithRelevanceOn() {
        TurAIAgent a = new TurAIAgent();
        a.setId("agent-test-1");
        a.setTitle("Test Agent");
        a.setChatMemoryEnabled(true);
        a.setChatMemoryRelevanceEnabled(true);
        a.setChatMemoryMaxMessages(100);
        a.setChatMemoryRelevanceTopK(5);
        a.setChatMemoryRecentN(10);
        return a;
    }

    private List<ChatMessageItem> recentHistory() {
        List<ChatMessageItem> history = new ArrayList<>();
        history.add(new ChatMessageItem("user", "current question"));
        return Collections.unmodifiableList(history);
    }

    private List<Map<String, Object>> persistedTurns(int count, String content) {
        List<Map<String, Object>> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(turn(i % 2 == 0 ? "user" : "assistant",
                    content + " #" + i,
                    BASE_TIME.plusSeconds(60L * i)));
        }
        return out;
    }

    private static Map<String, Object> turn(String role, String content, Instant ts) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        m.put("timestamp", ts == null ? null : ts.toString());
        return m;
    }
}
