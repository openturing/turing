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

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;

/**
 * T588 / §XXXIII.3 — config-driven CODE grader: the final answer contains
 * {@code substring}. Config: {@code substring} (required), {@code ignoreCase}
 * (default false).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurContainsGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.CONTAINS;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.configString("substring") != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String substring = config.configString("substring");
        String actual = ctx.finalAnswer();
        boolean ignoreCase = config.configBoolean("ignoreCase", false);
        boolean match = ignoreCase
                ? actual.toLowerCase().contains(substring.toLowerCase())
                : actual.contains(substring);
        return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                match ? null : "answer did not contain the expected substring");
    }
}
