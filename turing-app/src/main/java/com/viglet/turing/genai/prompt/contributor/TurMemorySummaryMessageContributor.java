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

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.prompt.TurMessageAssemblyContext;
import com.viglet.turing.genai.prompt.TurMessageContributor;
import com.viglet.turing.genai.prompt.TurPromptMessage;
import com.viglet.turing.service.chatmemory.TurChatMemoryCompressionService;

/**
 * Block AL / §XXXV.3 (T616) — contributes the T115 chat-memory compression summary
 * as the first history-prefix message. Thin wrapper over
 * {@link TurChatMemoryCompressionService#summaryBlock}: all the eligibility,
 * threshold, interval-cache and (a)sync-regeneration logic stays in the service; the
 * contributor only lifts its {@code Optional<ChatMessageItem>} into the message
 * pipeline. Emits at most one message, empty when the service opts out — so a
 * default agent gets nothing (byte-identical to the pre-pipeline path).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurMemorySummaryMessageContributor implements TurMessageContributor {

    private final TurChatMemoryCompressionService compressionService;

    public TurMemorySummaryMessageContributor(TurChatMemoryCompressionService compressionService) {
        this.compressionService = compressionService;
    }

    @Override
    public int order() {
        return ORDER_MEMORY_SUMMARY;
    }

    @Override
    public List<TurPromptMessage> contribute(TurMessageAssemblyContext context) {
        Optional<ChatMessageItem> summary =
                compressionService.summaryBlock(context.agent(), context.conversationId());
        if (summary.isEmpty()) {
            return List.of();
        }
        ChatMessageItem item = summary.get();
        return List.of(TurPromptMessage.of(item.role(), item.content(),
                TurPromptMessage.ORIGIN_MEMORY_SUMMARY, "Memory summary"));
    }
}
