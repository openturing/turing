/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.callcenter;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.nativeapi.voice.TurVoiceTranslationService;
import com.viglet.turing.mcp.server.TurMcpRagToolService;
import com.viglet.turing.system.TurLlmSummaryService;

import lombok.extern.slf4j.Slf4j;

/**
 * T189 / §X.15.c — real-time multilingual call-center augmentation. While an
 * operator handles a live call, the customer's utterance (often in another
 * language) is turned into a cited answer the operator reads on screen, in one
 * call:
 *
 * <ol>
 *   <li><b>Understand</b> — translate the customer's utterance into the
 *       operator's working language (T150 {@link TurVoiceTranslationService});
 *       skipped when the languages already match.</li>
 *   <li><b>Answer</b> — run the cited RAG path over the customer's index
 *       (T248 {@link TurMcpRagToolService#ragAnswer}) so the operator gets a
 *       grounded answer with a source list, not a guess.</li>
 *   <li><b>Relay</b> — translate that answer back into the customer's language
 *       so the operator can read it aloud verbatim; skipped when the languages
 *       match.</li>
 * </ol>
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: every step delegates to a shipped,
 * production-tested service — the simultaneous-interpreter translation (T150),
 * the multi-engine cited RAG answer (T248), and the default-LLM availability
 * gate ({@link TurLlmSummaryService}). This service only orchestrates the
 * translate → answer → translate-back loop and degrades fail-soft: a failed
 * translation falls back to the untranslated text (the operator still gets a
 * usable answer) rather than aborting the assist.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCallCenterAssistService {

    private static final String DEFAULT_OPERATOR_LANG = "English";

    private final TurVoiceTranslationService translationService;
    private final TurMcpRagToolService ragToolService;
    private final TurLlmSummaryService llmSummaryService;

    public TurCallCenterAssistService(TurVoiceTranslationService translationService,
            TurMcpRagToolService ragToolService,
            TurLlmSummaryService llmSummaryService) {
        this.translationService = translationService;
        this.ragToolService = ragToolService;
        this.llmSummaryService = llmSummaryService;
    }

    /**
     * One augmentation turn for an operator: translate the customer's utterance,
     * answer it from the index with citations, and translate the answer back.
     *
     * @param site             the SN site / index to ground the answer in (required)
     * @param siteLocale       the index locale to search (optional; {@code rag_answer} defaults it)
     * @param customerUtterance what the customer said, already transcribed (required)
     * @param customerLang     the customer's language for back-translation (optional — when
     *                         blank or equal to {@code operatorLang}, no translation happens)
     * @param operatorLang     the operator's working language for the query + cited answer
     *                         (optional; defaults to English)
     * @param maxSources       passed through to {@code rag_answer} (1-20)
     */
    public CallAssistResult assist(String site, String siteLocale, String customerUtterance,
            String customerLang, String operatorLang, Integer maxSources) {
        if (!StringUtils.hasText(site)) {
            return CallAssistResult.error("'site' is required.");
        }
        if (!StringUtils.hasText(customerUtterance)) {
            return CallAssistResult.error("'customerUtterance' is required.");
        }
        String opLang = StringUtils.hasText(operatorLang) ? operatorLang.trim() : DEFAULT_OPERATOR_LANG;
        boolean crossLingual = StringUtils.hasText(customerLang)
                && !customerLang.trim().equalsIgnoreCase(opLang);

        // 1. Understand — bring the utterance into the operator's working language.
        String operatorQuery = customerUtterance;
        boolean translatedQuery = false;
        if (crossLingual) {
            String t = translateSafe(opLang, customerLang, customerUtterance);
            if (t != null) {
                operatorQuery = t;
                translatedQuery = true;
            }
        }

        // 2. Answer — cited RAG over the customer's index (graceful: degrades to
        // a ranked source list when no default LLM is configured).
        String answer = ragToolService.ragAnswer(site, siteLocale, operatorQuery, maxSources);

        // 3. Relay — translate the answer back so the operator can read it to the
        // customer. Best-effort; null when same language or translation fails.
        String answerForCustomer = null;
        if (crossLingual && StringUtils.hasText(answer)) {
            answerForCustomer = translateSafe(customerLang.trim(), opLang, answer);
        }

        return new CallAssistResult(true, null, customerUtterance,
                StringUtils.hasText(customerLang) ? customerLang.trim() : null, opLang,
                operatorQuery, translatedQuery, answer, answerForCustomer, ragAvailable());
    }

    /** Translate fail-soft: returns {@code null} (caller keeps the original) on any failure. */
    private String translateSafe(String targetLang, String sourceLang, String text) {
        try {
            return translationService.translate(targetLang, sourceLang, text);
        } catch (RuntimeException e) {
            log.warn("[CallCenter] translation {}→{} failed, falling back to original: {}",
                    sourceLang, targetLang, e.getMessage());
            return null;
        }
    }

    private boolean ragAvailable() {
        try {
            return llmSummaryService.isAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The operator-facing result of one augmentation turn.
     *
     * @param success           false only on a bad request (missing site/utterance)
     * @param error             the reason when {@code success} is false, else null
     * @param customerUtterance the transcribed customer utterance (echoed back)
     * @param customerLang      the resolved customer language, or null when not cross-lingual
     * @param operatorLang      the operator's working language
     * @param operatorQuery     the query actually sent to the index (translated when needed)
     * @param translatedQuery   true when {@code operatorQuery} was translated from the utterance
     * @param answer            the cited answer in {@code operatorLang} (inline [n] + source list)
     * @param answerForCustomer the answer translated into {@code customerLang}, or null
     * @param ragAvailable      whether a default LLM is configured (false → answer is sources-only)
     */
    public record CallAssistResult(boolean success, String error, String customerUtterance,
            String customerLang, String operatorLang, String operatorQuery, boolean translatedQuery,
            String answer, String answerForCustomer, boolean ragAvailable) {

        static CallAssistResult error(String message) {
            return new CallAssistResult(false, message, null, null, null, null, false, null, null,
                    false);
        }
    }
}
