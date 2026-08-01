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

import java.util.Locale;

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

    public TurSNAnswerAPI(TurSNAnswerService answerService) {
        this.answerService = answerService;
    }

    @Operation(summary = "Stream a grounded, cited answer for a query over the site's knowledge base")
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> answer(@PathVariable String siteName,
            @RequestParam("q") String query,
            @RequestParam(value = "locale", required = false) String locale) {
        return answerService.answer(siteName, query, resolveLocale(locale));
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
