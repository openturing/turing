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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;

/**
 * T588 / §XXXIII.3 — config-driven CODE grader: the final answer matches the
 * {@code pattern} regex (a substring find, not a full match). Config:
 * {@code pattern} (required), {@code ignoreCase} (default false),
 * {@code fullMatch} (default false → find; true → the whole answer must match).
 * An invalid pattern fails the case (never throws).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurRegexGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.REGEX;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.configString("pattern") != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String patternStr = config.configString("pattern");
        boolean ignoreCase = config.configBoolean("ignoreCase", false);
        boolean fullMatch = config.configBoolean("fullMatch", false);
        try {
            Pattern pattern = Pattern.compile(patternStr, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
            var matcher = pattern.matcher(ctx.finalAnswer());
            boolean match = fullMatch ? matcher.matches() : matcher.find();
            return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                    match ? null : "answer did not match the pattern");
        } catch (PatternSyntaxException e) {
            return TurEvalGraderResult.scored(0d, false, "fail", "invalid regex: " + e.getMessage());
        }
    }
}
