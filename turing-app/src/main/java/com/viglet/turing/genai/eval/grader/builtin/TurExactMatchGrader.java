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
 * T588 / §XXXIII.3 — config-driven CODE grader: the final answer equals
 * {@code expected}. Config: {@code expected} (required), {@code ignoreCase}
 * (default false), {@code trim} (default true).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurExactMatchGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.EXACT;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.configString("expected") != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String expected = config.configString("expected");
        String actual = ctx.finalAnswer();
        boolean trim = config.configBoolean("trim", true);
        boolean ignoreCase = config.configBoolean("ignoreCase", false);
        String e = trim ? expected.trim() : expected;
        String a = trim ? actual.trim() : actual;
        boolean match = ignoreCase ? e.equalsIgnoreCase(a) : e.equals(a);
        return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                match ? null : "answer did not exactly match the expected value");
    }
}
