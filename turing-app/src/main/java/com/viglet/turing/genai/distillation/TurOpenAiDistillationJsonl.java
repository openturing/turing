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
 * F.9 / §X.10.c — T169. Pure, SDK-free builder for the OpenAI fine-tuning
 * training file: one JSON object per line, each {@code {"messages":[...]}} in
 * the chat fine-tuning schema. Built from neutral {@link TrainingExample}s so
 * the line shape is unit-testable without the OpenAI SDK (the SDK extraction of
 * Stored Completions lives in {@code TurOpenAiDistillationService}). Mirrors the
 * pure-builder pattern of {@code TurOpenAiBatchJsonl} (T156).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurOpenAiDistillationJsonl {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TurOpenAiDistillationJsonl() {
    }

    /** One chat message in a training example. */
    public record TrainingMessage(String role, String content) {
    }

    /** One fine-tuning training example: an ordered conversation ending in the assistant turn. */
    public record TrainingExample(List<TrainingMessage> messages) {
    }

    /**
     * A training example is usable only when it has at least one non-assistant
     * input message <b>and</b> a trailing assistant message — both with content.
     * Stored completions that don't reconstruct into that shape are skipped.
     */
    public static boolean isUsable(TrainingExample example) {
        if (example == null || example.messages() == null || example.messages().size() < 2) {
            return false;
        }
        List<TrainingMessage> messages = example.messages();
        TrainingMessage last = messages.get(messages.size() - 1);
        if (last == null || !"assistant".equals(last.role()) || isBlank(last.content())) {
            return false;
        }
        return messages.subList(0, messages.size() - 1).stream()
                .anyMatch(m -> m != null && !"assistant".equals(m.role()) && !isBlank(m.content()));
    }

    /**
     * Serialize the usable examples to JSONL (one object per line). Unusable
     * examples are filtered out; the returned string has a trailing newline when
     * non-empty. Returns an empty string when no example is usable.
     */
    public static String build(List<TrainingExample> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder jsonl = new StringBuilder();
        for (TrainingExample example : examples) {
            if (!isUsable(example)) {
                continue;
            }
            List<Map<String, Object>> messages = new ArrayList<>();
            for (TrainingMessage message : example.messages()) {
                if (message == null || isBlank(message.content())) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("role", message.role());
                entry.put("content", message.content());
                messages.add(entry);
            }
            jsonl.append(OBJECT_MAPPER.writeValueAsString(Map.of("messages", messages))).append('\n');
        }
        return jsonl.toString();
    }

    /** Count of usable examples in the list (what the fine-tune floor is checked against). */
    public static long usableCount(List<TrainingExample> examples) {
        if (examples == null) {
            return 0;
        }
        return examples.stream().filter(TurOpenAiDistillationJsonl::isUsable).count();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
