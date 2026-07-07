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
package com.viglet.turing.genai.prompt.contributor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.prompt.TurMessageAssemblyContext;
import com.viglet.turing.genai.prompt.TurMessageContributor;
import com.viglet.turing.genai.prompt.TurPromptMessage;
import com.viglet.turing.service.chatmemory.TurChatMemoryRelevanceRetriever;

/**
 * Block AL / §XXXV.3 (T616) — contributes the T30 BM25 relevance-retrieved older
 * turns as history-prefix messages, after the summary. Thin wrapper over
 * {@link TurChatMemoryRelevanceRetriever#retrievePrefix}: the retriever owns the
 * per-conversation index, bypass conditions and telemetry; the contributor only
 * maps each retrieved {@link ChatMessageItem} (already marked up with its
 * {@code [earlier turn #n]} prefix) into the message pipeline. Empty when the
 * retriever opts out or finds nothing on-topic — byte-identical to the pre-pipeline
 * path for a default agent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurRelevanceMessageContributor implements TurMessageContributor {

    private final TurChatMemoryRelevanceRetriever relevanceRetriever;

    public TurRelevanceMessageContributor(TurChatMemoryRelevanceRetriever relevanceRetriever) {
        this.relevanceRetriever = relevanceRetriever;
    }

    @Override
    public int order() {
        return ORDER_RELEVANCE;
    }

    @Override
    public List<TurPromptMessage> contribute(TurMessageAssemblyContext context) {
        List<ChatMessageItem> retrieved = relevanceRetriever.retrievePrefix(
                context.agent(), context.conversationId(), context.latestUserMessage());
        if (retrieved.isEmpty()) {
            return List.of();
        }
        List<TurPromptMessage> messages = new ArrayList<>(retrieved.size());
        for (ChatMessageItem item : retrieved) {
            messages.add(TurPromptMessage.of(item.role(), item.content(),
                    TurPromptMessage.ORIGIN_MEMORY_RELEVANCE, "Relevant earlier turn"));
        }
        return messages;
    }
}
