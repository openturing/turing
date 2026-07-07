/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader.builtin;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;

/**
 * T588 / §XXXIII.3 — config-driven CODE grader: a number lies within
 * {@code [min, max]}. Config: {@code min} and/or {@code max} (at least one),
 * {@code slot} (optional — read the number from that captured slot; otherwise
 * the first number found in the final answer). Fails when no number is found.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNumericRangeGrader implements TurEvalGrader {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.NUMERIC_RANGE;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.has("min") || config.has("max");
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        Double value = resolveNumber(ctx, config);
        if (value == null) {
            return TurEvalGraderResult.scored(0d, false, "fail", "no number found to range-check");
        }
        boolean okMin = !config.has("min") || value >= config.configDouble("min", Double.NEGATIVE_INFINITY);
        boolean okMax = !config.has("max") || value <= config.configDouble("max", Double.POSITIVE_INFINITY);
        boolean match = okMin && okMax;
        return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                match ? null : value + " is outside the allowed range");
    }

    private static Double resolveNumber(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String slot = config.configString("slot");
        String source;
        if (slot != null) {
            Map<String, String> slots = ctx.capturedSlots();
            source = slots == null ? null : slots.get(slot);
        } else {
            source = ctx.finalAnswer();
        }
        if (source == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(source);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.valueOf(m.group().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
