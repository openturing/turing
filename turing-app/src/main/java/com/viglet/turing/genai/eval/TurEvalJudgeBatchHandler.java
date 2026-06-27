/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.batch.TurBatchChatResult;
import com.viglet.turing.genai.batch.TurBatchCompletionHandler;
import com.viglet.turing.persistence.model.batch.TurBatchJob;

import lombok.extern.slf4j.Slf4j;

/**
 * F.7 / §X.8.d — consumes a completed LLM-Judge eval batch (T159).
 *
 * <p>Each batch result is a judge reply for one eval case (correlated by the
 * case id used as {@code customId}); this parses it with the shared
 * {@link TurEvalJudgePrompt} and logs the pass/fail tally plus each failing
 * case's rationale, so an overnight CI judging pass leaves an auditable verdict
 * trail. The aggregate is also visible on the batch job
 * ({@code GET /api/system/batch/jobs}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurEvalJudgeBatchHandler implements TurBatchCompletionHandler {

    public static final String PURPOSE = "llm-judge-eval";

    @Override
    public String purpose() {
        return PURPOSE;
    }

    @Override
    public void onBatchComplete(TurBatchJob job, List<TurBatchChatResult> results) {
        int pass = 0;
        int fail = 0;
        for (TurBatchChatResult result : results) {
            if (!result.success()) {
                fail++;
                log.warn("[Batch][Eval] case {} — judge request failed: {}",
                        result.customId(), result.error());
                continue;
            }
            TurEvalJudgePrompt.Verdict verdict = TurEvalJudgePrompt.parse(result.content());
            if (verdict.pass()) {
                pass++;
            } else {
                fail++;
                log.info("[Batch][Eval] case {} FAILED rubric: {}",
                        result.customId(), verdict.rationale());
            }
        }
        log.info("[Batch][Eval] job {} (agent {}) judged {} case(s): {} pass, {} fail",
                job.getId(), job.getContextJson(), results.size(), pass, fail);
    }
}
