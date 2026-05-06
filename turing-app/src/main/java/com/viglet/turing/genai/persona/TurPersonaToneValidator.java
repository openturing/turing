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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.persona.TurPersona;

import lombok.extern.slf4j.Slf4j;

/**
 * Post-LLM check that enforces a {@link TurPersona}'s negative
 * constraints. Runs in {@code TurAgentChatExecutor} after the model
 * responds and before the executor emits its {@code ChatResponse}.
 *
 * <p>Behaviour for the MVP: detect forbidden terms (case-insensitive,
 * whole-word match), mask each hit with {@code [***]}, and return the
 * sanitised text. The full list of hits is also returned so the caller
 * can log them — regenerating the response is intentionally out of scope
 * (latency / cost) and can be layered on later.
 *
 * <p>Forbidden terms are pipe-separated in {@code TurPersona#getForbiddenTerms()}
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

        String sanitized = responseText;
        List<String> violations = new ArrayList<>();
        for (String term : terms) {
            // Whole-word, case-insensitive. \b doesn't match around non-ASCII
            // word boundaries on Java's default regex; for robustness fall
            // back to substring detection when the term itself isn't ASCII.
            Pattern pattern = isAscii(term)
                    ? Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE)
                    : Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(sanitized);
            if (matcher.find()) {
                violations.add(term);
                sanitized = matcher.replaceAll(MASK);
            }
        }

        if (violations.isEmpty()) {
            return TurPersonaValidationResult.passed(responseText);
        }

        log.warn("[Persona] '{}' forbidden-term hits: {}", persona.getName(), violations);
        return new TurPersonaValidationResult(false, sanitized, violations);
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

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
