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

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.genai.transcription.TurTranscriptionResult;
import com.viglet.turing.genai.transcription.TurTranscriptionService;
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

            "name" and "systemInstruction" are REQUIRED and must be non-empty —
            they are the whole point of the draft. Always write a descriptive
            kebab-case "name" (never a placeholder) and a concrete 2-5 sentence
            "systemInstruction" in the second person that captures this specific
            voice. Never return them blank, null, or omitted. Write the
            systemInstruction in the transcript's own language.
            """;

    private final TurTranscriptionService transcriptionService;
    private final TurLlmSummaryService llmSummaryService;

    public TurPersonaAudioDeriveService(TurTranscriptionService transcriptionService,
            TurLlmSummaryService llmSummaryService) {
        this.transcriptionService = transcriptionService;
        this.llmSummaryService = llmSummaryService;
    }

    /** Wire response: a not-yet-saved draft plus the transcript it came from. */
    public record DraftResult(boolean success, String error, String transcript,
            TurPersonaDto draft) {
    }

    public DraftResult derive(byte[] audio, String mimeType, String languageHint) {
        TurTranscriptionResult transcription =
                transcriptionService.transcribe(audio, mimeType, languageHint);
        if (!transcription.success() || StringUtils.isBlank(transcription.text())) {
            return new DraftResult(false, StringUtils.defaultString(transcription.error(),
                    "Could not transcribe the audio."), null, null);
        }
        return draftFromTranscript(transcription.text());
    }

    /**
     * T715 — the analyse→draft half of {@link #derive}, split out so the async
     * persona-from-audio job (which transcribes separately, with a progress
     * listener) can reuse it. Runs the LLM behavioural analysis over the
     * transcript and maps the result to a never-saved draft persona.
     */
    public DraftResult draftFromTranscript(String transcript) {
        if (StringUtils.isBlank(transcript)) {
            return new DraftResult(false, "Empty transcript.", transcript, null);
        }
        String clipped = transcript.length() > MAX_TRANSCRIPT_CHARS
                ? transcript.substring(0, MAX_TRANSCRIPT_CHARS) : transcript;
        String cacheKey = "persona-from-audio:" + Integer.toHexString(clipped.hashCode());

        SummaryResult llm = llmSummaryService.generate(cacheKey, clipped, SYSTEM_PROMPT, false);
        if (!llm.success() || StringUtils.isBlank(llm.content())) {
            return new DraftResult(false, StringUtils.defaultString(llm.error(),
                    "Could not analyse the transcript."), transcript, null);
        }

        // Debug aid: the raw model draft is the ground truth when a field (esp.
        // name / systemInstruction) comes back empty — logged verbatim on request.
        log.info("[PersonaFromAudio] transcript {} chars, raw LLM draft: {}",
                transcript.length(), llm.content());

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
        draft.setDescription(dto.description());
        draft.setTone(parseEnum(TurPersonaTone.class, dto.tone()));
        draft.setVerbosity(dto.verbosity() == null ? 3 : Math.clamp(dto.verbosity(), 1, 5));
        draft.setLanguageStyle(parseEnum(TurPersonaLanguageStyle.class, dto.languageStyle()));
        draft.setEnabled(1);
        TurPersonaKind kind = parseEnum(TurPersonaKind.class, dto.personaKind());
        draft.setPersonaKind(kind == null ? TurPersonaKind.BOTH : kind);

        // name / systemInstruction are the two free-text fields the model must
        // author; weak models sometimes leave them blank while still filling the
        // constrained enums. Never hand the review form an empty draft for
        // either — synthesise a deterministic fallback and flag it in the log.
        draft.setName(resolveName(dto));
        draft.setSystemInstruction(resolveSystemInstruction(dto, draft));

        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(parseEnum(TurPersonaReadingLevel.class, dto.readingLevel()));
        audience.setDomainExpertise(parseEnum(TurPersonaDomainExpertise.class, dto.domainExpertise()));
        audience.setVocabularyCeiling(dto.vocabularyCeiling());
        audience.setPrimaryLanguage(dto.primaryLanguage());
        draft.setAudience(audience);
        return draft;
    }

    /** LLM name, else a slug of the description, else the generic placeholder. */
    private String resolveName(DraftDto dto) {
        if (StringUtils.isNotBlank(dto.name())) {
            return dto.name().trim();
        }
        String fromDescription = slugify(dto.description());
        if (StringUtils.isNotBlank(fromDescription)) {
            log.warn("[PersonaFromAudio] model returned no name; derived '{}' from description",
                    fromDescription);
            return fromDescription;
        }
        log.warn("[PersonaFromAudio] model returned no name and no description; using placeholder");
        return "persona-from-audio";
    }

    /**
     * LLM systemInstruction, else a voice contract synthesised from the extracted
     * traits so the review form is never handed an empty (and most important)
     * field. A warn marks the synthesised path so a recurring blank is visible.
     */
    private String resolveSystemInstruction(DraftDto dto, TurPersonaDto draft) {
        if (StringUtils.isNotBlank(dto.systemInstruction())) {
            return dto.systemInstruction().trim();
        }
        StringBuilder voice = new StringBuilder("Respond in the voice extracted from the recording.");
        if (draft.getTone() != null) {
            voice.append(" Keep a ").append(draft.getTone().name().toLowerCase(Locale.ROOT))
                    .append(" tone.");
        }
        if (draft.getLanguageStyle() != null) {
            voice.append(" Use a ").append(draft.getLanguageStyle().name().toLowerCase(Locale.ROOT))
                    .append(" style.");
        }
        voice.append(" Aim for verbosity ").append(draft.getVerbosity()).append(" of 5.");
        if (StringUtils.isNotBlank(dto.vocabularyCeiling())) {
            voice.append(" Vocabulary: ").append(dto.vocabularyCeiling().trim()).append('.');
        }
        voice.append(" Review and refine this before saving.");
        log.warn("[PersonaFromAudio] model returned no systemInstruction; synthesised one from traits");
        return voice.toString();
    }

    /** Lowercase kebab-case slug of the first few words, or blank when unusable. */
    private static String slugify(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String slug = StringUtils.strip(text.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-"), "-");
        int fifth = ordinalDashIndex(slug);
        return fifth > 0 ? slug.substring(0, fifth) : slug;
    }

    /** Index of the 5th dash (word boundary) so a slug is capped to ~5 words, else -1. */
    private static int ordinalDashIndex(String slug) {
        int dashes = 0;
        for (int i = 0; i < slug.length(); i++) {
            if (slug.charAt(i) == '-' && ++dashes == 5) {
                return i;
            }
        }
        return -1;
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
