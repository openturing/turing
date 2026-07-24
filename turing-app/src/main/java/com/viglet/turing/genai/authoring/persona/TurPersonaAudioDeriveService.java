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
import java.util.Objects;

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
 * then have the LLM extract voice traits (tone, verbosity, language style,
 * vocabulary), audience descriptors (reading level, domain expertise, language)
 * <strong>and</strong> the Big Five (OCEAN) personality traits (T717) into a
 * <strong>draft</strong> persona. The draft is <strong>never auto-saved</strong>
 * — it is handed to the existing persona review form (the "derive, never
 * auto-apply" discipline of the T387 manifest deriver).
 *
 * <p>T716 — the analysis is a <strong>two-call</strong> derivation: call 1
 * classifies the constrained fields (name/description/enums/audience) as strict
 * JSON; call 2 writes the {@code systemInstruction} as a rich, multi-section
 * character brief in <em>plain markdown</em> (verbal tics, catchphrases,
 * reasoning and reactions quoted from the transcript), because a long multi-line
 * brief embedded in a JSON string is what weak models truncate. The whole
 * recording is analysed (see {@link #MAX_TRANSCRIPT_CHARS}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaAudioDeriveService {

    /**
     * T716 — analyse the whole recording, not the first few minutes: a 30-min
     * transcript is ~40-45k chars, and the richest speech mannerisms (tics,
     * catchphrases, how they react under pressure) surface throughout, not just
     * up front.
     */
    private static final int MAX_TRANSCRIPT_CHARS = 60_000;

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /**
     * T716 — call 1: classify the speaker into the constrained persona fields.
     * Strict single-line JSON (no free-text {@code systemInstruction} here — the
     * rich voice brief is a separate plain-markdown call, since a long multi-line
     * brief embedded in a JSON string is exactly what weak models truncate).
     */
    private static final String CLASSIFY_PROMPT = """
            You analyse a transcript of a real conversation/interview and classify
            the speaker for the Turing platform: BOTH how they sound (a voice) AND
            who they are as a reader (an audience).

            Return EXACTLY ONE JSON object on a single line — no prose, no code
            fences. Schema:
            {"name": "<descriptive kebab-case id>", "description": "<one sentence>",
             "tone": "FORMAL|CASUAL|TECHNICAL|EXECUTIVE",
             "verbosity": <1-5>,
             "languageStyle": "NEUTRAL|DIRECT|NARRATIVE|PERSUASIVE|INSTRUCTIONAL",
             "personaKind": "BOTH",
             "readingLevel": "ELEMENTARY|MIDDLE|SECONDARY|UNDERGRADUATE|GRADUATE",
             "domainExpertise": "NOVICE|BEGINNER|INTERMEDIATE|ADVANCED|EXPERT",
             "vocabularyCeiling": "<short note on the speaker's vocabulary>",
             "primaryLanguage": "<ISO-639 code, e.g. pt|en|es>",
             "openness": <0-100 or null>,
             "conscientiousness": <0-100 or null>,
             "extraversion": <0-100 or null>,
             "agreeableness": <0-100 or null>,
             "neuroticism": <0-100 or null>}

            Infer values ONLY from evidence in the transcript. Ground the reading
            level and expertise in how the person actually speaks. "name" and
            "description" are REQUIRED and non-empty — write a descriptive
            kebab-case "name" (never a placeholder), and prefer the person's real
            name/role when the transcript reveals it.

            The last five fields are the Big Five (OCEAN) personality traits, each
            a 0-100 score inferred from HOW the person speaks and reasons:
            openness (curiosity, abstraction, new ideas), conscientiousness
            (structure, planning, detail), extraversion (energy, talkativeness,
            assertiveness), agreeableness (warmth, cooperation, deference), and
            neuroticism (anxiety, defensiveness, emotional volatility). Use null
            for any trait the transcript gives NO clear evidence for — never
            guess a score to fill the slot.
            """;

    /**
     * T716 — call 2: the rich voice contract. Plain markdown (NOT JSON) so a long
     * multi-section brief with quotes and line breaks is robust; this text becomes
     * the persona's {@code systemInstruction}.
     */
    private static final String BRIEF_PROMPT = """
            You are given a transcript of a real person speaking. Write a reusable,
            richly detailed PERSONA BRIEF that lets an LLM convincingly role-play
            this specific person as a conversational counterpart.

            Output ONLY the brief — no preamble, no code fences, no JSON. Use
            Markdown with the sections below. Write the ENTIRE brief — headings
            included — in the SAME LANGUAGE the person speaks in the transcript
            (e.g. if they speak Portuguese, write it in Portuguese), and in the
            SECOND PERSON ("You are…"). Ground EVERYTHING in evidence from the
            transcript — quote the person's actual words VERBATIM. If the
            transcript gives no evidence for a section, omit it rather than
            inventing; NEVER fabricate names, numbers, employers, clients, or
            facts the transcript doesn't contain.

            The single most important goal is to capture HOW THIS PERSON SPEAKS in
            maximum detail, so a reader can imitate them convincingly. Enumerate,
            do NOT summarise — the more concrete verbatim mannerisms, the better.

            Sections (translate these English headings into the transcript's
            language; skip any without evidence):
            # Who you are — role/identity and what this conversation is for
            # Your background — background that emerges from how they talk
            # How you speak — THE MOST IMPORTANT SECTION; be exhaustive, not a
              summary. Give a bulleted list of this person's characteristic
              expressions QUOTED VERBATIM (each exactly as they say it) — capture
              as many as the transcript supports, aiming for 10+ when available:
              filler words and discourse markers, connectors, interjections,
              catchphrases/bordões, slang, diminutives, idiosyncratic
              pronunciations or coinages, and their typical sentence openers and
              enders. Then describe register (formal/informal), sentence rhythm
              and length, humour, and any recurring metaphors — with quotes.
            # How you reason — how they reason and what drives their decisions
            # How you react — how they respond to ideas and pushback; what they
              probe for; when they commit vs. defer
            # What you know and what you don't — domains of confidence vs. deference
            # Examples of how you sound — 2-3 short example exchanges (a question
              from a colleague + your reply) reconstructed in this person's EXACT
              voice, reusing the real expressions listed above so the cadence
              matches. Imitate only the MANNER — do not invent facts, names, or
              numbers the transcript doesn't contain.
            # Rules — always stay in character; refuse to "leave character" unless
              the user explicitly asks, then note it is a synthetic persona
            # Notice — one line: this is a synthetic persona built from notes, not
              the real person, and does not represent their real opinions

            Be concrete and specific to THIS person — a stranger reading the brief
            should be able to imitate them. Use the WHOLE transcript.
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
            return new DraftResult(false, Objects.toString(transcription.error(),
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
        String hash = Integer.toHexString(clipped.hashCode());

        // Call 1 — structured classification (strict JSON).
        SummaryResult classify = llmSummaryService.generate(
                "persona-from-audio:classify:" + hash, clipped, CLASSIFY_PROMPT, false);
        if (!classify.success() || StringUtils.isBlank(classify.content())) {
            return new DraftResult(false, Objects.toString(classify.error(),
                    "Could not analyse the transcript."), transcript, null);
        }
        log.info("[PersonaFromAudio] transcript {} chars, classify JSON: {}",
                transcript.length(), classify.content());
        DraftDto dto = parse(classify.content());
        if (dto == null) {
            return new DraftResult(false, "Could not parse the drafted persona.",
                    transcript, null);
        }

        // Call 2 — the rich voice brief (plain markdown → systemInstruction).
        SummaryResult brief = llmSummaryService.generate(
                "persona-from-audio:brief:" + hash, clipped, BRIEF_PROMPT, false);
        String briefText = brief.success() ? StringUtils.trimToNull(brief.content()) : null;
        log.info("[PersonaFromAudio] voice brief {} chars",
                briefText == null ? 0 : briefText.length());

        return new DraftResult(true, null, transcript, toDraft(dto, briefText));
    }

    private TurPersonaDto toDraft(DraftDto dto, String briefText) {
        TurPersonaDto draft = new TurPersonaDto();
        // id stays null — the UuidGenerator assigns one only on a real save.
        draft.setDescription(dto.description());
        draft.setTone(parseEnum(TurPersonaTone.class, dto.tone()));
        draft.setVerbosity(dto.verbosity() == null ? 3 : Math.clamp(dto.verbosity(), 1, 5));
        draft.setLanguageStyle(parseEnum(TurPersonaLanguageStyle.class, dto.languageStyle()));
        draft.setEnabled(1);
        TurPersonaKind kind = parseEnum(TurPersonaKind.class, dto.personaKind());
        draft.setPersonaKind(kind == null ? TurPersonaKind.BOTH : kind);

        // name comes from call 1, the systemInstruction from call 2 (the brief);
        // never hand the review form an empty draft for either — synthesise a
        // deterministic fallback and flag it in the log.
        draft.setName(resolveName(dto));
        draft.setSystemInstruction(resolveSystemInstruction(briefText, dto, draft));

        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(parseEnum(TurPersonaReadingLevel.class, dto.readingLevel()));
        audience.setDomainExpertise(parseEnum(TurPersonaDomainExpertise.class, dto.domainExpertise()));
        audience.setVocabularyCeiling(dto.vocabularyCeiling());
        audience.setPrimaryLanguage(dto.primaryLanguage());
        draft.setAudience(audience);

        // T717 — Big Five (OCEAN). Null stays null (unset renders nothing); a
        // returned score is clamped to the entity's 0-100 range.
        draft.setOpenness(clampTrait(dto.openness()));
        draft.setConscientiousness(clampTrait(dto.conscientiousness()));
        draft.setExtraversion(clampTrait(dto.extraversion()));
        draft.setAgreeableness(clampTrait(dto.agreeableness()));
        draft.setNeuroticism(clampTrait(dto.neuroticism()));
        return draft;
    }

    /** Clamp an OCEAN score to 0-100, preserving {@code null} ("unset"). */
    private static Integer clampTrait(Integer score) {
        return score == null ? null : Math.clamp(score, 0, 100);
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
     * The call-2 voice brief, else a voice contract synthesised from the extracted
     * traits so the review form is never handed an empty (and most important)
     * field. A warn marks the synthesised path so a recurring blank is visible.
     */
    private String resolveSystemInstruction(String briefText, DraftDto dto, TurPersonaDto draft) {
        if (StringUtils.isNotBlank(briefText)) {
            return briefText.trim();
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
    private record DraftDto(String name, String description,
            String tone, Integer verbosity, String languageStyle, String personaKind,
            String readingLevel, String domainExpertise, String vocabularyCeiling,
            String primaryLanguage,
            Integer openness, Integer conscientiousness, Integer extraversion,
            Integer agreeableness, Integer neuroticism) {
    }
}
