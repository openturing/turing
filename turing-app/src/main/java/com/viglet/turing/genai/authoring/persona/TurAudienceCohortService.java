/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring.persona;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.dto.persona.TurPersonaDto;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaDomainExpertise;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Audience cohort synthesis (Block AW / §XLVI.5, T731): turn a one-paragraph
 * audience brief into a <em>diverse</em> set of {@code TurPersona} drafts —
 * demographics + psychographics → N personas with <strong>spread OCEAN facets</strong>
 * (T717) so a synthetic-research study samples a real range of temperaments, not
 * N clones. Reuses the AI-authoring strict-schema discipline (the persona-from-audio
 * {@link TurPersonaAudioDeriveService} pattern) over {@link TurLlmSummaryService}:
 * one call returns a JSON array, defensively parsed into never-saved drafts handed
 * to the existing persona review form ("derive, never auto-apply").
 *
 * <p>Fail-open like its siblings — {@code success=false} with an {@code error} when
 * there is no default LLM or the output can't be parsed; nothing is persisted.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAudienceCohortService {

    /** Bounds on the requested cohort size so a bad request can't ask for 500 drafts. */
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 12;
    private static final int DEFAULT_COUNT = 5;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final String SYSTEM_PROMPT = """
            You design a DIVERSE cohort of audience personas for synthetic user
            research, from a short audience brief. Return EXACTLY ONE JSON ARRAY —
            no prose, no code fences — of persona objects. Schema per element:
            {"name": "<descriptive kebab-case id, unique in the cohort>",
             "description": "<one sentence: who this person is>",
             "systemInstruction": "<a short second-person voice brief ('You are…') the LLM role-plays; ground it in the brief, invent no real names/employers>",
             "tone": "FORMAL|CASUAL|TECHNICAL|EXECUTIVE",
             "verbosity": <1-5>,
             "languageStyle": "NEUTRAL|DIRECT|NARRATIVE|PERSUASIVE|INSTRUCTIONAL",
             "readingLevel": "ELEMENTARY|MIDDLE|SECONDARY|UNDERGRADUATE|GRADUATE",
             "domainExpertise": "NOVICE|BEGINNER|INTERMEDIATE|ADVANCED|EXPERT",
             "vocabularyCeiling": "<short note on vocabulary>",
             "primaryLanguage": "<ISO-639 code, e.g. pt|en|es>",
             "openness": <0-100>, "conscientiousness": <0-100>,
             "extraversion": <0-100>, "agreeableness": <0-100>,
             "neuroticism": <0-100>}

            Make the cohort genuinely diverse: SPREAD the five OCEAN facets across
            the personas (do not cluster everyone near 50), and vary demographics,
            goals and expertise so marginal participants can still surface new
            themes. Write "name"/"description"/"systemInstruction" in the brief's
            language. Every field is REQUIRED and non-empty; never use placeholders.
            Return exactly the requested number of personas.
            """;

    private final TurLlmSummaryService llmSummaryService;

    public TurAudienceCohortService(TurLlmSummaryService llmSummaryService) {
        this.llmSummaryService = llmSummaryService;
    }

    /** Wire response: not-yet-saved drafts synthesized from the brief. */
    public record CohortResult(boolean success, String error, List<TurPersonaDto> personas) {

        public static CohortResult failed(String error) {
            return new CohortResult(false, error, List.of());
        }
    }

    /**
     * Synthesize {@code count} diverse persona drafts from the audience brief.
     * Never persists anything; a caller reviews the drafts and saves the ones it
     * keeps through the normal persona form.
     */
    public CohortResult synthesize(String brief, int count, boolean regenerate) {
        if (StringUtils.isBlank(brief)) {
            return CohortResult.failed("An audience brief is required.");
        }
        int n = Math.clamp(count <= 0 ? DEFAULT_COUNT : count, MIN_COUNT, MAX_COUNT);
        String data = "Number of personas: " + n + "\n\nAudience brief:\n" + brief.trim();
        String hash = Integer.toHexString((brief.trim() + "#" + n).hashCode());

        SummaryResult result = llmSummaryService.generate(
                "audience-cohort:" + hash, data, SYSTEM_PROMPT, regenerate);
        if (!result.success() || StringUtils.isBlank(result.content())) {
            return CohortResult.failed(Objects.toString(result.error(),
                    "Could not synthesize the cohort (no default LLM?)."));
        }
        List<CohortEntry> entries = parse(result.content());
        if (entries.isEmpty()) {
            return CohortResult.failed("Could not parse the synthesized cohort.");
        }
        List<TurPersonaDto> drafts = new ArrayList<>();
        for (CohortEntry entry : entries) {
            drafts.add(toDraft(entry));
        }
        return new CohortResult(true, null, drafts);
    }

    private TurPersonaDto toDraft(CohortEntry entry) {
        TurPersonaDto draft = new TurPersonaDto();
        // id stays null — the UuidGenerator assigns one only on a real save.
        draft.setName(StringUtils.trimToNull(entry.name()));
        draft.setDescription(entry.description());
        draft.setSystemInstruction(entry.systemInstruction());
        draft.setTone(parseEnum(TurPersonaTone.class, entry.tone()));
        draft.setVerbosity(entry.verbosity() == null ? 3 : Math.clamp(entry.verbosity(), 1, 5));
        draft.setLanguageStyle(parseEnum(TurPersonaLanguageStyle.class, entry.languageStyle()));
        draft.setEnabled(1);
        draft.setPersonaKind(TurPersonaKind.BOTH);
        draft.setOpenness(clampFacet(entry.openness()));
        draft.setConscientiousness(clampFacet(entry.conscientiousness()));
        draft.setExtraversion(clampFacet(entry.extraversion()));
        draft.setAgreeableness(clampFacet(entry.agreeableness()));
        draft.setNeuroticism(clampFacet(entry.neuroticism()));

        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(parseEnum(TurPersonaReadingLevel.class, entry.readingLevel()));
        audience.setDomainExpertise(parseEnum(TurPersonaDomainExpertise.class, entry.domainExpertise()));
        audience.setVocabularyCeiling(entry.vocabularyCeiling());
        audience.setPrimaryLanguage(entry.primaryLanguage());
        draft.setAudience(audience);
        return draft;
    }

    private static Integer clampFacet(Integer value) {
        return value == null ? null : Math.clamp(value, 0, 100);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Defensively slice the outermost JSON array and parse it (weak-model tolerant). */
    private List<CohortEntry> parse(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            List<CohortEntry> parsed = MAPPER.readValue(raw.substring(start, end + 1),
                    new TypeReference<List<CohortEntry>>() {
                    });
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException e) {
            log.warn("[AudienceCohort] could not parse cohort: {}", e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CohortEntry(String name, String description, String systemInstruction,
            String tone, Integer verbosity, String languageStyle,
            String readingLevel, String domainExpertise, String vocabularyCeiling,
            String primaryLanguage, Integer openness, Integer conscientiousness,
            Integer extraversion, Integer agreeableness, Integer neuroticism) {
    }
}
