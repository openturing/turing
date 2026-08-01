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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.codec.ServerSentEvent;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.citation.TurAnswerCitation;
import com.viglet.turing.genai.citation.TurAnswerCitationNumberer;
import com.viglet.turing.genai.rag.TurRagSource;

import reactor.core.publisher.Flux;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T833 / §LXII.2 — re-frames the internal RAG chat stream
 * ({@code Flux<ChatResponse>} whose event name rides in the record's {@code type})
 * into the dedicated answer-engine SSE contract: named {@link ServerSentEvent}s
 * emitting, in order, {@code sources} (before the first token), then {@code token}s,
 * then {@code followups}, {@code citations} and {@code trace}.
 *
 * <p>The one guaranteed ordering is <b>sources before the first token</b> — the
 * client paints its source cards before any prose arrives. The three trailing
 * events ({@code citations}, {@code trace}, and any inline {@code followups}) are
 * keyed by event name, so their relative order is not contractual.
 *
 * <p>This is a pure transform (no Spring, no direct I/O) so it can be unit-tested
 * by feeding a canned upstream {@link Flux} and a stub follow-up generator. It
 * reuses the shared {@link TurAnswerCitationNumberer}: incoming sources are
 * numbered positionally ({@code 1..k}) so a chip carries a footnote, and the
 * trailing {@code citations} event re-aligns those footnotes to any {@code [n]}
 * markers the answer actually emitted (fail-open to the positional numbering when
 * the prose has none). The {@code followups} event is produced by the supplied
 * {@link TurFollowUpGenerator} (T834) once the answer is complete.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurAnswerStreamFramer {

    /** Answer-contract SSE event names (the {@code event:} field the client reads). */
    public static final String EVENT_SOURCES = "sources";
    public static final String EVENT_TOKEN = "token";
    public static final String EVENT_CITATIONS = "citations";
    public static final String EVENT_FOLLOWUPS = "followups";
    public static final String EVENT_TRACE = "trace";
    public static final String EVENT_ERROR = "error";
    /** T841 — a structured answer artifact (an answer-as-app generative-UI spec). */
    public static final String EVENT_ARTIFACT = "artifact";
    /** T843 — one step of the live research trace (a tool-call start/end). */
    public static final String EVENT_TRACE_STEP = "trace_step";

    /** Upstream {@link ChatResponse#type()} values this framer maps or drops. */
    private static final String UPSTREAM_SOURCES = "sources";
    private static final String UPSTREAM_TOKEN = "token";
    /** The agent's client-tool invocation — the answer-as-app render spec (T438/T841). */
    private static final String UPSTREAM_CLIENT_TOOL_CALL = "client_tool_call";
    /** A runtime tool-call start/end — the raw material for the research trace (T436/T843). */
    private static final String UPSTREAM_TOOL_CALL = "tool_call";

    /** Mirrors the catalog backstop so a runaway answer can't flood the client. */
    private static final int MAX_RETURNED_CITATIONS = 24;

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<TurRagSource>> SOURCE_LIST = new TypeReference<>() {
    };

    private TurAnswerStreamFramer() {
        // stateless transform
    }

    /**
     * Re-frames an internal chat stream into the answer-engine SSE contract.
     *
     * @param upstream          the {@code Flux<ChatResponse>} from the SN RAG path
     *                          (its {@code sources} event already ordered before the
     *                          tokens)
     * @param followUpGenerator produces the {@code followups} once the answer is
     *                          complete; use {@link TurFollowUpGenerator#NONE} for
     *                          none
     * @return a flux of named {@link ServerSentEvent}s, never {@code null}
     */
    public static Flux<ServerSentEvent<String>> frame(Flux<ChatResponse> upstream,
            TurFollowUpGenerator followUpGenerator) {
        return frame(upstream, followUpGenerator, TurAnswerProvenance.NONE);
    }

    /**
     * Re-frames the stream, folding the T839 retrieval {@code provenance} (KB mode,
     * rerank strategy) into the terminal {@code trace} event.
     */
    public static Flux<ServerSentEvent<String>> frame(Flux<ChatResponse> upstream,
            TurFollowUpGenerator followUpGenerator, TurAnswerProvenance provenance) {
        TurFollowUpGenerator generator =
                followUpGenerator == null ? TurFollowUpGenerator.NONE : followUpGenerator;
        TurAnswerProvenance prov = provenance == null ? TurAnswerProvenance.NONE : provenance;
        // concatMap keeps the stream strictly ordered, so these single-subscription
        // accumulators are only ever touched from one thread at a time.
        StringBuilder answer = new StringBuilder();
        List<TurRagSource> numberedSources = new ArrayList<>();
        return upstream
                .concatMap(response -> mapEvent(response, answer, numberedSources))
                .concatWith(Flux.defer(
                        () -> trailingEvents(answer.toString(), numberedSources, generator, prov)));
    }

    /** Builds the terminal {@code error} event carrying a user-facing message. */
    public static ServerSentEvent<String> errorEvent(String message) {
        return event(EVENT_ERROR, toJson(Map.of("message", message == null ? "" : message)));
    }

    private static Flux<ServerSentEvent<String>> mapEvent(ChatResponse response,
            StringBuilder answer, List<TurRagSource> numberedSources) {
        String type = response.type() == null ? UPSTREAM_TOKEN : response.type();
        String content = response.content() == null ? "" : response.content();
        switch (type) {
            case UPSTREAM_SOURCES -> {
                List<TurRagSource> numbered = numberSources(content);
                if (numbered.isEmpty()) {
                    // Unparseable — pass the original through so the client still
                    // gets its source cards; citations degrade to empty.
                    return Flux.just(event(EVENT_SOURCES, content));
                }
                numberedSources.clear();
                numberedSources.addAll(numbered);
                return Flux.just(event(EVENT_SOURCES, toJson(numbered)));
            }
            case UPSTREAM_TOKEN -> {
                if (content.isEmpty()) {
                    return Flux.empty();
                }
                answer.append(content);
                return Flux.just(event(EVENT_TOKEN, content));
            }
            case UPSTREAM_CLIENT_TOOL_CALL -> {
                // T841 — the agent rendered the answer as a structured artifact (an
                // answer-as-app comparison table / spec grid). Forward the render spec
                // so the client draws it and reduces the prose to a lead-in.
                if (content.isEmpty()) {
                    return Flux.empty();
                }
                return Flux.just(event(EVENT_ARTIFACT, content));
            }
            case UPSTREAM_TOOL_CALL -> {
                // T843 — the runtime already emits tool-call start/end events; frame
                // each as one step of the live research trace ("searched X · reranked
                // · answered") so a multi-second answer feels deliberate, not slow.
                if (content.isEmpty()) {
                    return Flux.empty();
                }
                return Flux.just(event(EVENT_TRACE_STEP, content));
            }
            default -> {
                // grounding / secondOpinion / form / tool_call — and the SN path's
                // co-occurrence "options" follow-ups, which T834 replaces with an
                // LLM pass over the answer — are conversation side-channels, not part
                // of a clean answer stream, so drop them.
                return Flux.empty();
            }
        }
    }

    private static Flux<ServerSentEvent<String>> trailingEvents(String answerText,
            List<TurRagSource> numberedSources, TurFollowUpGenerator followUpGenerator,
            TurAnswerProvenance provenance) {
        List<TurAnswerCitation> projected = numberedSources.stream()
                .map(TurAnswerCitation::fromRagSource)
                .toList();
        // Align footnotes to any [n] markers the answer emitted; fail-open (no
        // markers) keeps every source, positionally numbered. failOpenTopK is the
        // full size so the marker-less path never truncates the chip list.
        TurAnswerCitationNumberer.Numbered<TurAnswerCitation> aligned =
                TurAnswerCitationNumberer.renumberByMarkers(answerText, projected,
                        TurAnswerCitation::rank, TurAnswerCitation::withRank,
                        MAX_RETURNED_CITATIONS, projected.size());
        List<TurAnswerCitation> citations = aligned.citations();
        ServerSentEvent<String> citationsEvent = event(EVENT_CITATIONS, toJson(citations));
        ServerSentEvent<String> traceEvent = event(EVENT_TRACE,
                toJson(new TurAnswerTraceEvent(numberedSources.size(), answerText.length(),
                        provenance.kbMode(), provenance.rerankStrategy(), provenance.rerankEnabled())));
        // Emit citations immediately (instant), then the follow-ups — which cost an
        // LLM call and are generated lazily here so they never delay the citations —
        // then the trace. Follow-up generation is fail-open to no chips.
        return Flux.concat(
                Flux.just(citationsEvent),
                Flux.defer(() -> followUpEvents(answerText, citations, followUpGenerator)),
                Flux.just(traceEvent));
    }

    private static Flux<ServerSentEvent<String>> followUpEvents(String answerText,
            List<TurAnswerCitation> citations, TurFollowUpGenerator followUpGenerator) {
        List<String> questions;
        try {
            questions = followUpGenerator.generate(answerText, citations);
        } catch (RuntimeException e) {
            questions = List.of();
        }
        if (questions == null || questions.isEmpty()) {
            return Flux.empty();
        }
        return Flux.just(event(EVENT_FOLLOWUPS, toJson(questions)));
    }

    /** Parses the upstream sources JSON and assigns positional footnotes 1..k. */
    private static List<TurRagSource> numberSources(String json) {
        try {
            List<TurRagSource> parsed = MAPPER.readValue(json, SOURCE_LIST);
            return TurAnswerCitationNumberer.numberInOrder(parsed, TurRagSource::withRank);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static ServerSentEvent<String> event(String name, String data) {
        return ServerSentEvent.<String>builder(data).event(name).build();
    }

    private static String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (RuntimeException e) {
            return "null";
        }
    }
}
