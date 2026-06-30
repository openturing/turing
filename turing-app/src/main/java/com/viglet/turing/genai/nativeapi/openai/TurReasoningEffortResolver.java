/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * T179 / §X.13.b — resolves the OpenAI {@code reasoning.effort} budget for a
 * given pipeline <b>stage</b>.
 *
 * <p>Reasoning models trade latency/cost for depth via {@code reasoning.effort}
 * ({@code minimal} / {@code low} / {@code medium} / {@code high}). The right
 * budget differs by where the call runs: an interactive chat turn wants a fast,
 * cheap answer ({@code low}); an overnight/background turn can afford the deepest
 * reasoning ({@code high}); an LLM-judge verdict sits in between ({@code medium}).
 * This resolver encodes that per-stage default policy in one place.
 *
 * <p>A caller may override the stage default with an explicit per-agent value
 * (the {@code reasoning-effort} Request Option). {@link #resolve(Stage, String)}
 * returns the explicit value when set, otherwise the stage's default. A blank
 * explicit value falls through to the default, so the option being present but
 * empty is the same as unset.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurReasoningEffortResolver {

    /** A point in the pipeline whose reasoning budget differs from the others. */
    public enum Stage {
        /** Interactive chat turn — latency-sensitive, cheapest reasoning. */
        LIVE,
        /** Background / overnight work (batch summarization, re-embedding) — deepest. */
        BACKGROUND,
        /** LLM-as-judge verdict (eval rubric) — balanced. */
        JUDGE
    }

    /** The documented per-stage default policy: live=low, background=high, judge=medium. */
    private static final Map<Stage, String> STAGE_DEFAULTS = Map.of(
            Stage.LIVE, "low",
            Stage.BACKGROUND, "high",
            Stage.JUDGE, "medium");

    /**
     * The effort for {@code stage}: the {@code explicit} per-agent value when it
     * is a non-blank known level, otherwise the stage default. Unknown explicit
     * values fall through to the default rather than producing an invalid request.
     */
    public String resolve(Stage stage, String explicit) {
        if (StringUtils.hasText(explicit)) {
            String normalized = explicit.trim().toLowerCase(Locale.ROOT);
            if (isKnownLevel(normalized)) {
                return normalized;
            }
        }
        return STAGE_DEFAULTS.get(stage);
    }

    private static boolean isKnownLevel(String level) {
        return switch (level) {
            case "minimal", "low", "medium", "high" -> true;
            default -> false;
        };
    }
}
