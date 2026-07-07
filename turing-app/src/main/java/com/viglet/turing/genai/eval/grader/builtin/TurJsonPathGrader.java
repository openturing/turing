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

import com.jayway.jsonpath.JsonPath;
import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;

/**
 * T588 / §XXXIII.3 — config-driven CODE grader for structured answers: reads a
 * JSON-path {@code path} from the final answer (parsed as JSON). Config:
 * {@code path} (required), {@code expected} (optional — when present the
 * extracted value must string-equal it; when absent the path must merely
 * resolve to a non-null value). A non-JSON answer or a missing path fails the
 * case (never throws).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurJsonPathGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.JSON_PATH;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return config.configString("path") != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String path = config.configString("path");
        String expected = config.configString("expected");
        try {
            Object value = JsonPath.read(ctx.finalAnswer(), path);
            if (expected == null) {
                boolean present = value != null;
                return TurEvalGraderResult.scored(present ? 1d : 0d, present, present ? "pass" : "fail",
                        present ? null : "json path resolved to null");
            }
            boolean match = expected.equals(String.valueOf(value));
            return TurEvalGraderResult.scored(match ? 1d : 0d, match, match ? "pass" : "fail",
                    match ? null : "json path value '" + value + "' != expected '" + expected + "'");
        } catch (RuntimeException e) {
            return TurEvalGraderResult.scored(0d, false, "fail", "json path failed: " + e.getMessage());
        }
    }
}
