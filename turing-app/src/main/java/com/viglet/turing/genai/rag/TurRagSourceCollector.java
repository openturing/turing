/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * T292 / §XVII.1 — per-turn sink for {@link TurRagSource} provenance.
 *
 * <p>The {@code search_knowledge_base} tool cannot return structured data over
 * the SSE stream directly: its result is a {@code String} consumed by the LLM
 * inside the tool-execution loop. To surface the provenance behind the answer,
 * the chat executor publishes a collector instance into the Spring AI
 * {@code ToolContext} under
 * {@link com.viglet.turing.genai.tool.TurCustomToolCallbackService#TOOL_CONTEXT_RAG_SOURCES};
 * the tool appends the retrieved chunks' provenance to it, and the streaming
 * dispatcher drains it after the loop completes to emit the {@code sources[]}
 * SSE event.
 *
 * <p>The collector survives the {@code TurToolExecutionLoop} rebuilding the
 * prompt each round because the loop preserves the same {@code ChatOptions}
 * (and therefore the same tool-context map and collector reference) across
 * iterations.
 *
 * <p>Backed by a {@link CopyOnWriteArrayList} so a tool running on a worker
 * thread and the dispatcher reading the snapshot never race.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class TurRagSourceCollector {

    private final List<TurRagSource> sources = new CopyOnWriteArrayList<>();

    /** Appends one chunk's provenance. */
    public void add(TurRagSource source) {
        if (source != null) {
            sources.add(source);
        }
    }

    /** Appends a batch of chunk provenance (skips {@code null} entries). */
    public void addAll(Collection<TurRagSource> batch) {
        if (batch != null) {
            for (TurRagSource s : batch) {
                add(s);
            }
        }
    }

    /** Immutable snapshot of everything collected so far. */
    public List<TurRagSource> snapshot() {
        return List.copyOf(sources);
    }

    public boolean isEmpty() {
        return sources.isEmpty();
    }
}
