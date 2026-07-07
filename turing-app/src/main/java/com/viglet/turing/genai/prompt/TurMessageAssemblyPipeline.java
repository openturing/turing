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
package com.viglet.turing.genai.prompt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Block AL / §XXXV.3 (T616) — the message-level counterpart of
 * {@link TurPromptAssemblyPipeline}. Runs the ordered {@link TurMessageContributor}
 * list <b>once</b> and returns the concatenated {@link TurPromptMessage}s that the
 * assembler prepends to the client history (ahead of the recent window). With the
 * built-in contributors (memory-summary, relevance) this reproduces the historical
 * inline {@code [summary] + [retrieved] + [history]} layering exactly — the
 * migration keeps the assembled message list byte-identical while collapsing the
 * two inline sources onto one composable path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurMessageAssemblyPipeline {

    private final List<TurMessageContributor> contributors;

    public TurMessageAssemblyPipeline(List<TurMessageContributor> contributors) {
        // Deterministic assembly order — lower order() runs earlier (closer to the
        // top of the history). Sorted once at construction.
        this.contributors = contributors.stream()
                .sorted(Comparator.comparingInt(TurMessageContributor::order))
                .toList();
    }

    /**
     * Assemble the ordered history-prefix messages for the turn. Never returns
     * {@code null}; empty when no contributor applies (default agent, blank
     * conversation, no memory hits).
     */
    public List<TurPromptMessage> assemble(TurMessageAssemblyContext context) {
        List<TurPromptMessage> out = new ArrayList<>();
        for (TurMessageContributor contributor : contributors) {
            List<TurPromptMessage> emitted = contributor.contribute(context);
            if (emitted != null) {
                out.addAll(emitted);
            }
        }
        return out;
    }
}
