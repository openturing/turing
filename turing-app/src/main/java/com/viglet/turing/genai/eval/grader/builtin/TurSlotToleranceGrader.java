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

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;

/**
 * T588 / §XXXIII.3 — config-driven CODE grader: one captured slot equals
 * {@code expected}, with numeric tolerance or case-insensitive string equality.
 * Config: {@code slot} (required), {@code expected} (required), {@code tolerance}
 * (numeric, default 0 — used when both values parse as numbers), {@code
 * ignoreCase} (default false — string comparison). Unlike the built-in
 * slot-match dimension this is a single, tolerant, config-addressable slot
 * assertion.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSlotToleranceGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.SLOT_TOLERANCE;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.configString("slot") != null && config.configString("expected") != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String slot = config.configString("slot");
        String expected = config.configString("expected");
        Map<String, String> slots = ctx.capturedSlots();
        String actual = slots == null ? null : slots.get(slot);
        if (actual == null) {
            return TurEvalGraderResult.scored(0d, false, "fail", "slot '" + slot + "' was not captured");
        }
        Double expectedNum = parseNumber(expected);
        Double actualNum = parseNumber(actual);
        boolean match;
        if (expectedNum != null && actualNum != null) {
            double tolerance = Math.abs(config.configDouble("tolerance", 0d));
            match = Math.abs(expectedNum - actualNum) <= tolerance;
        } else if (config.configBoolean("ignoreCase", false)) {
            match = expected.trim().equalsIgnoreCase(actual.trim());
        } else {
            match = expected.trim().equals(actual.trim());
        }
        return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                match ? null : "slot '" + slot + "' = '" + actual + "' != expected '" + expected + "'");
    }

    private static Double parseNumber(String s) {
        try {
            return Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
