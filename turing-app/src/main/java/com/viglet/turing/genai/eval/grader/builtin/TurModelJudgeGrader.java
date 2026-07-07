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
import com.viglet.turing.genai.eval.grader.TurEvalModelJudge;
import com.viglet.turing.genai.eval.grader.TurEvalModelStrategy;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T590 / §XXXIII.5 — config-driven MODEL grader generalizing the lone rubric
 * judge into strategies (LABEL / SCORE / CRITERIA / PAIRWISE). Config:
 * {@code strategy}, {@code prompt} (instruction; falls back to the case rubric),
 * {@code criteria} (list, CRITERIA), {@code reference} (PAIRWISE),
 * {@code passThreshold} (SCORE, default 0.5), and an optional per-grader
 * {@code model} (LLM instance id — otherwise the run's judge model). Judges via
 * the bilingual {@link TurEvalModelJudge} JSON verdict; fails closed on error.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurModelJudgeGrader implements TurEvalGrader {

    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;

    public TurModelJudgeGrader(TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory, TurSecretCryptoService secretCryptoService) {
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
    }

    @Override
    public String graderId() {
        return TurEvalBuiltinGraders.MODEL_JUDGE;
    }

    @Override
    public TurEvalGraderKind kind() {
        return TurEvalGraderKind.MODEL;
    }

    @Override
    public boolean appliesTo(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        boolean configured = config.has("strategy") || config.configString("prompt") != null
                || !config.configStringList("criteria").isEmpty()
                || config.configString("reference") != null || ctx.referenceAnswer() != null;
        return configured && (ctx.judgeModel() != null || config.configString("model") != null);
    }

    @Override
    public TurEvalGraderResult grade(TurEvalGradingContext ctx, TurEvalGraderConfigView config) {
        ChatModel model = resolveModel(config, ctx);
        if (model == null) {
            return TurEvalGraderResult.scored(0d, false, "fail", "no judge model available");
        }
        TurEvalModelStrategy strategy = TurEvalModelStrategy.from(config.configString("strategy"));
        String instruction = config.configString("prompt");
        if (instruction == null && ctx.evalCase() != null) {
            instruction = ctx.evalCase().getRubric();
        }
        String reference = config.configString("reference");
        if (reference == null) {
            reference = ctx.referenceAnswer();
        }
        String system = TurEvalModelJudge.system(strategy);
        String user = TurEvalModelJudge.user(strategy, instruction,
                config.configStringList("criteria"), reference, ctx.userTurns(), ctx.finalAnswer());
        try {
            String reply = model.call(new Prompt(List.of(new SystemMessage(system),
                    new UserMessage(user)))).getResult().getOutput().getText();
            TurEvalJudgePrompt.Verdict verdict = TurEvalModelJudge.parse(reply);
            boolean passed = verdict.pass();
            if (strategy == TurEvalModelStrategy.SCORE) {
                passed = verdict.score() >= config.configDouble("passThreshold", 0.5d);
            }
            return TurEvalGraderResult.scored(verdict.score(), passed, passed ? "pass" : "fail",
                    verdict.rationale());
        } catch (RuntimeException e) {
            log.warn("[AgentEval] model-judge failed: {}", e.getMessage());
            return TurEvalGraderResult.scored(0d, false, "fail", "judge error: " + e.getMessage());
        }
    }

    /** Per-grader {@code model} instance id, else the run's judge model. */
    private ChatModel resolveModel(TurEvalGraderConfigView config, TurEvalGradingContext ctx) {
        String modelId = config.configString("model");
        if (modelId == null || modelId.isBlank()) {
            return ctx.judgeModel();
        }
        try {
            return llmInstanceRepository.findById(modelId)
                    .filter(l -> l.getEnabled() == 1)
                    .map(this::createChatModel)
                    .orElseGet(ctx::judgeModel);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] model-judge model '{}' unresolved: {}", modelId, e.getMessage());
            return ctx.judgeModel();
        }
    }

    private ChatModel createChatModel(TurLLMInstance instance) {
        String apiKey = secretCryptoService.decrypt(instance.getApiKeyEncrypted());
        return llmModelFactory.createChatModel(instance, apiKey);
    }
}
