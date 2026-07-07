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
 * T586 / §XXXIII.1 — built-in CODE grader wrapping the historical <b>node</b>
 * dimension: the flow cursor the replay landed on vs the case's
 * {@code expectedNodeId}. Skipped when the case asserts no node.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNodeGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.NODE;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String expectedNodeId = ctx.evalCase().getExpectedNodeId();
        return expectedNodeId != null && !expectedNodeId.isBlank();
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        boolean match = ctx.evalCase().getExpectedNodeId().equals(ctx.finalNodeId());
        return TurEvalGraderResult.binary(match);
    }
}
