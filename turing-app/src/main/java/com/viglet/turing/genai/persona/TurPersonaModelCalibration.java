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

    public static Params forPersona(TurPersona persona) {
        if (persona == null || persona.getCalibrateModelParams() != ENABLED) {
            return Params.NONE;
        }
        return new Params(temperatureForTone(persona.getTone()),
                maxTokensForVerbosity(persona.getVerbosity()));
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
