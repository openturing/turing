/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.citation.TurAnswerCitation;
import com.viglet.turing.genai.rag.TurRagSource;

import org.springframework.http.codec.ServerSentEvent;

import reactor.core.publisher.Flux;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T833 / §LXII.2 — coverage for {@link TurAnswerStreamFramer}, the pure transform
 * that re-frames the internal RAG chat stream into the answer-engine SSE contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerStreamFramerTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static String sourcesJson(TurRagSource... sources) {
        return MAPPER.writeValueAsString(List.of(sources));
    }

    private static List<ServerSentEvent<String>> frame(Flux<ChatResponse> upstream) {
        return frame(upstream, TurFollowUpGenerator.NONE);
    }

    private static List<ServerSentEvent<String>> frame(Flux<ChatResponse> upstream,
            TurFollowUpGenerator generator) {
        return TurAnswerStreamFramer.frame(upstream, generator).collectList().block();
    }

    private static ChatResponse token(String text) {
        return new ChatResponse("assistant", text, "token");
    }

    @Test
    void emitsSourcesFirstThenTokensThenTrailingCitationsAndTraceDroppingSideChannels() {
        String sources = sourcesJson(
                new TurRagSource("a", "A", "ua", null, 0.9, false),
                new TurRagSource("b", "B", "ub", null, 0.7, false));
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", sources, "sources"),
                token("Hello "),
                token("world"),
                new ChatResponse("assistant", "internal", "grounding"), // dropped
                new ChatResponse("assistant", "[\"co-occurrence\"]", "options")); // dropped (T834)

        List<ServerSentEvent<String>> events = frame(upstream);

        // No follow-ups from TurFollowUpGenerator.NONE, and the upstream side-channels
        // (grounding, the SN path's co-occurrence "options") are dropped.
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("sources", "token", "token", "citations", "trace");
        // sources numbered 1..k
        assertThat(ranks(events, "sources")).containsExactly(1, 2);
        // tokens carry the raw text
        assertThat(events.get(1).data()).isEqualTo("Hello ");
        assertThat(events.get(2).data()).isEqualTo("world");
        // citations mirror the numbered sources
        List<TurAnswerCitation> citations = citations(events);
        assertThat(citations).extracting(TurAnswerCitation::rank).containsExactly(1, 2);
        assertThat(citations).extracting(TurAnswerCitation::sourceId).containsExactly("a", "b");
        // trace reports the honest counts (answer = "Hello world" = 11 chars)
        TurAnswerTraceEvent trace = MAPPER.readValue(byEvent(events, "trace").data(),
                TurAnswerTraceEvent.class);
        assertThat(trace.sourceCount()).isEqualTo(2);
        assertThat(trace.answerLength()).isEqualTo(11);
    }

    @Test
    void forwardsClientToolCallAsAnArtifactEvent() {
        String artifact = "{\"callId\":\"c1\",\"name\":\"comparison_table\",\"args\":\"{}\"}";
        Flux<ChatResponse> upstream = Flux.just(
                token("Here is a comparison: "),
                new ChatResponse("assistant", artifact, "client_tool_call"));

        List<ServerSentEvent<String>> events = frame(upstream);

        // The structured artifact is forwarded (in order) as an `artifact` event.
        assertThat(events).extracting(ServerSentEvent::event).contains("artifact");
        assertThat(byEvent(events, "artifact").data()).isEqualTo(artifact);
    }

    @Test
    void forwardsToolCallAsATraceStepEvent() {
        String step = "{\"callId\":\"t1\",\"name\":\"search_knowledge_base\",\"phase\":\"start\"}";
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", step, "tool_call"),
                token("Answer."));

        List<ServerSentEvent<String>> events = frame(upstream);

        assertThat(events).extracting(ServerSentEvent::event).contains("trace_step");
        assertThat(byEvent(events, "trace_step").data()).isEqualTo(step);
    }

    @Test
    void traceCarriesRetrievalProvenance() {
        String sources = sourcesJson(new TurRagSource("a", "A", "ua", null, 0.9, false));
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", sources, "sources"),
                token("Answer."));

        List<ServerSentEvent<String>> events = TurAnswerStreamFramer.frame(upstream,
                TurFollowUpGenerator.NONE, new TurAnswerProvenance("HYBRID", "COHERE", true))
                .collectList().block();

        TurAnswerTraceEvent trace = MAPPER.readValue(byEvent(events, "trace").data(),
                TurAnswerTraceEvent.class);
        assertThat(trace.kbMode()).isEqualTo("HYBRID");
        assertThat(trace.rerankStrategy()).isEqualTo("COHERE");
        assertThat(trace.rerankEnabled()).isTrue();
        assertThat(trace.sourceCount()).isEqualTo(1);
    }

    @Test
    void emitsGeneratedFollowupsBetweenCitationsAndTrace() {
        String sources = sourcesJson(new TurRagSource("a", "A", "ua", null, 0.9, false));
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", sources, "sources"),
                token("Answer."));

        List<ServerSentEvent<String>> events =
                frame(upstream, (answer, citations) -> List.of("What about X?", "And Y?"));

        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("sources", "token", "citations", "followups", "trace");
        List<String> questions = MAPPER.readValue(byEvent(events, "followups").data(),
                new TypeReference<List<String>>() {
                });
        assertThat(questions).containsExactly("What about X?", "And Y?");
    }

    @Test
    void followupGeneratorFailureDegradesToNoChips() {
        String sources = sourcesJson(new TurRagSource("a", "A", "ua", null, 0.9, false));
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", sources, "sources"),
                token("Answer."));

        List<ServerSentEvent<String>> events = frame(upstream, (answer, citations) -> {
            throw new IllegalStateException("boom");
        });

        assertThat(events).extracting(ServerSentEvent::event).doesNotContain("followups");
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("sources", "token", "citations", "trace");
    }

    @Test
    void citationsRealignToTheMarkersTheAnswerActuallyEmitted() {
        String sources = sourcesJson(
                new TurRagSource("a", "A", "ua", null, 0.9, false),
                new TurRagSource("b", "B", "ub", null, 0.7, false));
        // The answer cites source #2 before source #1 -> footnotes must follow the
        // answer's order, so b becomes [1] and a becomes [2].
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", sources, "sources"),
                token("See [2] then [1]."));

        List<TurAnswerCitation> citations = citations(frame(upstream));

        assertThat(citations).extracting(TurAnswerCitation::sourceId).containsExactly("b", "a");
        assertThat(citations).extracting(TurAnswerCitation::rank).containsExactly(1, 2);
    }

    @Test
    void marklessAnswerKeepsEverySourcePositionallyNumbered() {
        String sources = sourcesJson(
                new TurRagSource("a", "A", "ua", null, 0.9, false),
                new TurRagSource("b", "B", "ub", null, 0.7, false),
                new TurRagSource("c", "C", "uc", null, 0.5, false));
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", sources, "sources"),
                token("A plain answer with no citation markers."));

        List<TurAnswerCitation> citations = citations(frame(upstream));

        // No truncation to a smaller top-k: all three sources survive, numbered 1..3.
        assertThat(citations).extracting(TurAnswerCitation::rank).containsExactly(1, 2, 3);
    }

    @Test
    void unparseableSourcesArePassedThroughWithNoCitations() {
        Flux<ChatResponse> upstream = Flux.just(
                new ChatResponse("assistant", "not json", "sources"),
                token("Answer."));

        List<ServerSentEvent<String>> events = frame(upstream);

        assertThat(byEvent(events, "sources").data()).isEqualTo("not json");
        assertThat(citations(events)).isEmpty();
    }

    @Test
    void emptyTokensAreDropped() {
        Flux<ChatResponse> upstream = Flux.just(token(""), token("real"));

        List<ServerSentEvent<String>> events = frame(upstream);

        assertThat(events).filteredOn(e -> "token".equals(e.event())).hasSize(1);
    }

    @Test
    void errorEventCarriesTheMessage() {
        ServerSentEvent<String> error = TurAnswerStreamFramer.errorEvent("boom");
        assertThat(error.event()).isEqualTo("error");
        assertThat(error.data()).contains("boom");
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static ServerSentEvent<String> byEvent(List<ServerSentEvent<String>> events, String name) {
        return events.stream().filter(e -> name.equals(e.event())).findFirst().orElseThrow();
    }

    private static List<Integer> ranks(List<ServerSentEvent<String>> events, String eventName) {
        List<TurRagSource> sources = MAPPER.readValue(byEvent(events, eventName).data(),
                new TypeReference<List<TurRagSource>>() {
                });
        return sources.stream().map(TurRagSource::rank).toList();
    }

    private static List<TurAnswerCitation> citations(List<ServerSentEvent<String>> events) {
        return MAPPER.readValue(byEvent(events, "citations").data(),
                new TypeReference<List<TurAnswerCitation>>() {
                });
    }
}
