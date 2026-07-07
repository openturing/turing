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
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * F.7 / §X.8.d — the bilingual LLM-Judge prompt + verdict parser, factored out
 * of {@link TurAgentEvalRunnerService} so the synchronous judge and the Batch
 * judge (T159) build the <em>identical</em> system/user prompt and parse the
 * verdict the same way. Pure (no Spring, no model) → unit-testable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurEvalJudgePrompt {

    /** The strict, language-agnostic auditor system prompt. */
    public static final String SYSTEM = """
            You are a strict, impartial conversation auditor for an AI agent.
            Given a RUBRIC and the user's scripted turns of a conversation, decide
            whether the agent's behaviour satisfies the rubric. Be conservative:
            when in doubt, fail. Reply in the SAME language as the rubric.
            Respond with ONLY a compact JSON object, no markdown fences, exactly:
            {"verdict":"pass"|"fail","score":0.0-1.0,"rationale":"one short sentence"}
            """;

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private TurEvalJudgePrompt() {
    }

    /** One judge verdict: pass/fail, a 0..1 score, and a one-line rationale. */
    public record Verdict(boolean pass, double score, String rationale) {
    }

    /** Build the user message from a rubric and the scripted user turns. */
    public static String buildUser(String rubric, List<String> turns) {
        String transcript = turns == null ? "" : String.join("\n", turns);
        return "RUBRIC:\n" + rubric + "\n\nUSER TURNS:\n" + transcript;
    }

    /** Parse a judge reply into a {@link Verdict}; never throws (fails closed). */
    public static Verdict parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return new Verdict(false, 0d, "empty judge reply");
        }
        String json = extractJson(reply);
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            String verdict = String.valueOf(parsed.getOrDefault("verdict", "fail")).trim().toLowerCase();
            boolean pass = "pass".equals(verdict);
            double defaultScore = pass ? 1d : 0d;
            double score = parsed.get("score") instanceof Number n ? n.doubleValue() : defaultScore;
            score = Math.clamp(score, 0d, 1d);
            Object rationale = parsed.get("rationale");
            return new Verdict(pass, score, rationale == null ? null : String.valueOf(rationale));
        } catch (RuntimeException e) {
            return new Verdict(false, 0d, "unparseable judge reply");
        }
    }

    private static String extractJson(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return reply.substring(start, end + 1);
        }
        return reply.trim();
    }
}
