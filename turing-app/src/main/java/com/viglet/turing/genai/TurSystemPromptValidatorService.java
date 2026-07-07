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

import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptIssueDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptPreviewDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptSegmentDto;
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

    // --- S1192: extracted duplicated literals ---
    private static final String AGENT = "AGENT";
    private static final String PERSONA = "PERSONA";
    private static final String ENGLISH = "English";
    private static final String PORTUGUESE = "Portuguese";
    private static final String SPANISH = "Spanish";
    private static final String FRENCH = "French";
    private static final String GERMAN = "German";


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
            "(?iu)(respond|reply|answer|write|always respond|responda|responder|escreva|sempre responda)"
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
        checkFlowPersonaOverrides(agent, issues);
        checkCrossSegment(preview, issues);

        return issues;
    }

    // ─────────────────── T610 — cross-segment lint ───────────────────

    /** Segment origins that carry authored rule text (not informational). */
    private static final Set<String> RULE_ORIGINS = Set.of("PERSONA", AGENT, "MCP", "FLOW");
    /** Minimum normalized-line length to consider a line a "rule" worth matching. */
    private static final int MIN_RULE_LEN = 15;
    /** Minimum directive-core length before a polarity contradiction is reported. */
    private static final int MIN_CORE_LEN = 12;

    /** Polarity-negation markers (EN/PT) — their presence flips a directive. */
    private static final Set<String> NEGATIONS = Set.of("never", "not", "dont", "cannot", "cant",
            "no", "avoid", "nunca", "jamais", "nao", "evite", "evitar");
    /** Filler/intensifier words stripped so opposite-polarity cores align. */
    private static final Set<String> FILLERS = Set.of("do", "does", "please", "always", "must",
            "should", "sempre", "deve", "favor", "por");

    /**
     * T610 — cross-segment redundancy + contradiction lint. The other checks look
     * <em>within</em> one fragment; this one compares the assembled segments
     * (persona / agent / MCP / active-flow addendum) against each other to catch a
     * rule paid for 2–3× (duplicated forbidden vocab, "never ask CPF" in both the
     * persona and the agent) and a directive stated one way in one segment and
     * negated in another. Operates on the segments the preview already assembled —
     * no re-parsing of the flattened string.
     */
    private void checkCrossSegment(TurSystemPromptPreviewDto preview,
            List<TurSystemPromptIssueDto> issues) {
        List<RuleLine> lines = new ArrayList<>();
        for (TurSystemPromptSegmentDto seg : preview.segments()) {
            if (!seg.included() || seg.runtimeOnly() || !StringUtils.hasText(seg.content())
                    || !RULE_ORIGINS.contains(seg.origin())) {
                continue;
            }
            for (String raw : seg.content().split("\\r?\\n")) {
                String normalized = normalizeRule(raw);
                if (normalized.length() < MIN_RULE_LEN) {
                    continue;
                }
                boolean[] negated = { false };
                String core = directiveCore(normalized, negated);
                lines.add(new RuleLine(seg.origin(), raw.strip(), normalized, core, negated[0]));
            }
        }
        detectCrossSegmentRedundancy(lines, issues);
        detectCrossSegmentContradiction(lines, issues);
    }

    /** One authored rule line from a segment, normalized for cross-comparison. */
    private record RuleLine(String origin, String raw, String normalized, String core,
            boolean negated) {
    }

    /** A normalized rule line repeated across ≥2 segments is duplicated token cost. */
    private void detectCrossSegmentRedundancy(List<RuleLine> lines,
            List<TurSystemPromptIssueDto> issues) {
        Map<String, List<RuleLine>> byNormalized = new LinkedHashMap<>();
        for (RuleLine line : lines) {
            byNormalized.computeIfAbsent(line.normalized(), k -> new ArrayList<>()).add(line);
        }
        for (Map.Entry<String, List<RuleLine>> entry : byNormalized.entrySet()) {
            List<RuleLine> group = entry.getValue();
            Set<String> origins = new LinkedHashSet<>();
            group.forEach(l -> origins.add(l.origin()));
            if (origins.size() < 2) {
                continue;
            }
            int wastedTokens = TurPromptSegment.estimateTokens(group.get(0).raw()) * (group.size() - 1);
            String where = String.join(" + ", origins);
            String snippet = truncate(group.get(0).raw(), 80);
            issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "cross_segment_redundancy",
                    "The rule \"" + snippet + "\" is repeated across " + where
                            + " — the model is paying for the same instruction "
                            + group.size() + "× (~" + wastedTokens + " wasted tokens).",
                    "Keep the rule in one layer and delete the copies to save tokens and avoid drift.",
                    where, Map.of("rule", snippet, "segments", where,
                            "tokens", String.valueOf(wastedTokens))));
        }
    }

    /** Same directive core stated affirmatively in one segment and negated in another. */
    private void detectCrossSegmentContradiction(List<RuleLine> lines,
            List<TurSystemPromptIssueDto> issues) {
        Map<String, List<RuleLine>> byCore = new LinkedHashMap<>();
        for (RuleLine line : lines) {
            if (line.core().length() >= MIN_CORE_LEN) {
                byCore.computeIfAbsent(line.core(), k -> new ArrayList<>()).add(line);
            }
        }
        Set<String> reported = new LinkedHashSet<>();
        for (List<RuleLine> group : byCore.values()) {
            RuleLine affirmative = group.stream().filter(l -> !l.negated()).findFirst().orElse(null);
            RuleLine negated = group.stream().filter(l -> l.negated()).findFirst().orElse(null);
            if (affirmative == null || negated == null
                    || affirmative.origin().equals(negated.origin())) {
                continue;
            }
            String where = affirmative.origin() + " ↔ " + negated.origin();
            if (!reported.add(where + "::" + affirmative.core())) {
                continue;
            }
            issues.add(new TurSystemPromptIssueDto(SEV_ERROR, "cross_segment_contradiction",
                    "Contradiction between " + where + ": \"" + truncate(affirmative.raw(), 60)
                            + "\" vs \"" + truncate(negated.raw(), 60)
                            + "\". The model gets opposite instructions on the same point.",
                    "Reconcile the two segments so a single, consistent rule governs this behavior.",
                    where, Map.of("segments", where)));
        }
    }

    /** Lower-cases, strips markdown list/heading/emphasis markup, collapses spaces. */
    static String normalizeRule(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip()
                .replaceAll("^[#>\\-*+\\d.\\)\\s]+", "")   // leading heading/list markers
                .replace("*", "").replace("_", "").replace("`", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[.:;!?\"']+$", "")
                .strip();
        return s;
    }

    /**
     * Reduces a normalized rule to its directive "core" by dropping polarity
     * markers + fillers, and reports (out-param) whether a negation was present —
     * so "always ask for cpf" and "never ask for cpf" share the core "ask for cpf"
     * with opposite polarity.
     */
    static String directiveCore(String normalized, boolean[] negatedOut) {
        StringBuilder core = new StringBuilder();
        for (String word : normalized.split(" ")) {
            String w = word.replaceAll("[^\\p{L}]", "");
            if (w.isEmpty()) {
                continue;
            }
            if (NEGATIONS.contains(w)) {
                negatedOut[0] = true;
                continue;
            }
            if (FILLERS.contains(w)) {
                continue;
            }
            core.append(core.isEmpty() ? "" : " ").append(w);
        }
        return core.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /**
     * T607 — a chat flow's {@code persona} node can switch the active voice, but
     * only if the referenced persona is in the agent's catalog and usable as a
     * speaker; otherwise the runtime silently falls back to the default (the same
     * blind spot the preview used to have). Surface those misconfigurations so the
     * operator doesn't ship a flow that quietly speaks in the wrong voice.
     */
    private void checkFlowPersonaOverrides(TurAIAgent agent,
            List<TurSystemPromptIssueDto> issues) {
        for (TurSystemPromptPreviewService.FlowPersonaDiagnostic d
                : previewService.diagnoseFlowPersonas(agent)) {
            if (d.notInCatalog()) {
                issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "flow_persona_not_in_catalog",
                        "Flow \"" + d.flowName() + "\" has a persona node pointing at persona \""
                                + d.personaRef() + "\", which is not in this agent's persona catalog. "
                                + "The runtime silently falls back to the default persona.",
                        "Add that persona to the agent, or fix the node to reference an allowed persona.",
                        "FLOW", Map.of("flow", d.flowName(), "persona", d.personaRef())));
            } else if (d.audienceOnly()) {
                issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "flow_persona_audience_only",
                        "Flow \"" + d.flowName() + "\" switches to persona \"" + d.personaRef()
                                + "\", which is audience-only and cannot be a speaker. "
                                + "The runtime falls back to the default persona.",
                        "Use a persona whose kind is SPEAKER or BOTH for a flow voice switch.",
                        "FLOW", Map.of("flow", d.flowName(), "persona", d.personaRef())));
            }
        }
    }

    private void checkEmptyPrompt(String base, List<TurSystemPromptIssueDto> issues) {
        if (!StringUtils.hasText(base)) {
            issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "empty_system_prompt",
                    "This agent has no system prompt; it falls back to a generic default.",
                    "Write a system prompt that defines the agent's scope, tone, and boundaries.",
                    AGENT));
        }
    }

    private void checkPromptLength(String assembled, List<TurSystemPromptIssueDto> issues) {
        if (assembled != null && assembled.length() > LONG_PROMPT_THRESHOLD) {
            issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "system_prompt_too_long",
                    "The assembled system prompt is " + assembled.length()
                            + " characters — large prompts inflate token cost and dilute the model's attention.",
                    "Trim redundant guidance, or move stable reference material into a tool or RAG store.",
                    AGENT, Map.of("length", String.valueOf(assembled.length()))));
        }
    }

    private void checkLanguageDirectiveConflict(String base, String personaInstruction,
            List<TurSystemPromptIssueDto> issues) {
        Set<String> baseLangs = detectLanguageDirectives(base);
        Set<String> personaLangs = detectLanguageDirectives(personaInstruction);
        Set<String> all = new LinkedHashSet<>(baseLangs);
        all.addAll(personaLangs);
        if (all.size() > 1) {
            String singleSource = baseLangs.size() > 1 ? AGENT : PERSONA;
            String source = !baseLangs.isEmpty() && !personaLangs.isEmpty()
                    ? "AGENT + PERSONA"
                    : singleSource;
            issues.add(new TurSystemPromptIssueDto(SEV_WARNING, "language_directive_conflict",
                    "Conflicting response-language directives: " + String.join(", ", all)
                            + ". The model gets contradictory instructions about which language to answer in.",
                    "Keep a single response-language rule across the agent prompt and the persona, "
                            + "or scope each one to an explicit condition.",
                    source, Map.of("langs", String.join(", ", all))));
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
                        PERSONA, Map.of("term", term)));
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
                            AGENT, Map.of("term", term)));
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
                    PERSONA));
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
        m.put("english", ENGLISH);
        m.put("inglês", ENGLISH);
        m.put("ingles", ENGLISH);
        m.put("portuguese", PORTUGUESE);
        m.put("português", PORTUGUESE);
        m.put("portugues", PORTUGUESE);
        m.put("spanish", SPANISH);
        m.put("español", SPANISH);
        m.put("espanhol", SPANISH);
        m.put("french", FRENCH);
        m.put("français", FRENCH);
        m.put("francês", FRENCH);
        m.put("frances", FRENCH);
        m.put("german", GERMAN);
        m.put("alemão", GERMAN);
        m.put("alemao", GERMAN);
        m.put("italian", "Italian");
        m.put("italiano", "Italian");
        return m;
    }
}
