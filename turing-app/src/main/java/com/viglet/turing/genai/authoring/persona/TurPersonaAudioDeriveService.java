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

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.genai.transcription.TurTranscriptionProvider;
import com.viglet.turing.genai.transcription.TurTranscriptionResult;
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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persona-from-audio derivation (Block AA / §XXVI.7): transcribe a recording,
 * then have the LLM extract <strong>both</strong> voice traits (tone, verbosity,
 * language style, vocabulary) <strong>and</strong> audience descriptors
 * (reading level, domain expertise, language) into a <strong>draft</strong>
 * persona. The draft is <strong>never auto-saved</strong> — it is handed to the
 * existing persona review form (the "derive, never auto-apply" discipline of
 * the T387 manifest deriver).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaAudioDeriveService {

    private static final int MAX_TRANSCRIPT_CHARS = 16_000;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private static final String SYSTEM_PROMPT = """
            You analyse a transcript of a real conversation/interview and draft a
            reusable Persona for the Turing platform. Extract BOTH how the speaker
            sounds (a voice) AND who they are as a reader (an audience).

            Return EXACTLY ONE JSON object on a single line — no prose, no code
            fences. Schema:
            {"name": "<kebab-case id>", "description": "<one sentence>",
             "systemInstruction": "<2-5 sentences, second person, the voice>",
             "tone": "FORMAL|CASUAL|TECHNICAL|EXECUTIVE",
             "verbosity": <1-5>,
             "languageStyle": "NEUTRAL|DIRECT|NARRATIVE|PERSUASIVE|INSTRUCTIONAL",
             "personaKind": "BOTH",
             "readingLevel": "ELEMENTARY|MIDDLE|SECONDARY|UNDERGRADUATE|GRADUATE",
             "domainExpertise": "NOVICE|BEGINNER|INTERMEDIATE|ADVANCED|EXPERT",
             "vocabularyCeiling": "<short note on the speaker's vocabulary>",
             "primaryLanguage": "<ISO-639 code, e.g. pt|en|es>"}

            Infer values ONLY from evidence in the transcript. Ground the reading
            level and expertise in how the person actually speaks.
            """;

    private final TurTranscriptionProvider transcriptionProvider;
    private final TurLlmSummaryService llmSummaryService;

    public TurPersonaAudioDeriveService(TurTranscriptionProvider transcriptionProvider,
            TurLlmSummaryService llmSummaryService) {
        this.transcriptionProvider = transcriptionProvider;
        this.llmSummaryService = llmSummaryService;
    }

    /** Wire response: a not-yet-saved draft plus the transcript it came from. */
    public record DraftResult(boolean success, String error, String transcript,
            TurPersonaDto draft) {
    }

    public DraftResult derive(byte[] audio, String mimeType, String languageHint) {
        TurTranscriptionResult transcription =
                transcriptionProvider.transcribe(audio, mimeType, languageHint);
        if (!transcription.success() || StringUtils.isBlank(transcription.text())) {
            return new DraftResult(false, StringUtils.defaultString(transcription.error(),
                    "Could not transcribe the audio."), null, null);
        }

        String transcript = transcription.text();
        String clipped = transcript.length() > MAX_TRANSCRIPT_CHARS
                ? transcript.substring(0, MAX_TRANSCRIPT_CHARS) : transcript;
        String cacheKey = "persona-from-audio:" + Integer.toHexString(clipped.hashCode());

        SummaryResult llm = llmSummaryService.generate(cacheKey, clipped, SYSTEM_PROMPT, false);
        if (!llm.success() || StringUtils.isBlank(llm.content())) {
            return new DraftResult(false, StringUtils.defaultString(llm.error(),
                    "Could not analyse the transcript."), transcript, null);
        }

        DraftDto dto = parse(llm.content());
        if (dto == null) {
            return new DraftResult(false, "Could not parse the drafted persona.",
                    transcript, null);
        }
        return new DraftResult(true, null, transcript, toDraft(dto));
    }

    private TurPersonaDto toDraft(DraftDto dto) {
        TurPersonaDto draft = new TurPersonaDto();
        // id stays null — the UuidGenerator assigns one only on a real save.
        draft.setName(StringUtils.defaultIfBlank(dto.name(), "persona-from-audio"));
        draft.setDescription(dto.description());
        draft.setSystemInstruction(dto.systemInstruction());
        draft.setTone(parseEnum(TurPersonaTone.class, dto.tone()));
        draft.setVerbosity(dto.verbosity() == null ? 3 : Math.clamp(dto.verbosity(), 1, 5));
        draft.setLanguageStyle(parseEnum(TurPersonaLanguageStyle.class, dto.languageStyle()));
        draft.setEnabled(1);
        TurPersonaKind kind = parseEnum(TurPersonaKind.class, dto.personaKind());
        draft.setPersonaKind(kind == null ? TurPersonaKind.BOTH : kind);

        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(parseEnum(TurPersonaReadingLevel.class, dto.readingLevel()));
        audience.setDomainExpertise(parseEnum(TurPersonaDomainExpertise.class, dto.domainExpertise()));
        audience.setVocabularyCeiling(dto.vocabularyCeiling());
        audience.setPrimaryLanguage(dto.primaryLanguage());
        draft.setAudience(audience);
        return draft;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private DraftDto parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return MAPPER.readValue(raw.substring(start, end + 1), DraftDto.class);
        } catch (RuntimeException e) {
            log.warn("[PersonaFromAudio] could not parse draft: {}", e.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DraftDto(String name, String description, String systemInstruction,
            String tone, Integer verbosity, String languageStyle, String personaKind,
            String readingLevel, String domainExpertise, String vocabularyCeiling,
            String primaryLanguage) {
    }
}
