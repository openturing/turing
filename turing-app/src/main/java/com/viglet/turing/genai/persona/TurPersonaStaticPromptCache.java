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

import java.util.ArrayList;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;

/**
 * T31 / §IV.5 — caches the deterministic, persona-only portion of the
 * system prompt composed by {@link TurPersonaPromptComposer}. The cached
 * block is:
 *
 * <pre>
 *   {persona.systemInstruction}
 *
 *   # Style Guidelines
 *   - Tone, Verbosity, Language Style
 *
 *   # Required Vocabulary      (when mandatoryTerms set)
 *   # Forbidden Vocabulary     (when forbiddenTerms set)
 *   # Brand Context            (placeholder when brandContextMcpServer set)
 * </pre>
 *
 * <p>The composer assembles the FULL prompt as
 * {@code staticBlock + fewShotBlock(userQuery) + basePrompt}. Of these
 * three, only the first is deterministic given the persona id — the
 * few-shot block depends on the user message (vector store retrieval) and
 * the base prompt carries per-turn RAG context. They stay uncached.
 *
 * <h2>Cache name &amp; key</h2>
 *
 * The {@code turPersonaStaticPrompt} cache is keyed by
 * {@link TurPersona#id persona id}. Eviction is wired in
 * {@link TurPersonaStaticPromptEvictionListener} (a JPA entity callback on
 * {@link TurPersona}), plus an explicit {@code @CacheEvict} on the bulk-DML
 * delete endpoint, so an operator editing the persona's instruction / tone /
 * vocabulary sees the change reflected on the next chat turn without an app
 * restart. (Block AC / T486 moved this off the now-uncached persona repository.)
 *
 * <h2>Why a separate bean</h2>
 *
 * Spring's {@code @Cacheable} relies on the proxy intercepting the call.
 * If {@code TurPersonaPromptComposer.compose(...)} invoked a cacheable
 * method via {@code this.x(...)}, the proxy would be bypassed and the
 * cache annotation would silently no-op. Lifting the cacheable surface to
 * a separate {@code @Component} means the composer's call goes through
 * Spring's proxy (a normal cross-bean invocation), so caching works.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurPersonaStaticPromptCache {

    /** Public cache name — evicted by {@link TurPersonaStaticPromptEvictionListener}. */
    public static final String CACHE_NAME = "turPersonaStaticPrompt";

    /**
     * Returns the persona-only block of the system prompt. The result is
     * cached under {@link #CACHE_NAME} keyed by the persona id; subsequent
     * turns for the same persona hit the cache and skip the string-builder
     * cost (a few ms per turn at typical persona size, scaling with the
     * size of {@code systemInstruction} and the term lists).
     *
     * <p>Returns an empty string when {@code persona} is null — callers
     * append the few-shot and base-prompt portions on top, so an empty
     * static block is a clean no-op rather than a special case.
     *
     * <p>The {@code unless} guard prevents caching the empty-string result
     * for null personas: there's nothing to memoize, and a single cached
     * entry per "no persona" call would silently mask the null-check.
     */
    @Cacheable(value = CACHE_NAME, key = "#persona.id",
            condition = "#persona != null && #persona.id != null",
            unless = "#result == null || #result.isEmpty()")
    public String composeStaticBlock(TurPersona persona) {
        if (persona == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, persona.getSystemInstruction());

        appendStyleGuidelines(sb, persona);
        appendPersonalityGuidelines(sb, persona);
        appendTermsBlock(sb, "# Required Vocabulary",
                "Naturally weave the following terms into your responses when they fit the topic:",
                persona.getMandatoryTerms());
        appendTermsBlock(sb, "# Forbidden Vocabulary",
                "Never use the following terms — rephrase if needed:",
                persona.getForbiddenTerms());

        if (persona.getBrandContextMcpServer() != null) {
            sb.append("\n\n# Brand Context\n");
            sb.append("You have access to brand-guideline tools provided by the '")
                    .append(persona.getBrandContextMcpServer().getTitle())
                    .append("' MCP server. Consult them whenever the user asks about brand voice, ");
            sb.append("messaging, or visual identity.");
        }
        return sb.toString();
    }

    private static void appendStyleGuidelines(StringBuilder sb, TurPersona persona) {
        TurPersonaTone tone = persona.getTone();
        TurPersonaLanguageStyle style = persona.getLanguageStyle();
        int verbosity = persona.getVerbosity();
        if (tone == null && style == null && verbosity == 0) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append("# Style Guidelines\n");
        if (tone != null) {
            sb.append("- Tone: ").append(tone.name().toLowerCase()).append('\n');
        }
        sb.append("- Verbosity (1=terse, 5=expansive): ")
                .append(clampVerbosity(verbosity)).append('\n');
        if (style != null) {
            sb.append("- Language Style: ").append(style.name().toLowerCase()).append('\n');
        }
    }

    /**
     * T717 / §XLVI.1 — renders set Big Five (OCEAN) traits as behavioral guidance.
     * Only traits that deviate from the neutral midpoint band ({@value #TRAIT_LOW}..
     * {@value #TRAIT_HIGH}) are emitted, so a persona reads as distinct on the
     * dimensions its author deliberately dialed up or down; an unset or mid-range
     * trait contributes nothing. When no trait deviates, the whole block is omitted,
     * keeping legacy personas byte-for-byte unchanged.
     */
    private static void appendPersonalityGuidelines(StringBuilder sb, TurPersona persona) {
        List<String> lines = new ArrayList<>();
        addTraitLine(lines, persona.getOpenness(),
                "intellectually curious — embrace novel ideas, abstractions, and tangents",
                "practical and conventional — prefer concrete, familiar, proven ideas");
        addTraitLine(lines, persona.getConscientiousness(),
                "organized and precise — give structured, thorough, carefully qualified answers",
                "spontaneous and casual — give loose, improvised, unstructured answers");
        addTraitLine(lines, persona.getExtraversion(),
                "outgoing and expressive — energetic, talkative, and forthcoming",
                "reserved and reflective — concise and measured, volunteering little");
        addTraitLine(lines, persona.getAgreeableness(),
                "warm and cooperative — accommodating and eager to avoid conflict",
                "blunt and skeptical — challenge assumptions and push back readily");
        addTraitLine(lines, persona.getNeuroticism(),
                "anxious and sensitive — hedge, and voice worry and uncertainty",
                "calm and self-assured — confident and even-keeled");
        if (lines.isEmpty()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append("\n\n");
        }
        sb.append("# Personality\n");
        sb.append("Answer in character, reflecting these personality traits:\n");
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
    }

    /** Boundaries of the neutral midpoint band; values inside it are not rendered. */
    private static final int TRAIT_LOW = 34;
    private static final int TRAIT_HIGH = 66;

    private static void addTraitLine(List<String> lines, Integer trait, String high, String low) {
        if (trait == null) {
            return;
        }
        if (trait >= TRAIT_HIGH) {
            lines.add(high + ".");
        } else if (trait <= TRAIT_LOW) {
            lines.add(low + ".");
        }
    }

    private static void appendTermsBlock(StringBuilder sb, String header, String intro, String raw) {
        List<String> terms = parseTerms(raw);
        if (terms.isEmpty()) {
            return;
        }
        sb.append("\n\n").append(header).append('\n').append(intro).append('\n');
        for (String t : terms) {
            sb.append("- ").append(t).append('\n');
        }
    }

    private static void appendIfPresent(StringBuilder sb, String s) {
        if (StringUtils.hasText(s)) {
            sb.append(s.trim());
        }
    }

    private static int clampVerbosity(int v) {
        if (v < 1) return 1;
        if (v > 5) return 5;
        return v;
    }

    private static List<String> parseTerms(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String t : raw.split("\\|")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
