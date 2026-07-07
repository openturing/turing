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
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;

/**
 * T586 / §XXXIII.1 — built-in CODE grader wrapping the historical <b>outcome</b>
 * dimension: the terminal outcome label observed after the replay vs the case's
 * {@code expectedOutcome}. Skipped when the case asserts {@code ANY}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurOutcomeGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.OUTCOME;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        TurAgentEvalExpectedOutcome expected = ctx.evalCase().getExpectedOutcome();
        return expected != null && expected != TurAgentEvalExpectedOutcome.ANY;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        boolean match = ctx.evalCase().getExpectedOutcome().name().equals(ctx.actualOutcome());
        return TurEvalGraderResult.binary(match);
    }
}
