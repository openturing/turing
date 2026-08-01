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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.citation.TurAnswerCitation;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

import lombok.extern.slf4j.Slf4j;

/**
 * T834 / §LXII.3 — generates the 3–5 follow-up questions streamed as the answer's
 * {@code followups} event. One cheap LLM pass over the answer + its citations,
 * cached on {@code (site, query, source set)} through {@link TurLlmSummaryService}'s
 * cache discipline so a repeated query costs nothing.
 *
 * <p>Fail-open by construction: no default LLM, an LLM error, or an empty reply all
 * degrade to no chips — never a broken answer stream. Distinct from
 * {@code use-turing-related-terms} (search-term co-occurrence) and from Gemini's
 * {@code searchSuggestions} (Google-mandated, web-only): these are questions a
 * reader of <em>this</em> answer would naturally ask next.
 *
 * <p>Follows the ROADMAP Non-goal on structured output — the model returns one
 * question per line (not JSON), parsed by {@link #parseQuestions}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurFollowUpQuestionService {

    /** Hard cap on returned questions (the prompt asks for 3–5). */
    private static final int MAX_QUESTIONS = 5;
    /** Cap on citation titles fed to the prompt so the call stays cheap. */
    private static final int MAX_CITATIONS_IN_PROMPT = 8;

    private static final String SYSTEM_PROMPT = """
            You suggest follow-up questions a user might naturally ask next, given a \
            question and the answer it received.
            Rules:
            - Return between 3 and 5 questions.
            - Output ONE question per line. No numbering, no bullets, no preamble, no blank lines.
            - Each question must be self-contained and answerable from the same knowledge base.
            - Keep each question concise (under about 12 words).
            - Do not repeat the original question.""";

    private final TurLlmSummaryService llmSummaryService;

    public TurFollowUpQuestionService(TurLlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    /**
     * Generates follow-up questions for a completed answer.
     *
     * @param siteName  the SN site (namespaces the cache key)
     * @param query     the original user question
     * @param answer    the synthesized answer text
     * @param citations the answer's numbered citations (may be empty)
     * @return 3–5 questions, or an empty list when unavailable / on any failure
     */
    public List<String> generate(String siteName, String query, String answer,
            List<TurAnswerCitation> citations) {
        if (answer == null || answer.isBlank() || !llmSummaryService.isAvailable()) {
            return List.of();
        }
        List<TurAnswerCitation> safeCitations = citations == null ? List.of() : citations;
        try {
            SummaryResult result = llmSummaryService.generate(
                    cacheKey(siteName, query, safeCitations),
                    buildData(query, answer, safeCitations),
                    SYSTEM_PROMPT, false);
            if (!result.success() || result.content() == null) {
                return List.of();
            }
            return parseQuestions(result.content(), query);
        } catch (RuntimeException e) {
            log.warn("[FollowUp] generation failed for site '{}': {}", siteName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Cache key over {@code (site, query, source set)} — the source ids are sorted
     * so the same answer over the same evidence always hits, regardless of the
     * order the sources happened to arrive in.
     */
    private static String cacheKey(String siteName, String query, List<TurAnswerCitation> citations) {
        String sourceSet = citations.stream()
                .map(TurAnswerCitation::sourceId)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.joining(","));
        return "followups:" + safe(siteName) + ":" + safe(query) + ":" + sourceSet;
    }

    private static String buildData(String query, String answer, List<TurAnswerCitation> citations) {
        StringBuilder data = new StringBuilder();
        data.append("QUESTION:\n").append(safe(query)).append("\n\n");
        data.append("ANSWER:\n").append(answer.strip()).append("\n\n");
        if (!citations.isEmpty()) {
            data.append("SOURCES:\n");
            citations.stream().limit(MAX_CITATIONS_IN_PROMPT).forEach(c -> data.append("- ")
                    .append(c.title() == null || c.title().isBlank() ? c.sourceId() : c.title())
                    .append('\n'));
        }
        return data.toString();
    }

    /**
     * Parses the model's one-question-per-line reply into a bounded, de-duplicated
     * list, stripping any stray bullet/numbering the model added and dropping a
     * question that merely echoes the original.
     */
    static List<String> parseQuestions(String content, String query) {
        String original = safe(query);
        return content.lines()
                .map(TurFollowUpQuestionService::stripLeadingMarker)
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.equalsIgnoreCase(original))
                .distinct()
                .limit(MAX_QUESTIONS)
                .toList();
    }

    /** Removes a leading "1.", "1)", "- ", "* " or "• " the model may have added. */
    private static String stripLeadingMarker(String line) {
        return line.replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
