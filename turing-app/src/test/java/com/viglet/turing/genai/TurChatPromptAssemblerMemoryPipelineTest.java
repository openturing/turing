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
package com.viglet.turing.genai;

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
import org.springframework.ai.chat.messages.Message;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.prompt.TurMessageAssemblyPipeline;
import com.viglet.turing.genai.prompt.TurPromptAssembly;
import com.viglet.turing.genai.prompt.TurPromptAssemblyPipeline;
import com.viglet.turing.genai.prompt.contributor.TurMemorySummaryMessageContributor;
import com.viglet.turing.genai.prompt.contributor.TurRelevanceMessageContributor;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.service.chatmemory.TurChatMemoryCompressionService;
import com.viglet.turing.service.chatmemory.TurChatMemoryRelevanceRetriever;

/**
 * Block AL / §XXXV.3 (T616) — proves the message contributor pipeline (default)
 * produces a <b>byte-identical</b> message list to the legacy inline concatenation
 * escape hatch, and that both layer {@code [system] + [summary] + [retrieved] +
 * [history]} in the correct order.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurChatPromptAssemblerMemoryPipelineTest {

    @Mock
    private TurChatMemoryRelevanceRetriever relevanceRetriever;
    @Mock
    private TurChatMemoryCompressionService compressionService;
    @Mock
    private TurChatPipelineObservation observation;
    @Mock
    private TurChatAttachmentService attachmentService;
    @Mock
    private TurPromptAssemblyPipeline promptAssemblyPipeline;
    @Mock
    private TurAIAgent agent;

    private TurChatPromptAssembler assembler;

    private static final String SYSTEM = "SYSTEM PROMPT";
    private static final String CONV = "conv-42";
    private static final String QUERY = "what changed?";

    @BeforeEach
    void setUp() {
        TurMessageAssemblyPipeline messagePipeline = new TurMessageAssemblyPipeline(List.of(
                new TurMemorySummaryMessageContributor(compressionService),
                new TurRelevanceMessageContributor(relevanceRetriever)));
        assembler = new TurChatPromptAssembler(relevanceRetriever, compressionService,
                observation, attachmentService, promptAssemblyPipeline, messagePipeline);

        lenient().when(promptAssemblyPipeline.assemble(any()))
                .thenReturn(new TurPromptAssembly(List.of(), SYSTEM, -1));
    }

    private TurChatPromptRequest request() {
        return new TurChatPromptRequest(agent,
                List.of(new ChatMessageItem("user", QUERY)),
                CONV, null, null, QUERY, "BASE");
    }

    /** Renders a message list to comparable "Class:text" tokens. */
    private static List<String> shape(List<Message> messages) {
        return messages.stream()
                .map(m -> m.getClass().getSimpleName() + ":" + m.getText())
                .toList();
    }

    @Test
    void pipelineLayersSummaryThenRetrievedThenHistory() {
        when(compressionService.summaryBlock(agent, CONV))
                .thenReturn(Optional.of(new ChatMessageItem("user", "[Summary] older ctx")));
        when(relevanceRetriever.retrievePrefix(agent, CONV, QUERY))
                .thenReturn(List.of(
                        new ChatMessageItem("user", "[earlier turn #1] I fly monthly"),
                        new ChatMessageItem("assistant", "[earlier turn #2] got it")));

        ReflectionTestUtils.setField(assembler, "messagePipelineEnabled", true);
        List<Message> messages = assembler.assemble(request());

        assertThat(shape(messages)).containsExactly(
                "SystemMessage:" + SYSTEM,
                "UserMessage:[Summary] older ctx",
                "UserMessage:[earlier turn #1] I fly monthly",
                "AssistantMessage:[earlier turn #2] got it",
                "UserMessage:" + QUERY);
    }

    @Test
    void pipelineAndLegacyProduceIdenticalMessageList() {
        when(compressionService.summaryBlock(agent, CONV))
                .thenReturn(Optional.of(new ChatMessageItem("user", "[Summary] older ctx")));
        when(relevanceRetriever.retrievePrefix(agent, CONV, QUERY))
                .thenReturn(List.of(
                        new ChatMessageItem("user", "[earlier turn #1] I fly monthly"),
                        new ChatMessageItem("assistant", "[earlier turn #2] got it")));

        ReflectionTestUtils.setField(assembler, "messagePipelineEnabled", true);
        List<String> viaPipeline = shape(assembler.assemble(request()));

        ReflectionTestUtils.setField(assembler, "messagePipelineEnabled", false);
        List<String> viaLegacy = shape(assembler.assemble(request()));

        assertThat(viaPipeline).isEqualTo(viaLegacy);
    }

    @Test
    void noMemory_bothPathsAreSystemPlusHistory() {
        when(compressionService.summaryBlock(agent, CONV)).thenReturn(Optional.empty());
        when(relevanceRetriever.retrievePrefix(agent, CONV, QUERY)).thenReturn(List.of());

        ReflectionTestUtils.setField(assembler, "messagePipelineEnabled", true);
        List<String> viaPipeline = shape(assembler.assemble(request()));
        ReflectionTestUtils.setField(assembler, "messagePipelineEnabled", false);
        List<String> viaLegacy = shape(assembler.assemble(request()));

        assertThat(viaPipeline)
                .containsExactly("SystemMessage:" + SYSTEM, "UserMessage:" + QUERY)
                .isEqualTo(viaLegacy);
    }

    @Test
    void assistantSummaryRole_becomesAssistantMessage() {
        // Guard the role fan-out: a summary emitted with role "assistant" must map
        // to AssistantMessage, identically on both paths.
        when(compressionService.summaryBlock(agent, CONV))
                .thenReturn(Optional.of(new ChatMessageItem("assistant", "[Summary] as assistant")));
        when(relevanceRetriever.retrievePrefix(agent, CONV, QUERY)).thenReturn(List.of());

        ReflectionTestUtils.setField(assembler, "messagePipelineEnabled", true);
        List<String> viaPipeline = shape(assembler.assemble(request()));
        ReflectionTestUtils.setField(assembler, "messagePipelineEnabled", false);
        List<String> viaLegacy = shape(assembler.assemble(request()));

        assertThat(viaPipeline).containsExactly(
                "SystemMessage:" + SYSTEM,
                "AssistantMessage:[Summary] as assistant",
                "UserMessage:" + QUERY);
        assertThat(viaPipeline).isEqualTo(viaLegacy);
    }
}
