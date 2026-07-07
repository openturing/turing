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

import java.util.List;

/**
 * Block AL / §XXXV.3 (T616) — the message-level sibling of
 * {@link TurPromptContributor}. Where a {@code TurPromptContributor} emits
 * fragments of the single <em>system</em> message, a {@code TurMessageContributor}
 * emits whole {@link TurPromptMessage history-prefix turns} that sit between the
 * system message and the client-supplied recent window.
 *
 * <p>This is the SPI that brings the two sources historically assembled inline in
 * {@code TurChatPromptAssembler} — the T115 chat-memory compression summary and the
 * T30 relevance-retrieved older turns — under one composable assembly path.
 * {@link TurMessageAssemblyPipeline} runs the ordered contributor list once; the
 * assembler prepends the emitted messages to the history and fans the whole list
 * out through its single {@code user}/{@code assistant} conversion, so what the
 * model receives has exactly one assembly point.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurMessageContributor {

    /** T115 chat-memory compression summary — the low-signal background, first. */
    int ORDER_MEMORY_SUMMARY = 100;
    /** T30 relevance-retrieved older turns — high-signal verbatim, after the summary. */
    int ORDER_RELEVANCE = 200;

    /**
     * Contribute this source's history-prefix messages for the turn, in the order
     * they should appear, or an empty list when the source does not apply (disabled
     * flag, blank conversation, no hits). Must never return {@code null}.
     * Implementations may read persisted memory and record telemetry, but must not
     * mutate the context.
     */
    List<TurPromptMessage> contribute(TurMessageAssemblyContext context);

    /**
     * Ascending assembly order. Lower runs earlier (closer to the top of the
     * history, just after the system message). Defaults to
     * {@link #ORDER_MEMORY_SUMMARY}.
     */
    default int order() {
        return ORDER_MEMORY_SUMMARY;
    }
}
