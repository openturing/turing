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
import com.viglet.turing.genai.eval.grader.TurGroovyGraderEngine;

/**
 * T589 / §XXXIII.4 — config-driven CODE grader that runs a custom Groovy
 * {@code script} (from {@code configJson}) via {@link TurGroovyGraderEngine},
 * so a team can assert domain rules the built-in library can't express without
 * a backend change. Opt-in (applies only when a script is configured);
 * errors-as-fail.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurGroovyGrader implements TurEvalGrader {

    private final TurGroovyGraderEngine engine;

    public TurGroovyGrader(TurGroovyGraderEngine engine) {
        this.engine = engine;
    }

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.GROOVY;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.CODE;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String script = config.configString("script");
        return script != null && !script.isBlank();
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        return engine.evaluate(config.configString("script"), ctx);
    }
}
