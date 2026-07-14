/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.research.TurResearchSaturationScorer.Participant;
import com.viglet.turing.genai.research.dto.TurResearchGraphDto;
import com.viglet.turing.genai.research.dto.TurResearchPersonaLensDto;
import com.viglet.turing.genai.research.dto.TurResearchQuoteDto;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.genai.research.dto.TurResearchThemeDto;
import com.viglet.turing.genai.research.dto.TurResearchTurnDto;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.research.TurResearchInterview;
import com.viglet.turing.persistence.model.research.TurResearchInterviewStatus;
import com.viglet.turing.persistence.model.research.TurResearchStudy;
import com.viglet.turing.persistence.model.research.TurResearchStudyPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchInterviewRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyPersonaRepository;
import com.viglet.turing.persistence.repository.research.TurResearchStudyRepository;
import com.viglet.turing.system.TurLlmSummaryService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Synthesis &amp; sufficiency for Synthetic User Research studies (Block AW /
 * §XLVI.3). Two read surfaces over a study's completed interviews:
 *
 * <ul>
 *   <li><strong>T722 — insights report.</strong> Synthesizes the transcripts
 *       through {@link TurLlmSummaryService} (cached, regenerable, the same home as
 *       the SN-site and System-Info insights) into an executive summary, ranked
 *       themes each carrying <em>verbatim quotes traceable to (interview, persona)</em>,
 *       and recommendations. The by-theme lens is the themes themselves; the
 *       by-persona lens is derived deterministically from the quotes, so the two
 *       are always consistent. Attributions are validated against the study roster,
 *       so a fabricated participant is flagged rather than trusted.</li>
 *   <li><strong>T723 — saturation scoring.</strong> A pure, LLM-free novelty score
 *       via {@link TurResearchSaturationScorer} — free and reproducible in CI —
 *       that reports when marginal personas stop adding themes ("sample adequate at
 *       N").</li>
 * </ul>
 *
 * <p>Both are read-only and fail-open. Neither has a UI of its own; the
 * {@code /bento/persona/research} studio (T730, Phase 5) renders the report lenses,
 * the regenerate button, and the saturation gauge.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurResearchInsightsService {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();
    private static final TypeReference<List<TurResearchTurnDto>> TURNS_TYPE =
            new TypeReference<>() {};

    /** Cap on the transcript corpus handed to the synthesizer (chars). */
    private static final int MAX_CORPUS_CHARS = 24_000;
    private static final int MAX_ANSWER_CHARS = 1_500;

    private static final String SYSTEM_PROMPT = """
            You are a senior UX / market researcher. You are given a set of interview \
            transcripts with synthetic participants (each a distinct persona). \
            Synthesize what the cohort collectively reveals.

            Respond with ONLY a single JSON object (no markdown fences, no prose \
            outside the JSON) of exactly this shape:
            {
              "executiveSummary": "2-4 sentence summary of the collective findings",
              "themes": [
                {
                  "title": "short theme label",
                  "summary": "1-2 sentence description of the theme",
                  "quotes": [
                    {"personaId": "<id from the header>", "personaName": "<name>", \
            "quote": "a VERBATIM phrase copied exactly from that participant's answer"}
                  ]
                }
              ],
              "recommendations": ["concrete, actionable recommendation", "..."]
            }

            Rules:
            - Rank themes most-important first; produce 3 to 8 themes.
            - Every quote MUST be copied verbatim from a participant answer and \
            attributed to the correct participant using the personaId shown in that \
            participant's "### name (personaId: ...)" header. Never invent \
            participants, ids, or quotes.
            - Provide 3 to 6 recommendations.
            - Write all prose in the same language as the transcripts.""";

    private final TurResearchStudyRepository studyRepository;
    private final TurResearchStudyPersonaRepository personaJoinRepository;
    private final TurResearchInterviewRepository interviewRepository;
    private final TurPersonaRepository personaRepository;
    private final TurLlmSummaryService llmSummaryService;
    private final TurResearchSaturationScorer saturationScorer;
    private final TurResearchThemeGraphBuilder graphBuilder;
    private final TurResearchModelLaneResolver laneResolver;

    public TurResearchInsightsService(TurResearchStudyRepository studyRepository,
            TurResearchStudyPersonaRepository personaJoinRepository,
            TurResearchInterviewRepository interviewRepository,
            TurPersonaRepository personaRepository,
            TurLlmSummaryService llmSummaryService,
            TurResearchSaturationScorer saturationScorer,
            TurResearchThemeGraphBuilder graphBuilder,
            TurResearchModelLaneResolver laneResolver) {
        this.studyRepository = studyRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.interviewRepository = interviewRepository;
        this.personaRepository = personaRepository;
        this.llmSummaryService = llmSummaryService;
        this.saturationScorer = saturationScorer;
        this.graphBuilder = graphBuilder;
        this.laneResolver = laneResolver;
    }

    // ---- T722 insights report ---------------------------------------------

    /**
     * Synthesize (or return the cached) insights report for a study. Fail-open:
     * returns an {@code available=false} report when there is no default LLM or no
     * completed interviews, rather than throwing.
     */
    @Transactional(readOnly = true)
    public TurResearchReportDto report(String studyId, boolean regenerate) {
        TurResearchStudy study = studyRepository.findById(studyId).orElse(null);
        if (study == null) {
            return TurResearchReportDto.unavailable("Study not found.", false);
        }
        if (!llmSummaryService.isAvailable()) {
            return TurResearchReportDto.unavailable(
                    "No default LLM configured in Global Settings.", false);
        }

        List<Interview> completed = orderedCompletedInterviews(studyId);
        if (completed.isEmpty()) {
            return TurResearchReportDto.unavailable(
                    "No completed interviews to synthesize yet. Run the study first.", false);
        }

        // T728 — the synthesis stage rides its own model lane (fail-open to the
        // study-wide instance, then the default LLM). Fold the resolved instance
        // into the cache key so switching the synthesis model misses stale cache.
        String synthesisLlmId = laneResolver.resolveInstanceId(study, TurResearchStage.SYNTHESIS);
        String corpus = buildCorpus(completed);
        String cacheKey = "research-insights-" + studyId + "-" + stableHash(corpus)
                + "-" + Integer.toHexString(StringUtils.defaultString(synthesisLlmId).hashCode());

        TurLlmSummaryService.SummaryResult result =
                llmSummaryService.generate(cacheKey, corpus, SYSTEM_PROMPT, regenerate, synthesisLlmId);
        if (!result.success()) {
            return TurResearchReportDto.unavailable(result.error(), result.canRegenerate());
        }
        return parseReport(result.content(), result.canRegenerate(), completed);
    }

    // ---- T723 saturation ---------------------------------------------------

    /** Deterministic saturation report (no LLM) over the completed interviews. */
    @Transactional(readOnly = true)
    public TurResearchSaturationResultDto saturation(String studyId) {
        if (!studyRepository.existsById(studyId)) {
            return TurResearchSaturationResultDto.unavailable("Study not found.", 0);
        }
        List<Interview> completed = orderedCompletedInterviews(studyId);
        List<Participant> participants = completed.stream()
                .map(i -> new Participant(i.personaId(), i.personaName(), i.answersText()))
                .toList();
        return saturationScorer.score(participants);
    }

    // ---- T724 theme / affinity graph ---------------------------------------

    /**
     * The theme/affinity graph — a deterministic re-projection of the (cached)
     * T722 report into a persona↔theme graph. Visualization only: it adds no
     * inference, it just reuses the synthesized report (generating it once if not
     * already cached, then reusing the cache).
     */
    @Transactional(readOnly = true)
    public TurResearchGraphDto graph(String studyId) {
        TurResearchReportDto report = report(studyId, false);
        if (!report.available()) {
            return TurResearchGraphDto.unavailable(report.error());
        }
        return graphBuilder.build(report);
    }

    // ---- interview loading (roster order) ----------------------------------

    /**
     * The study's COMPLETED interviews with any captured turns, in roster
     * (position) order — the order saturation is measured in and the corpus is
     * presented in.
     */
    private List<Interview> orderedCompletedInterviews(String studyId) {
        Map<String, TurResearchInterview> byPersona = new LinkedHashMap<>();
        for (TurResearchInterview i : interviewRepository.findByStudy_IdOrderByPersonaIdAsc(studyId)) {
            byPersona.put(i.getPersonaId(), i);
        }
        List<Interview> ordered = new ArrayList<>();
        for (TurResearchStudyPersona join : personaJoinRepository
                .findByStudy_IdOrderByPositionAsc(studyId)) {
            TurResearchInterview interview = byPersona.get(join.getPersonaId());
            if (interview == null
                    || interview.getStatus() != TurResearchInterviewStatus.COMPLETED) {
                continue;
            }
            List<TurResearchTurnDto> turns = parseTurns(interview.getTranscriptJson());
            if (turns.isEmpty()) {
                continue;
            }
            String name = personaRepository.findById(join.getPersonaId())
                    .map(TurPersona::getName).orElse(join.getPersonaId());
            ordered.add(new Interview(join.getPersonaId(), name, turns));
        }
        return ordered;
    }

    private String buildCorpus(List<Interview> interviews) {
        StringBuilder sb = new StringBuilder();
        for (Interview i : interviews) {
            String header = "\n### " + i.personaName() + " (personaId: " + i.personaId() + ")\n";
            if (sb.length() + header.length() > MAX_CORPUS_CHARS) {
                break;
            }
            sb.append(header);
            for (TurResearchTurnDto turn : i.turns()) {
                String q = StringUtils.abbreviate(
                        StringUtils.defaultString(turn.question()), 300);
                String a = StringUtils.abbreviate(
                        StringUtils.defaultString(turn.answer()), MAX_ANSWER_CHARS);
                String block = "Interviewer: " + q + "\nParticipant: " + a + "\n";
                if (sb.length() + block.length() > MAX_CORPUS_CHARS) {
                    break;
                }
                sb.append(block);
            }
        }
        return sb.toString();
    }

    // ---- report parsing + post-processing ----------------------------------

    private TurResearchReportDto parseReport(String content, boolean canRegenerate,
            List<Interview> interviews) {
        LlmReport parsed = parseLlmJson(content);
        if (parsed == null) {
            // The model returned prose we could not trust as structured output;
            // surface the raw text so the studio can still render it (fail-open).
            log.debug("[Research] insights JSON parse failed for a study; returning raw content");
            return new TurResearchReportDto(true, null, canRegenerate, null,
                    List.of(), List.of(), List.of(), content);
        }

        Map<String, String> rosterNames = rosterNames(interviews);
        Map<String, String> nameToId = new LinkedHashMap<>();
        rosterNames.forEach((id, name) -> nameToId.putIfAbsent(lower(name), id));

        List<TurResearchThemeDto> themes = new ArrayList<>();
        for (LlmTheme theme : nullToEmpty(parsed.themes())) {
            if (theme == null || StringUtils.isBlank(theme.title())) {
                continue;
            }
            List<TurResearchQuoteDto> quotes = new ArrayList<>();
            Set<String> personasInTheme = new LinkedHashSet<>();
            for (LlmQuote q : nullToEmpty(theme.quotes())) {
                if (q == null || StringUtils.isBlank(q.quote())) {
                    continue;
                }
                TurResearchQuoteDto quote = resolveQuote(q, rosterNames, nameToId);
                quotes.add(quote);
                if (quote.resolved()) {
                    personasInTheme.add(quote.personaId());
                }
            }
            themes.add(new TurResearchThemeDto(theme.title().trim(),
                    StringUtils.trimToNull(theme.summary()),
                    personasInTheme.isEmpty() ? quotes.size() : personasInTheme.size(),
                    quotes));
        }

        List<String> recommendations = nullToEmpty(parsed.recommendations()).stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .toList();

        return new TurResearchReportDto(true, null, canRegenerate,
                StringUtils.trimToNull(parsed.executiveSummary()), themes, recommendations,
                byPersona(themes, rosterNames), content);
    }

    private TurResearchQuoteDto resolveQuote(LlmQuote q, Map<String, String> rosterNames,
            Map<String, String> nameToId) {
        String quote = q.quote().trim();
        String id = StringUtils.trimToNull(q.personaId());
        if (id != null && rosterNames.containsKey(id)) {
            return new TurResearchQuoteDto(id, rosterNames.get(id), quote, true);
        }
        // Attribution by id failed — try to rescue it by name against the roster.
        String byName = nameToId.get(lower(q.personaName()));
        if (byName != null) {
            return new TurResearchQuoteDto(byName, rosterNames.get(byName), quote, true);
        }
        return new TurResearchQuoteDto(id, StringUtils.trimToNull(q.personaName()), quote, false);
    }

    /** Re-project the theme quotes as the by-persona lens, in roster order. */
    private List<TurResearchPersonaLensDto> byPersona(List<TurResearchThemeDto> themes,
            Map<String, String> rosterNames) {
        Map<String, List<TurResearchPersonaLensDto.Quote>> byId = new LinkedHashMap<>();
        // Seed in roster order so the lens lists personas as the study ordered them.
        rosterNames.keySet().forEach(id -> byId.put(id, new ArrayList<>()));
        for (TurResearchThemeDto theme : themes) {
            for (TurResearchQuoteDto quote : theme.quotes()) {
                if (!quote.resolved() || quote.personaId() == null) {
                    continue;
                }
                byId.computeIfAbsent(quote.personaId(), k -> new ArrayList<>())
                        .add(new TurResearchPersonaLensDto.Quote(theme.title(), quote.quote()));
            }
        }
        List<TurResearchPersonaLensDto> lens = new ArrayList<>();
        byId.forEach((id, quotes) -> {
            if (!quotes.isEmpty()) {
                lens.add(new TurResearchPersonaLensDto(id,
                        rosterNames.getOrDefault(id, id), quotes));
            }
        });
        return lens;
    }

    private Map<String, String> rosterNames(List<Interview> interviews) {
        Map<String, String> names = new LinkedHashMap<>();
        for (Interview i : interviews) {
            names.put(i.personaId(), i.personaName());
        }
        return names;
    }

    // ---- json helpers ------------------------------------------------------

    /**
     * Parse the model's structured output defensively: strip any markdown fences
     * and slice to the outermost JSON object before deserializing. Returns
     * {@code null} on any failure so the caller can fall back to raw content
     * (the gpt-4o-mini "prose around JSON" guard from the repo playbook).
     */
    private LlmReport parseLlmJson(String content) {
        String json = extractJsonObject(content);
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LlmReport.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String extractJsonObject(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String trimmed = content.strip();
        // Drop a leading ```json / ``` fence if present.
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return trimmed.substring(start, end + 1);
    }

    private List<TurResearchTurnDto> parseTurns(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<TurResearchTurnDto> parsed = MAPPER.readValue(json, TURNS_TYPE);
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private String stableHash(String material) {
        return Integer.toHexString(material.length()) + "-"
                + Integer.toHexString(material.hashCode());
    }

    private String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    // ---- LLM output shape --------------------------------------------------

    private record LlmReport(String executiveSummary, List<LlmTheme> themes,
            List<String> recommendations) {
    }

    private record LlmTheme(String title, String summary, List<LlmQuote> quotes) {
    }

    private record LlmQuote(String personaId, String personaName, String quote) {
    }

    // ---- internal interview view -------------------------------------------

    private record Interview(String personaId, String personaName,
            List<TurResearchTurnDto> turns) {

        /** The participant's answers concatenated — the saturation mining input. */
        String answersText() {
            StringBuilder sb = new StringBuilder();
            for (TurResearchTurnDto turn : turns) {
                if (StringUtils.isNotBlank(turn.answer())) {
                    sb.append(turn.answer()).append(' ');
                }
            }
            return sb.toString();
        }
    }
}
