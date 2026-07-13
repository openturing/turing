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

import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;

/**
 * T605 — opt-in translation of a persona's <b>Style Guidelines</b> into concrete
 * LLM sampling parameters for a turn. Historically the style guidelines
 * (tone / verbosity / language style) only reached the model as <em>text</em> in
 * the system prompt (see {@code TurPersonaStaticPromptCache}); the model's
 * {@code temperature}/{@code maxTokens} came solely from the
 * {@link com.viglet.turing.persistence.model.llm.TurLLMInstance}.
 *
 * <p>When a persona sets {@code calibrateModelParams = 1}, this pure mapper
 * derives per-turn overrides so the style also shapes the <em>sampling</em>:
 *
 * <ul>
 *   <li><b>verbosity → maxTokens</b> — 1 (terse) … 5 (expansive) maps to
 *       256 / 512 / 1024 / 2048 / 4096 output tokens ({@code 128 << verbosity});</li>
 *   <li><b>tone → temperature</b> — TECHNICAL 0.2, FORMAL 0.3, EXECUTIVE 0.4,
 *       CASUAL 0.8 (a null tone leaves temperature untouched).</li>
 * </ul>
 *
 * <p><b>T717 / §XLVI.1 — Big Five (OCEAN) nudge.</b> When the persona also carries
 * personality traits, they <em>nudge</em> the calibrated temperature so a cohort of
 * synthetic participants answers with different sampling variability instead of one
 * flat voice: {@code openness} and {@code neuroticism} raise temperature (more
 * divergent / more hedging-and-variable), {@code conscientiousness} lowers it (more
 * controlled). Each set trait contributes up to ±{@value #TRAIT_MAX_CONTRIBUTION}
 * around its 50 midpoint; the summed delta is clamped to
 * ±{@value #PERSONALITY_MAX_DELTA} and applied on top of the tone temperature (or a
 * {@value #DEFAULT_TEMPERATURE} baseline when no tone is set but traits are), then
 * clamped to [0,1]. Unset traits contribute nothing; when <em>all</em> traits are
 * unset the temperature is exactly the tone-derived value, so calibrated personas
 * without OCEAN traits are unchanged.
 *
 * <p>A {@code null} field in {@link Params} means "no override" — the caller
 * keeps the instance's value for that parameter. When the persona is null or has
 * not opted in, {@link #forPersona} returns {@link Params#NONE} and nothing is
 * touched, so legacy behaviour is byte-for-byte unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurPersonaModelCalibration {

    /** Per-turn sampling overrides; a {@code null} field means "leave as is". */
    public record Params(Double temperature, Integer maxTokens) {
        public static final Params NONE = new Params(null, null);

        public boolean hasAny() {
            return temperature != null || maxTokens != null;
        }
    }

    private TurPersonaModelCalibration() {
    }

    /** Opt-in flag value on {@link TurPersona#getCalibrateModelParams()}. */
    private static final int ENABLED = 1;
    private static final int MIN_VERBOSITY = 1;
    private static final int MAX_VERBOSITY = 5;

    /** Neutral temperature used as the nudge baseline when no tone is set. */
    private static final double DEFAULT_TEMPERATURE = 0.5;
    /** Max absolute temperature shift a single set trait can contribute. */
    private static final double TRAIT_MAX_CONTRIBUTION = 0.15;
    /** Cap on the summed personality temperature delta. */
    private static final double PERSONALITY_MAX_DELTA = 0.3;
    private static final double TRAIT_MIDPOINT = 50.0;
    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 1.0;

    public static Params forPersona(TurPersona persona) {
        if (persona == null || persona.getCalibrateModelParams() != ENABLED) {
            return Params.NONE;
        }
        return new Params(temperatureFor(persona),
                maxTokensForVerbosity(persona.getVerbosity()));
    }

    private static Double temperatureFor(TurPersona persona) {
        Double base = temperatureForTone(persona.getTone());
        Double delta = personalityTemperatureDelta(persona);
        if (delta == null) {
            // No OCEAN traits set — tone-only behaviour, unchanged.
            return base;
        }
        double baseline = base != null ? base : DEFAULT_TEMPERATURE;
        double temperature = clamp(baseline + delta, MIN_TEMPERATURE, MAX_TEMPERATURE);
        // Round to 2 decimals so the value is stable/testable (avoids fp noise).
        return Math.round(temperature * 100.0) / 100.0;
    }

    /**
     * Summed, clamped temperature delta from the Big Five traits, or {@code null}
     * when no trait is set. Openness and neuroticism push up (more divergent /
     * hedging), conscientiousness pushes down (more controlled). Extraversion and
     * agreeableness are behavioral only (they shape the prompt, not the sampling).
     */
    private static Double personalityTemperatureDelta(TurPersona persona) {
        Double up = contribution(persona.getOpenness());
        Double neuro = contribution(persona.getNeuroticism());
        Double down = contribution(persona.getConscientiousness());
        if (up == null && neuro == null && down == null) {
            return null;
        }
        double delta = nz(up) + nz(neuro) - nz(down);
        return clamp(delta, -PERSONALITY_MAX_DELTA, PERSONALITY_MAX_DELTA);
    }

    /** ±{@link #TRAIT_MAX_CONTRIBUTION} scaled by the trait's distance from 50, or null when unset. */
    private static Double contribution(Integer trait) {
        if (trait == null) {
            return null;
        }
        int clamped = (int) clamp(trait, 0, 100);
        return (clamped - TRAIT_MIDPOINT) / TRAIT_MIDPOINT * TRAIT_MAX_CONTRIBUTION;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Double temperatureForTone(TurPersonaTone tone) {
        if (tone == null) {
            return null;
        }
        return switch (tone) {
            case TECHNICAL -> 0.2;
            case FORMAL -> 0.3;
            case EXECUTIVE -> 0.4;
            case CASUAL -> 0.8;
        };
    }

    private static Integer maxTokensForVerbosity(int verbosity) {
        int clamped = Math.max(MIN_VERBOSITY, Math.min(MAX_VERBOSITY, verbosity));
        // 1 -> 256, 2 -> 512, 3 -> 1024, 4 -> 2048, 5 -> 4096.
        return 128 << clamped;
    }
}
