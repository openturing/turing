/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * F.9 / §X.10.d — T170. Pure, SDK-free builder that turns operator thumb-up/down
 * feedback into an OpenAI <b>DPO</b> (Direct Preference Optimization) preference
 * dataset.
 *
 * <p>DPO needs a <em>pair</em> per example — a chosen and a rejected answer for
 * the same prompt — but a thumb is only a single label. So the builder groups
 * feedback by normalized prompt and emits one preference example for every
 * prompt that has <b>both</b> at least one thumb-up (chosen) and one thumb-down
 * (rejected) answer; the first UP and first DOWN (input order) are paired. A
 * prompt with only ups, only downs, or one rating contributes nothing — the
 * dataset is therefore built <em>incrementally</em> as contradictory feedback
 * accumulates.
 *
 * <p>Output is JSONL in OpenAI's preference format, one object per line:
 * <pre>
 * {"input":{"messages":[{"role":"user","content":"…"}]},
 *  "preferred_output":[{"role":"assistant","content":"…(thumb-up)…"}],
 *  "non_preferred_output":[{"role":"assistant","content":"…(thumb-down)…"}]}
 * </pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurDpoDatasetBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TurDpoDatasetBuilder() {
    }

    /** One rated assistant turn: the prompt, the answer, and whether the operator approved it. */
    public record FeedbackEntry(String prompt, String answer, boolean up) {
    }

    /** One DPO preference example built from a chosen (up) vs rejected (down) answer. */
    public record PreferencePair(String prompt, String chosen, String rejected) {
    }

    /**
     * Pair contradictory feedback per prompt into DPO preference examples. For
     * each normalized prompt with at least one up and one down answer, the first
     * up (chosen) and first down (rejected) are paired. Order follows the input
     * (callers pass feedback oldest-first), so the result is deterministic.
     */
    public static List<PreferencePair> pair(List<FeedbackEntry> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return List.of();
        }
        // Preserve first-seen prompt order for deterministic output.
        Map<String, String> firstUp = new LinkedHashMap<>();
        Map<String, String> firstDown = new LinkedHashMap<>();
        List<String> promptOrder = new ArrayList<>();
        for (FeedbackEntry entry : feedback) {
            if (entry == null || isBlank(entry.prompt()) || isBlank(entry.answer())) {
                continue;
            }
            String key = entry.prompt().trim();
            if (!firstUp.containsKey(key) && !firstDown.containsKey(key)) {
                promptOrder.add(key);
            }
            Map<String, String> target = entry.up() ? firstUp : firstDown;
            target.putIfAbsent(key, entry.answer());
        }
        List<PreferencePair> pairs = new ArrayList<>();
        for (String key : promptOrder) {
            String chosen = firstUp.get(key);
            String rejected = firstDown.get(key);
            if (chosen != null && rejected != null && !chosen.equals(rejected)) {
                pairs.add(new PreferencePair(key, chosen, rejected));
            }
        }
        return pairs;
    }

    /** Serialize the preference pairs to OpenAI DPO JSONL (one object per line). */
    public static String build(List<FeedbackEntry> feedback) {
        List<PreferencePair> pairs = pair(feedback);
        if (pairs.isEmpty()) {
            return "";
        }
        StringBuilder jsonl = new StringBuilder();
        for (PreferencePair p : pairs) {
            Map<String, Object> input = Map.of("messages",
                    List.of(message("user", p.prompt())));
            Map<String, Object> example = new LinkedHashMap<>();
            example.put("input", input);
            example.put("preferred_output", List.of(message("assistant", p.chosen())));
            example.put("non_preferred_output", List.of(message("assistant", p.rejected())));
            jsonl.append(OBJECT_MAPPER.writeValueAsString(example)).append('\n');
        }
        return jsonl.toString();
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
