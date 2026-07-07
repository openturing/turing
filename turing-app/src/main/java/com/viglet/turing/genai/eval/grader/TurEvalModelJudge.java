/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import java.util.List;

import com.viglet.turing.genai.eval.TurEvalJudgePrompt;

/**
 * T590 / §XXXIII.5 — builds the strategy-flavored system + user prompts for the
 * MODEL grader and parses the verdict. Every strategy emits the <em>same</em>
 * compact JSON verdict shape as {@link TurEvalJudgePrompt} (the robust bilingual
 * LlmJudge path — no free-text-JSON structured-output guardrail), so the proven
 * {@link TurEvalJudgePrompt#parse parser} is reused verbatim. Pure → unit-testable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurEvalModelJudge {

    private static final String SHAPE = """
            Respond with ONLY a compact JSON object, no markdown fences, exactly:
            {"verdict":"pass"|"fail","score":0.0-1.0,"rationale":"one short sentence"}""";

    private TurEvalModelJudge() {
    }

    /** The strategy-specific auditor system prompt. */
    public static String system(TurEvalModelStrategy strategy) {
        return switch (strategy) {
            case LABEL -> "You are a strict, impartial evaluator of an AI agent's answer. "
                    + "Given an INSTRUCTION and the conversation, decide pass or fail. Be "
                    + "conservative; when in doubt, fail. Reply in the same language as the "
                    + "instruction.\n" + SHAPE;
            case SCORE -> "You are a strict, impartial evaluator of an AI agent's answer. "
                    + "Given an INSTRUCTION and the conversation, assign a quality SCORE from "
                    + "0.0 to 1.0. Reply in the same language as the instruction.\n" + SHAPE;
            case CRITERIA -> "You are a strict, impartial evaluator. Given a CHECKLIST of "
                    + "criteria and the conversation, verify EVERY criterion. Pass only if all "
                    + "are met; the score is the fraction met. Reply in the same language as the "
                    + "criteria.\n" + SHAPE;
            case PAIRWISE -> "You are a strict, impartial evaluator. Given a REFERENCE answer and "
                    + "the AGENT answer for the same task, decide whether the agent answer is at "
                    + "least as good as the reference (pass) or worse (fail). Reply in the same "
                    + "language as the reference.\n" + SHAPE;
        };
    }

    /** The strategy-specific user message: instruction/criteria/reference + transcript + answer. */
    public static String user(TurEvalModelStrategy strategy, String instruction,
            List<String> criteria, String reference, List<String> userTurns, String answer) {
        String head = switch (strategy) {
            case CRITERIA -> criteriaHead(criteria, instruction);
            case PAIRWISE -> pairwiseHead(reference, instruction);
            default -> defaultHead(instruction, reference);
        };
        return head
                + "\nUSER TURNS:\n" + (userTurns == null ? "" : String.join("\n", userTurns))
                + "\n\nAGENT ANSWER:\n" + (answer == null ? "" : answer);
    }

    private static String criteriaHead(List<String> criteria, String instruction) {
        StringBuilder sb = new StringBuilder("CHECKLIST:\n");
        if (criteria != null) {
            criteria.forEach(c -> sb.append("- ").append(c).append('\n'));
        }
        appendBlock(sb, "CONTEXT", instruction);
        return sb.toString();
    }

    private static String pairwiseHead(String reference, String instruction) {
        StringBuilder sb = new StringBuilder("REFERENCE:\n")
                .append(reference == null ? "" : reference).append('\n');
        appendBlock(sb, "TASK", instruction);
        return sb.toString();
    }

    private static String defaultHead(String instruction, String reference) {
        StringBuilder sb = new StringBuilder("INSTRUCTION:\n")
                .append(instruction == null ? "" : instruction).append('\n');
        // T591 — reference-answer faithfulness/correctness (LABEL / SCORE vs a reference).
        appendBlock(sb, "REFERENCE ANSWER", reference);
        return sb.toString();
    }

    private static void appendBlock(StringBuilder sb, String label, String text) {
        if (text != null && !text.isBlank()) {
            sb.append('\n').append(label).append(":\n").append(text).append('\n');
        }
    }

    /** Parse a judge reply into a verdict (reuses the robust bilingual parser). */
    public static TurEvalJudgePrompt.Verdict parse(String reply) {
        return TurEvalJudgePrompt.parse(reply);
    }
}
