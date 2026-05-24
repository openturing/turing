/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.persona.TurPersona;

import lombok.extern.slf4j.Slf4j;

/**
 * Post-LLM check that enforces a {@link TurPersona}'s negative
 * constraints. Runs in {@code TurAgentChatExecutor} after the model
 * responds and before the executor emits its {@code ChatResponse}.
 *
 * <h2>Matching strategy (T22 / §IV.3)</h2>
 *
 * Tokenizes both the reply and each forbidden term through PT / EN / ES
 * Lucene analyzers and compares on the union of stems across all three.
 * Replaces the pre-T22 substring + regex check which missed morphological
 * variants — admins added "competitivo" to the forbidden list but the bot
 * could still say "competitividade", "competitiva", "competitivamente"
 * because none of those equaled the literal term.
 *
 * <p>The three-analyzer union is intentional: persona authors should not
 * have to declare a language to get morphology-aware enforcement. The
 * additional cost (3 tokenizations of short strings) is negligible
 * compared to the LLM round-trip the validator runs after.
 *
 * <p>The match is by-stem and a forbidden term hits when ANY of its
 * stems appear in the reply's stem set. Stem alignment also works across
 * languages: PT "carro" and ES "carro" stem the same way, so a bilingual
 * persona configured against either language catches the other.
 *
 * <h2>Masking</h2>
 *
 * On a hit, every character span in the reply that produced a matching
 * stem is replaced with {@link #MASK}. Spans from multiple analyzers
 * pointing at the same word are deduplicated, and overlapping spans are
 * merged before the substitution — the user sees one {@code [***]}, not
 * three concatenated masks.
 *
 * <p>Forbidden terms are pipe-separated in {@link TurPersona}'s
 * {@code forbiddenTerms} field (Lombok-generated {@code getForbiddenTerms()})
 * because individual terms may legitimately contain commas.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@Component
public class TurPersonaToneValidator {

    private static final String MASK = "[***]";

    /**
     * T22 / §IV.3 — analyzers used to tokenize both the response and each
     * forbidden term. The union of stems across all three matches
     * morphological variants in PT, EN, and ES without requiring the
     * persona to declare its language. Lucene {@link Analyzer} instances
     * are documented thread-safe; a single static list is shared.
     */
    private static final List<Analyzer> TONE_ANALYZERS = List.of(
            // BrazilianAnalyzer over PortugueseAnalyzer: the default PT
            // analyzer uses PortugueseLightStemFilter (plurals + gender
            // only) which leaves "competitivo" and "competitividade" with
            // different stems — the §IV.3 headline regression remains.
            // BrazilianAnalyzer ships the aggressive Snowball-style PT
            // stemmer that collapses adjective↔noun derivations
            // (competitiv- shared root), which is exactly what tone
            // validation wants. Trade-off: occasional over-matching on
            // unrelated words sharing a root prefix — acceptable because
            // forbidden-term hits are admin-curated and false positives
            // surface as visible [***] masks (loud-fail), not silent
            // wrong content.
            new BrazilianAnalyzer(),
            new EnglishAnalyzer(),
            new SpanishAnalyzer());

    /**
     * @param persona may be {@code null}; in that case the response passes
     *                through unchanged.
     */
    public TurPersonaValidationResult validate(TurPersona persona, String responseText) {
        if (responseText == null) {
            return TurPersonaValidationResult.passed("");
        }
        if (persona == null || !StringUtils.hasText(persona.getForbiddenTerms())) {
            return TurPersonaValidationResult.passed(responseText);
        }

        List<String> terms = parseTerms(persona.getForbiddenTerms());
        if (terms.isEmpty()) {
            return TurPersonaValidationResult.passed(responseText);
        }

        // Tokenize the response ONCE — three analyzers' worth of stems +
        // their original character offsets, so multiple forbidden terms
        // can be checked against the same data without re-tokenizing.
        List<TokenSpan> replyTokens = tokenizeWithOffsets(responseText);
        Set<String> replyStems = new HashSet<>();
        for (TokenSpan span : replyTokens) {
            replyStems.add(span.stem());
        }

        List<String> violations = new ArrayList<>();
        List<int[]> rangesToMask = new ArrayList<>();
        for (String term : terms) {
            collectHitRanges(term, replyTokens, replyStems, violations, rangesToMask);
        }

        if (violations.isEmpty()) {
            return TurPersonaValidationResult.passed(responseText);
        }

        String sanitized = applyMasks(responseText, rangesToMask);
        log.warn("[Persona] '{}' forbidden-term hits: {}", persona.getName(), violations);
        return new TurPersonaValidationResult(false, sanitized, violations);
    }

    /**
     * For one forbidden {@code term}, computes its stem set across PT/EN/ES,
     * intersects with the pre-computed {@code replyStems}, and on any hit
     * appends:
     *   - the term to {@code violations} (preserves admin's literal config
     *     for the audit log line);
     *   - every reply-side character range whose stem matched into
     *     {@code rangesToMask}.
     *
     * <p>Skips silently when the term tokenizes to an empty stem set —
     * that's typically a symbols-only / stopword-only admin entry which
     * would otherwise match every reply. Logged at DEBUG since it's a
     * configuration smell, not a runtime error.
     */
    private static void collectHitRanges(String term,
            List<TokenSpan> replyTokens,
            Set<String> replyStems,
            List<String> violations,
            List<int[]> rangesToMask) {
        Set<String> termStems = tokenizeStems(term);
        if (termStems.isEmpty()) {
            log.debug("[Persona] forbidden term '{}' tokenized to empty stem set — skipping", term);
            return;
        }
        Set<String> intersection = new HashSet<>(termStems);
        intersection.retainAll(replyStems);
        if (intersection.isEmpty()) {
            return;
        }
        violations.add(term);
        for (TokenSpan span : replyTokens) {
            if (intersection.contains(span.stem())) {
                rangesToMask.add(new int[] { span.start(), span.end() });
            }
        }
    }

    /**
     * Drives every {@link #TONE_ANALYZERS analyzer} over {@code text},
     * collecting {@link TokenSpan}s that carry both the stem and the
     * source character offsets. Multi-analyzer tokenization on the same
     * source text necessarily produces duplicates (PT/EN/ES often emit
     * identical stems for shared Latin roots) — that's fine; the caller
     * deduplicates by character range when masking.
     */
    private static List<TokenSpan> tokenizeWithOffsets(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<TokenSpan> out = new ArrayList<>();
        for (Analyzer analyzer : TONE_ANALYZERS) {
            try (TokenStream stream = analyzer.tokenStream("t", text)) {
                CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
                OffsetAttribute offset = stream.addAttribute(OffsetAttribute.class);
                stream.reset();
                while (stream.incrementToken()) {
                    String stem = term.toString();
                    if (!stem.isBlank()) {
                        out.add(new TokenSpan(stem, offset.startOffset(), offset.endOffset()));
                    }
                }
                stream.end();
            } catch (IOException e) {
                log.warn("[Persona] tokenizeWithOffsets failed ({}): {}",
                        analyzer.getClass().getSimpleName(), e.getMessage());
            }
        }
        return out;
    }

    /**
     * Same as {@link #tokenizeWithOffsets} but discards positions — used
     * for the forbidden term itself, where we only need the stem set
     * (not the source offsets within the term).
     */
    private static Set<String> tokenizeStems(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (Analyzer analyzer : TONE_ANALYZERS) {
            try (TokenStream stream = analyzer.tokenStream("t", text)) {
                CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
                stream.reset();
                while (stream.incrementToken()) {
                    String stem = term.toString();
                    if (!stem.isBlank()) {
                        out.add(stem);
                    }
                }
                stream.end();
            } catch (IOException e) {
                log.warn("[Persona] tokenizeStems failed ({}): {}",
                        analyzer.getClass().getSimpleName(), e.getMessage());
            }
        }
        return out;
    }

    /**
     * Replaces each character range in {@code rangesToMask} with
     * {@link #MASK}, merging overlapping/adjacent ranges first so a word
     * matched by multiple analyzers (or by multiple terms) yields a
     * single {@code [***]} in the output, not several side-by-side.
     *
     * <p>Substitutions are applied from the END of the string toward the
     * start so each replacement doesn't shift offsets the next one needs.
     */
    private static String applyMasks(String text, List<int[]> rangesToMask) {
        if (rangesToMask.isEmpty()) {
            return text;
        }
        // Sort ascending by start; rangesToMask was built in token order
        // which is mostly sorted already, but multi-analyzer cross-passes
        // can put ES stems after PT ones for the same offset — re-sort
        // defensively.
        List<int[]> sorted = new ArrayList<>(rangesToMask);
        sorted.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] range : sorted) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                // Overlap (or touches) the previous range — extend it.
                merged.get(merged.size() - 1)[1] = Math.max(
                        merged.get(merged.size() - 1)[1], range[1]);
            } else {
                merged.add(new int[] { range[0], range[1] });
            }
        }
        StringBuilder result = new StringBuilder(text);
        for (int i = merged.size() - 1; i >= 0; i--) {
            int[] range = merged.get(i);
            // Clip to valid bounds — OffsetAttribute is well-behaved but
            // a future analyzer with synthetic positions could over-shoot.
            int start = Math.max(0, range[0]);
            int end = Math.min(result.length(), range[1]);
            if (start < end) {
                result.replace(start, end, MASK);
            }
        }
        return result.toString();
    }

    private static List<String> parseTerms(String raw) {
        List<String> out = new ArrayList<>();
        for (String t : raw.split("\\|")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Stem + original character offsets in the source text. */
    private record TokenSpan(String stem, int start, int end) {
    }
}
