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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.prompt.contributor.TurMemorySummaryMessageContributor;
import com.viglet.turing.genai.prompt.contributor.TurRelevanceMessageContributor;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.service.chatmemory.TurChatMemoryCompressionService;
import com.viglet.turing.service.chatmemory.TurChatMemoryRelevanceRetriever;

/**
 * Block AL / §XXXV.3 (T616) — proves the message contributor pipeline layers the
 * T115 memory summary ahead of the T30 relevance-retrieved turns (the exact
 * {@code [summary] + [retrieved]} order the legacy inline path produced), and
 * no-ops when both sources opt out.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurMessageAssemblyPipelineTest {

    @Mock
    private TurChatMemoryCompressionService compressionService;
    @Mock
    private TurChatMemoryRelevanceRetriever relevanceRetriever;
    @Mock
    private TurAIAgent agent;

    private TurMessageAssemblyPipeline pipeline;

    private static final String CONV = "conv-1";
    private static final String QUERY = "what about pricing?";

    @BeforeEach
    void setUp() {
        // Contributors intentionally registered relevance-first to prove the
        // pipeline sorts by order() (summary=100 must still lead relevance=200).
        pipeline = new TurMessageAssemblyPipeline(List.of(
                new TurRelevanceMessageContributor(relevanceRetriever),
                new TurMemorySummaryMessageContributor(compressionService)));
    }

    private TurMessageAssemblyContext ctx() {
        return new TurMessageAssemblyContext(agent, CONV, QUERY);
    }

    @Test
    void summaryLeadsRetrievedTurns_inOrder() {
        when(compressionService.summaryBlock(agent, CONV))
                .thenReturn(Optional.of(new ChatMessageItem("user", "[Summary 1–8] earlier context")));
        when(relevanceRetriever.retrievePrefix(agent, CONV, QUERY))
                .thenReturn(List.of(
                        new ChatMessageItem("user", "[earlier turn #2] I prefer window seats"),
                        new ChatMessageItem("assistant", "[earlier turn #3] noted")));

        List<TurPromptMessage> prefix = pipeline.assemble(ctx());

        assertThat(prefix).extracting(TurPromptMessage::origin).containsExactly(
                TurPromptMessage.ORIGIN_MEMORY_SUMMARY,
                TurPromptMessage.ORIGIN_MEMORY_RELEVANCE,
                TurPromptMessage.ORIGIN_MEMORY_RELEVANCE);
        assertThat(prefix).extracting(TurPromptMessage::role)
                .containsExactly("user", "user", "assistant");
        assertThat(prefix.get(0).content()).startsWith("[Summary 1–8]");
        assertThat(prefix.get(1).content()).startsWith("[earlier turn #2]");
        assertThat(prefix).allSatisfy(m -> assertThat(m.tokens()).isPositive());
    }

    @Test
    void retrievedOnly_whenNoSummary() {
        when(compressionService.summaryBlock(agent, CONV)).thenReturn(Optional.empty());
        when(relevanceRetriever.retrievePrefix(agent, CONV, QUERY))
                .thenReturn(List.of(new ChatMessageItem("user", "[earlier turn #4] budget is $2k")));

        List<TurPromptMessage> prefix = pipeline.assemble(ctx());

        assertThat(prefix).singleElement()
                .satisfies(m -> assertThat(m.origin()).isEqualTo(TurPromptMessage.ORIGIN_MEMORY_RELEVANCE));
    }

    @Test
    void empty_whenBothSourcesOptOut() {
        lenient().when(compressionService.summaryBlock(agent, CONV)).thenReturn(Optional.empty());
        lenient().when(relevanceRetriever.retrievePrefix(agent, CONV, QUERY)).thenReturn(List.of());

        assertThat(pipeline.assemble(ctx())).isEmpty();
    }
}
