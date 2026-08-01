/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.sn.genai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.answer.TurSNAnswerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * T833 / §LXII.2 — the answer-first streaming endpoint. Composes retrieval →
 * rerank → synthesis over an SN site's knowledge base and streams the answer as
 * named SSE events ({@code sources} before the first token, then {@code token}s,
 * then {@code citations}, {@code followups} and {@code trace}).
 *
 * <p>Deliberately a {@code GET} on a distinct path from search and from the
 * conversational chat endpoint: the client fires the (cheap, cacheable) search and
 * the (LLM-latency) answer in parallel and composes them, so the hot search path
 * never pays for the answer. Anonymous-capable like the public AI-mode surfaces.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/{siteName}/answer")
@Tag(name = "Semantic Navigation Answer",
        description = "Answer-first streaming answer engine over an SN site's knowledge base")
public class TurSNAnswerAPI {

    private final TurSNAnswerService answerService;
    private final com.viglet.turing.genai.citation.TurCitationDriftService driftService;
    private final com.viglet.turing.genai.answer.TurAnswerShareService answerShareService;

    public TurSNAnswerAPI(TurSNAnswerService answerService,
            com.viglet.turing.genai.citation.TurCitationDriftService driftService,
            com.viglet.turing.genai.answer.TurAnswerShareService answerShareService) {
        this.answerService = answerService;
        this.driftService = driftService;
        this.answerShareService = answerShareService;
    }

    @Operation(summary = "Stream a grounded, cited answer for a query over the site's knowledge base")
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> answer(@PathVariable String siteName,
            @RequestParam("q") String query,
            @RequestParam(value = "locale", required = false) String locale,
            @RequestParam(value = "fq", required = false) List<String> fq) {
        return answerService.answer(siteName, query, resolveLocale(locale), parseFilters(fq));
    }

    /**
     * T840 / §LXII.9 — re-check the freshness of a produced answer's cited sources.
     * Given the original question, the instant the answer was produced and its cited
     * sources, returns per-source drift verdicts so the client can show a freshness
     * badge and offer a one-click re-answer. Fail-open: an empty list (no badge) when
     * drift detection is off or nothing re-checks. A safe read (no mutation).
     */
    @Operation(summary = "Re-check whether a produced answer's cited sources have drifted")
    @org.springframework.web.bind.annotation.PostMapping(value = "/freshness",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<com.viglet.turing.genai.citation.TurCitationDriftService.FreshnessVerdict> freshness(
            @PathVariable String siteName,
            @org.springframework.web.bind.annotation.RequestBody FreshnessRequest request) {
        if (request == null || request.sources() == null || request.sources().isEmpty()) {
            return List.of();
        }
        List<com.viglet.turing.genai.citation.TurCitationDriftService.FreshnessRef> refs =
                request.sources().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(s -> new com.viglet.turing.genai.citation.TurCitationDriftService.FreshnessRef(
                                s.sourceId(), s.citedText()))
                        .toList();
        int topK = request.topK() == null ? 0 : request.topK();
        return driftService.checkFreshness(request.q(), refs, request.answeredAt(), topK);
    }

    /** Body of {@code POST /freshness}: the question, when the answer ran, and its cited sources. */
    public record FreshnessRequest(String q, java.time.Instant answeredAt, Integer topK,
            List<FreshnessSource> sources) {
    }

    /** One cited source to re-check: its id and the grounded passage. */
    public record FreshnessSource(String sourceId, String citedText) {
    }

    // ─────────────────────────── Answer permalinks (T844) ───────────────────────────

    /**
     * T844 / §LXII.13 — mint a stable permalink for an answer by persisting its
     * snapshot (query + filter state + source snapshot + answer text + timestamp).
     * Returns the share id the client turns into a URL.
     */
    @Operation(summary = "Persist an answer snapshot and return its shareable permalink id")
    @org.springframework.web.bind.annotation.PostMapping(value = "/share",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ShareResponse createShare(@PathVariable String siteName,
            @org.springframework.web.bind.annotation.RequestBody ShareRequest request) {
        String id = answerShareService.create(siteName, request.q(), request.filtersJson(),
                request.answerText(), request.sourcesJson(), request.citationsJson(),
                request.answeredAt());
        return new ShareResponse(id);
    }

    /** T844 — read a shared answer snapshot back, so the SPA re-renders it read-only. */
    @Operation(summary = "Read a shared answer snapshot by its permalink id")
    @GetMapping(value = "/share/{shareId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public org.springframework.http.ResponseEntity<AnswerShareView> readShare(
            @PathVariable String siteName, @PathVariable String shareId) {
        return answerShareService.find(shareId)
                .map(share -> org.springframework.http.ResponseEntity.ok(new AnswerShareView(
                        share.getId(), share.getSiteName(), share.getQuery(), share.getFiltersJson(),
                        share.getAnswerText(), share.getSourcesJson(), share.getCitationsJson(),
                        share.getAnsweredAt())))
                .orElseGet(() -> org.springframework.http.ResponseEntity.notFound().build());
    }

    /**
     * T844 — the Open-Graph card for a shared answer (question as title, answer
     * excerpt as description), redirecting human visitors to {@code dest}. Scrapers
     * unfurl the meta tags; a 404 becomes an empty body.
     */
    @Operation(summary = "Render the Open-Graph card for a shared answer")
    @GetMapping(value = "/share/{shareId}/og", produces = MediaType.TEXT_HTML_VALUE)
    public org.springframework.http.ResponseEntity<String> shareOg(@PathVariable String siteName,
            @PathVariable String shareId,
            @RequestParam(value = "dest", required = false) String dest) {
        return answerShareService.renderOgCard(shareId, dest)
                .map(org.springframework.http.ResponseEntity::ok)
                .orElseGet(() -> org.springframework.http.ResponseEntity.notFound().build());
    }

    /** Body of {@code POST /share}: the answer snapshot to persist (JSON fields opaque). */
    public record ShareRequest(String q, String filtersJson, String answerText,
            String sourcesJson, String citationsJson, java.time.Instant answeredAt) {
    }

    /** Response of {@code POST /share}: the minted permalink id. */
    public record ShareResponse(String id) {
    }

    /** The shared-answer read model returned by {@code GET /share/{id}}. */
    public record AnswerShareView(String id, String siteName, String query, String filtersJson,
            String answerText, String sourcesJson, String citationsJson,
            java.time.Instant answeredAt) {
    }

    /**
     * T838 / §LXII.7 — parses the active facet filters (repeated {@code fq}
     * params, each {@code field:value}) into the {@code field → values} map the
     * answer engine synthesizes within. Splits on the first colon so a value may
     * itself contain colons; a malformed entry (no colon, blank field) is skipped.
     */
    private static Map<String, List<String>> parseFilters(List<String> fq) {
        if (fq == null || fq.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> filters = new LinkedHashMap<>();
        for (String entry : fq) {
            if (entry == null) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                continue;
            }
            String field = entry.substring(0, colon).trim();
            String value = entry.substring(colon + 1).trim();
            if (field.isEmpty() || value.isEmpty()) {
                continue;
            }
            filters.computeIfAbsent(field, k -> new java.util.ArrayList<>()).add(value);
        }
        return filters;
    }

    /** Parses the requested locale, defaulting to English when absent/invalid. */
    private static Locale resolveLocale(String requested) {
        if (requested == null || requested.isBlank()) {
            return Locale.ENGLISH;
        }
        try {
            return LocaleUtils.toLocale(requested.trim().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return Locale.ENGLISH;
        }
    }
}
