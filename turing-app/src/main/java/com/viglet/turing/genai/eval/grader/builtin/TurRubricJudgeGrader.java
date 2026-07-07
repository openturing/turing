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

import java.util.List;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.eval.TurEvalJudgePrompt;
import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderKind;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;

import lombok.extern.slf4j.Slf4j;

/**
 * T586 / §XXXIII.1 — built-in MODEL grader wrapping the historical <b>rubric</b>
 * dimension: the case's natural-language rubric scored by the bilingual,
 * provider-agnostic LLM judge ({@link TurEvalJudgePrompt}). Uses the run's judge
 * model from the grading context; fails closed (score 0, fail) on any error, so
 * the aggregate is unchanged from the pre-SPI inline judge.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurRubricJudgeGrader implements TurEvalGrader {

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.RUBRIC;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.MODEL;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        String rubric = ctx.evalCase().getRubric();
        return rubric != null && !rubric.isBlank() && ctx.judgeModel() != null;
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        ChatModel judgeModel = ctx.judgeModel();
        String rubric = ctx.evalCase().getRubric();
        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(TurEvalJudgePrompt.SYSTEM),
                    new UserMessage(TurEvalJudgePrompt.buildUser(rubric, ctx.userTurns()))));
            String reply = judgeModel.call(prompt).getResult().getOutput().getText();
            TurEvalJudgePrompt.Verdict verdict = TurEvalJudgePrompt.parse(reply);
            return TurEvalGraderResult.scored(verdict.score(), verdict.pass(),
                    verdict.pass() ? "pass" : "fail", verdict.rationale());
        } catch (RuntimeException e) {
            log.warn("[AgentEval] judge failed: {}", e.getMessage());
            return TurEvalGraderResult.scored(0d, false, "fail", "judge error: " + e.getMessage());
        }
    }
}
