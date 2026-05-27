/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.dto.agent.TurSystemPromptIssueDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptPreviewDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates an AI Agent's assembled system prompt for conflicts that can
 * confuse or break the LLM. Two engines, sharing the
 * {@link TurSystemPromptIssueDto} shape with the chat-flow "authoring
 * warnings" linter:
 *
 * <ul>
 *   <li>{@link #validate(TurAIAgent)} — fast, deterministic heuristics
 *       (empty/overlong prompt, contradictory response-language directives,
 *       persona vocabulary that is both required and forbidden, a forbidden
 *       term used in the prompt itself, redundant agent/persona instruction).
 *       Safe to run on every page load.</li>
 *   <li>{@link #deepCheck(TurAIAgent)} — sends the assembled prompt to the
 *       default LLM as a conflict auditor and parses its findings. Costs a
 *       call, so it is button-triggered.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSystemPromptValidatorService {

    /** Above this assembled-prompt length we warn about token bloat. */
    static final int LONG_PROMPT_THRESHOLD = 8000;

    private static final String SEV_ERROR = "ERROR";
    private static final String SEV_WARNING = "WARNING";
    private static final String SEV_INFO = "INFO";

    // Response-language directive detector. A canonical language code is
    // emitted only when a directive verb sits within ~25 chars of a language
    // word, which keeps incidental mentions ("translate the Portuguese term")
    // from registering as a directive.
    private static final Pattern LANGUAGE_DIRECTIVE = Pattern.compile(
            "(?i)(respond|reply|answer|write|always respond|responda|responder|escreva|sempre responda)"
                    + "[^\\n]{0,25}?\\b(english|ingl[eê]s|portuguese|portugu[eê]s|spanish|espa[nñ]ol|espanhol"
                    + "|french|fran[cç][eê]s|german|alem[aã]o|italian|italiano)\\b");

    private static final Map<String, String> LANGUAGE_CODES = buildLanguageCodes();

    private final TurSystemPromptPreviewService previewService;
    private final TurLlmSummaryService llmSummaryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurSystemPromptValidatorService(TurSystemPromptPreviewService previewService,
            TurLlmSummaryService llmSummaryService) {
        this.previewService = previewService;
        this.llmSummaryService = llmSummaryService;
    }

    /** Deterministic heuristic checks — fast, no LLM call. */
    @Transactional(readOnly = true)
    public List<TurSystemPromptIssueDto> validate(TurAIAgent agent) {
        List<TurSystemPromptIssueDto> issues = new ArrayList<>();
        String base = agent.getSystemPrompt();
        TurPersona persona = agent.getDefaultPersona();
        String personaInstruction = persona == null ? null : persona.getSystemInstruction();
        TurSystemPromptPreviewDto preview = previewService.buildPreview(agent);

        checkEmptyPrompt(base, issues);
        checkPromptLength(preview.assembledText(), issues);
        checkLanguageDirectiveConflict(base, personaInstruction, issues);
        checkPersonaVocabulary(persona, base, issues);
        checkRedundantInstruction(base, personaInstruction, issues);

        return issues;
    }

    private void checkEmptyPrompt(String base, List<TurSystemPromptIssueDto> issues) {
        if (!StringUtils.hasText(base)) {
            issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "empty_system_prompt",
                    "This agent has no system prompt; it falls back to a generic default.",
                    "Write a system prompt that defines the agent's scope, tone, and boundaries.",
                    "AGENT"));
        }
    }

    private void checkPromptLength(String assembled, List<TurSystemPromptIssueDto> issues) {
        if (assembled != null && assembled.length() > LONG_PROMPT_THRESHOLD) {
            issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "system_prompt_too_long",
                    "The assembled system prompt is " + assembled.length()
                            + " characters — large prompts inflate token cost and dilute the model's attention.",
                    "Trim redundant guidance, or move stable reference material into a tool or RAG store.",
                    "AGENT"));
        }
    }

    private void checkLanguageDirectiveConflict(String base, String personaInstruction,
            List<TurSystemPromptIssueDto> issues) {
        Set<String> baseLangs = detectLanguageDirectives(base);
        Set<String> personaLangs = detectLanguageDirectives(personaInstruction);
        Set<String> all = new LinkedHashSet<>(baseLangs);
        all.addAll(personaLangs);
        if (all.size() > 1) {
            String source = !baseLangs.isEmpty() && !personaLangs.isEmpty()
                    ? "AGENT + PERSONA"
                    : (baseLangs.size() > 1 ? "AGENT" : "PERSONA");
            issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "language_directive_conflict",
                    "Conflicting response-language directives: " + String.join(", ", all)
                            + ". The model gets contradictory instructions about which language to answer in.",
                    "Keep a single response-language rule across the agent prompt and the persona, "
                            + "or scope each one to an explicit condition.",
                    source));
        }
    }

    private void checkPersonaVocabulary(TurPersona persona, String base,
            List<TurSystemPromptIssueDto> issues) {
        if (persona == null) {
            return;
        }
        Set<String> mandatory = parseTerms(persona.getMandatoryTerms());
        Set<String> forbidden = parseTerms(persona.getForbiddenTerms());

        // A term required AND forbidden by the same persona is a hard contradiction.
        for (String term : mandatory) {
            if (containsIgnoreCase(forbidden, term)) {
                issues.add(new TurSystemPromptIssueDto(SEV_ERROR, "persona_vocab_contradiction",
                        "The persona lists \"" + term + "\" as both required and forbidden vocabulary.",
                        "Remove the term from one of the two lists.",
                        "PERSONA"));
            }
        }
        // A forbidden term used verbatim in the agent prompt undercuts the ban.
        if (StringUtils.hasText(base)) {
            for (String term : forbidden) {
                if (containsWord(base, term)) {
                    issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "forbidden_term_in_prompt",
                            "The agent prompt uses \"" + term
                                    + "\", which the default persona forbids — the persona will be told to rephrase its own instructions.",
                            "Rephrase the agent prompt to avoid the forbidden term, or drop it from the persona's forbidden list.",
                            "AGENT"));
                }
            }
        }
    }

    private void checkRedundantInstruction(String base, String personaInstruction,
            List<TurSystemPromptIssueDto> issues) {
        if (!StringUtils.hasText(base) || !StringUtils.hasText(personaInstruction)) {
            return;
        }
        String a = base.strip();
        String b = personaInstruction.strip();
        boolean redundant = a.equalsIgnoreCase(b)
                || (a.length() > 40 && b.length() > 40
                        && (a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT))
                                || b.toLowerCase(Locale.ROOT).contains(a.toLowerCase(Locale.ROOT))));
        if (redundant) {
            issues.add(new TurSystemPromptIssueDto(SEV_INFO, "redundant_persona_instruction",
                    "The default persona's instruction largely duplicates the agent system prompt.",
                    "Keep each layer focused — the agent prompt for scope/behavior, the persona for voice — to avoid sending the same text twice.",
                    "PERSONA"));
        }
    }

    /**
     * Deep semantic audit via the default LLM. Returns the parsed findings, a
     * single availability warning when no LLM is configured, or a single INFO
     * issue carrying the raw analysis when the model's reply isn't parseable.
     */
    @Transactional(readOnly = true)
    public List<TurSystemPromptIssueDto> deepCheck(TurAIAgent agent) {
        if (!llmSummaryService.isAvailable()) {
            return List.of(new TurSystemPromptIssueDto(SEV_WARNING, "deep_check_unavailable",
                    "No default LLM is configured or enabled, so the deep conflict analysis can't run.",
                    "Set a default LLM in Global Settings to enable the deep check.",
                    "LLM"));
        }
        String assembled = previewService.buildPreview(agent).assembledText();
        SummaryResult result = llmSummaryService.generate(
                "turSystemPromptDeepCheck:" + agent.getId(), assembled, judgeSystemPrompt(), true);
        if (!result.success()) {
            return List.of(new TurSystemPromptIssueDto(SEV_WARNING, "deep_check_failed",
                    result.error() == null ? "The deep conflict analysis failed." : result.error(),
                    "Try again; if it persists, check the default LLM's status.",
                    "LLM"));
        }
        return parseJudgeResponse(result.content());
    }

    private static String judgeSystemPrompt() {
        return String.join("\n",
                "You are a prompt-conflict auditor for an AI agent's SYSTEM PROMPT.",
                "The user message contains the fully assembled system prompt that will be sent to an LLM.",
                "Find instructions that CONTRADICT each other or are likely to confuse the model or cause an error:",
                "e.g. conflicting response languages, mutually exclusive tone/format rules, "
                        + "an instruction that both requires and forbids the same behavior, "
                        + "impossible constraints, or directives that fight the agent's stated scope.",
                "Do NOT report stylistic preferences or things that are merely verbose — only real conflicts.",
                "Respond with ONLY a JSON array (no prose, no markdown fences). Each element:",
                "{\"severity\":\"ERROR|WARNING|INFO\",\"code\":\"snake_case_id\",\"message\":\"one line\",\"hint\":\"one-line fix\"}",
                "Use ERROR for hard contradictions, WARNING for risky overlaps, INFO for minor notes.",
                "If there are no conflicts, respond with exactly: []",
                "Write message and hint in the same language as the system prompt you are auditing.");
    }

    private List<TurSystemPromptIssueDto> parseJudgeResponse(String raw) {
        String json = extractJsonArray(raw);
        if (json == null) {
            // Model didn't return parseable JSON — surface its analysis as-is
            // rather than dropping it, so the operator still benefits.
            return List.of(new TurSystemPromptIssueDto(SEV_INFO, "deep_check_note",
                    StringUtils.hasText(raw) ? raw.strip() : "The model returned an empty analysis.",
                    "This is the raw model analysis (it wasn't structured).",
                    "LLM"));
        }
        try {
            JsonNode array = objectMapper.readTree(json);
            if (!array.isArray()) {
                return List.of();
            }
            List<TurSystemPromptIssueDto> issues = new ArrayList<>();
            for (JsonNode node : array) {
                String severity = normalizeSeverity(text(node, "severity"));
                String message = text(node, "message");
                if (!StringUtils.hasText(message)) {
                    continue;
                }
                String code = StringUtils.hasText(text(node, "code")) ? text(node, "code") : "llm_finding";
                issues.add(new TurSystemPromptIssueDto(severity, code, message,
                        text(node, "hint"), "LLM"));
            }
            return issues;
        } catch (JacksonException e) {
            log.debug("[SystemPrompt] deep-check JSON parse failed: {}", e.getMessage());
            return List.of(new TurSystemPromptIssueDto(SEV_INFO, "deep_check_note",
                    raw.strip(), "This is the raw model analysis (it wasn't structured).", "LLM"));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String normalizeSeverity(String raw) {
        if (raw == null) {
            return SEV_WARNING;
        }
        String up = raw.trim().toUpperCase(Locale.ROOT);
        return switch (up) {
            case SEV_ERROR, SEV_WARNING, SEV_INFO -> up;
            default -> SEV_WARNING;
        };
    }

    /** Extracts the first JSON array substring, tolerating code fences/prose. */
    static String extractJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    Set<String> detectLanguageDirectives(String text) {
        Set<String> langs = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return langs;
        }
        Matcher m = LANGUAGE_DIRECTIVE.matcher(text);
        while (m.find()) {
            String word = m.group(2).toLowerCase(Locale.ROOT);
            String code = LANGUAGE_CODES.get(word);
            if (code != null) {
                langs.add(code);
            }
        }
        return langs;
    }

    private static Set<String> parseTerms(String raw) {
        Set<String> out = new LinkedHashSet<>();
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

    private static boolean containsIgnoreCase(Set<String> set, String value) {
        for (String s : set) {
            if (s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWord(String haystack, String term) {
        if (!StringUtils.hasText(term)) {
            return false;
        }
        Pattern p = Pattern.compile("\\b" + Pattern.quote(term) + "\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return p.matcher(haystack).find();
    }

    private static Map<String, String> buildLanguageCodes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("english", "English");
        m.put("inglês", "English");
        m.put("ingles", "English");
        m.put("portuguese", "Portuguese");
        m.put("português", "Portuguese");
        m.put("portugues", "Portuguese");
        m.put("spanish", "Spanish");
        m.put("español", "Spanish");
        m.put("espanhol", "Spanish");
        m.put("french", "French");
        m.put("français", "French");
        m.put("francês", "French");
        m.put("frances", "French");
        m.put("german", "German");
        m.put("alemão", "German");
        m.put("alemao", "German");
        m.put("italian", "Italian");
        m.put("italiano", "Italian");
        return m;
    }
}
