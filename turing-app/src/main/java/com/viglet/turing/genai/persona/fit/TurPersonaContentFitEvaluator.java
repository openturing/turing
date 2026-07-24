/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.genai.persona.readability.TurReadabilityResult;
import com.viglet.turing.genai.persona.readability.TurReadabilityScorer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaSource;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Content-fit evaluator (Block AA / §XXVI.4): the LLM role-plays the persona
 * <em>as a reader</em> and, grounded strictly in the source text, returns a
 * structured verdict (fit %, what condiz / what não condiz, flagged spans with
 * rewrites). The final score <strong>fuses</strong> the deterministic
 * {@link TurReadabilityScorer} sub-score (hard signal) with the LLM's grounded
 * judgment — neither alone is enough.
 *
 * <p>Reuses {@link TurLlmSummaryService} for the default-LLM resolution, the
 * cache (keyed by persona + source + content hash) and token accounting; the
 * JSON contract is baked into the system prompt (the portable, provider-neutral
 * structured-output recipe used elsewhere in this codebase). A grounding
 * hard-fail (à la T385) drops any flagged span not present verbatim in the
 * source, so the LLM cannot invent problems. When the LLM is unavailable or its
 * output cannot be parsed, the score degrades gracefully to readability-only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaContentFitEvaluator {

    /** How much of the fused score comes from the deterministic readability scorer. */
    private static final double READABILITY_WEIGHT = 0.5;
    private static final int MAX_SOURCE_CHARS = 12_000;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private final TurReadabilityScorer readabilityScorer;
    private final TurLlmSummaryService llmSummaryService;

    public TurPersonaContentFitEvaluator(TurReadabilityScorer readabilityScorer,
            TurLlmSummaryService llmSummaryService) {
        this.readabilityScorer = readabilityScorer;
        this.llmSummaryService = llmSummaryService;
    }

    /** Evaluate one notebook source against the persona. */
    public TurContentFitResult evaluate(TurPersona persona, TurPersonaSource source,
            boolean regenerate) {
        String text = source == null ? null : source.getCachedText();
        String sourceId = source == null ? null : source.getId();
        String sourceName = source == null ? null : source.getSourceName();
        return evaluateText(persona, text, sourceId, sourceName, regenerate);
    }

    /** Evaluate a raw piece of text against the persona's audience facet. */
    public TurContentFitResult evaluateText(TurPersona persona, String text,
            String sourceId, String sourceName, boolean regenerate) {
        TurPersonaAudience audience = persona == null ? null : persona.getAudience();
        TurReadabilityResult readability = readabilityScorer.score(text, audience);
        double readabilityScore = readability.fitScore();

        if (StringUtils.isBlank(text)) {
            return new TurContentFitResult(readabilityScore, "No text to evaluate.",
                    List.of(), List.of(), readabilityScore, readability.metrics(),
                    false, "Source has no extracted text yet.", false,
                    sourceId, sourceName);
        }

        String clipped = text.length() > MAX_SOURCE_CHARS
                ? text.substring(0, MAX_SOURCE_CHARS) : text;
        String cacheKey = "persona-fit:" + (persona == null ? "?" : persona.getId())
                + ":" + sourceId + ":" + Integer.toHexString(clipped.hashCode());

        SummaryResult llm = llmSummaryService.generate(cacheKey, clipped,
                buildSystemPrompt(persona, audience), regenerate);

        if (!llm.success() || StringUtils.isBlank(llm.content())) {
            // LLM unavailable / errored → degrade to the deterministic score.
            return new TurContentFitResult(readabilityScore,
                    "Deterministic readability score only (LLM unavailable).",
                    List.of(), List.of(), readabilityScore, readability.metrics(),
                    false, llm.error(), llm.canRegenerate(), sourceId, sourceName);
        }

        LlmVerdict verdict = parse(llm.content());
        if (verdict == null) {
            return new TurContentFitResult(readabilityScore,
                    "Deterministic readability score only (could not parse LLM verdict).",
                    List.of(), List.of(), readabilityScore, readability.metrics(),
                    false, "Could not parse LLM verdict.", llm.canRegenerate(),
                    sourceId, sourceName);
        }

        List<TurContentFitMisfit> misfits = groundMisfits(verdict.misfits(), text);
        List<String> fits = verdict.fits() == null ? List.of() : verdict.fits();
        double llmScore = clamp(verdict.fitScore() == null ? readabilityScore
                : verdict.fitScore());
        double fused = round(READABILITY_WEIGHT * readabilityScore
                + (1 - READABILITY_WEIGHT) * llmScore);

        return new TurContentFitResult(fused,
                StringUtils.defaultString(verdict.summary()), fits, misfits,
                readabilityScore, readability.metrics(), true, null,
                llm.canRegenerate(), sourceId, sourceName);
    }

    // ---- grounding ---------------------------------------------------------

    /** Drop any flagged span not present verbatim in the source (T385 hard-fail). */
    private List<TurContentFitMisfit> groundMisfits(List<MisfitDto> raw, String source) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        String haystack = normalize(source);
        List<TurContentFitMisfit> grounded = new ArrayList<>();
        for (MisfitDto m : raw) {
            if (m == null || StringUtils.isBlank(m.span())) {
                continue;
            }
            if (haystack.contains(normalize(m.span()))) {
                grounded.add(new TurContentFitMisfit(m.span().strip(),
                        Objects.toString(m.reason(), "tone"), m.suggestion()));
            } else {
                log.debug("[ContentFit] dropping ungrounded misfit span: {}", m.span());
            }
        }
        return grounded;
    }

    private String normalize(String s) {
        return s.toLowerCase().replaceAll("\\s+", " ").strip();
    }

    // ---- prompt ------------------------------------------------------------

    private String buildSystemPrompt(TurPersona persona, TurPersonaAudience audience) {
        return """
                You evaluate whether a document is appropriate for a specific
                READER (audience). Role-play this audience reading the document
                and judge it from their perspective.

                AUDIENCE:
                %s

                Return EXACTLY ONE JSON object on a single line — no prose, no
                code fences. Schema:
                {"fitScore": <0-100 integer>, "summary": "<one sentence>",
                 "fits": ["<short bullet of what suits this reader>"],
                 "misfits": [{"span": "<verbatim phrase copied from the document>",
                   "reason": "too-complex|jargon|tone|missing-context",
                   "suggestion": "<a simpler rewrite>"}]}

                HARD RULES:
                - Every "span" MUST be copied verbatim from the document. Never
                  invent a problem that is not literally in the text. If you
                  cannot ground a misfit in a verbatim span, omit it.
                - fitScore reflects how well the document suits THIS reader
                  (100 = perfect fit, 0 = wholly inappropriate).
                - Reply in the audience's primary language when set.
                """.formatted(describeAudience(persona, audience));
    }

    private String describeAudience(TurPersona persona, TurPersonaAudience audience) {
        StringBuilder sb = new StringBuilder();
        if (persona != null && StringUtils.isNotBlank(persona.getName())) {
            sb.append("- Name: ").append(persona.getName()).append('\n');
        }
        if (persona != null && StringUtils.isNotBlank(persona.getDescription())) {
            sb.append("- Description: ").append(persona.getDescription()).append('\n');
        }
        if (audience != null) {
            if (audience.getReadingLevel() != null) {
                sb.append("- Reading / education level: ")
                        .append(audience.getReadingLevel()).append('\n');
            }
            if (audience.getDomainExpertise() != null) {
                sb.append("- Domain expertise: ")
                        .append(audience.getDomainExpertise()).append('\n');
            }
            appendIfSet(sb, "Vocabulary ceiling", audience.getVocabularyCeiling());
            appendIfSet(sb, "Comprehension notes", audience.getComprehensionNotes());
            appendIfSet(sb, "Accessibility notes", audience.getAccessibilityNotes());
            appendIfSet(sb, "Primary language", audience.getPrimaryLanguage());
        }
        return sb.isEmpty() ? "- A general reader." : sb.toString();
    }

    private void appendIfSet(StringBuilder sb, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            sb.append("- ").append(label).append(": ").append(value).append('\n');
        }
    }

    // ---- parsing -----------------------------------------------------------

    private LlmVerdict parse(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LlmVerdict.class);
        } catch (RuntimeException e) {
            log.warn("[ContentFit] could not parse LLM verdict: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LlmVerdict(Integer fitScore, String summary, List<String> fits,
            List<MisfitDto> misfits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MisfitDto(String span, String reason, String suggestion) {
    }
}
