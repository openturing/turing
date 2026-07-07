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
 * T592 / §XXXIII.7 — the missing third grader kind: HUMAN. It cannot decide
 * automatically, so it <b>defers</b> — returning a {@link
 * TurEvalGraderResult#deferred deferred} verdict. The eval runner reacts by
 * marking the case (and report) {@code PENDING_REVIEW} and parking a {@code
 * TurEvalReviewTask} for the T593 review inbox. Opt-in: only in the stack when
 * configured, so it never affects a legacy default-stack run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurHumanReviewGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.HUMAN_REVIEW;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.HUMAN;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return true;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return TurEvalGraderResult.deferred("awaiting human review");
    }
}
