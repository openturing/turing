/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.viglet.turing.genai.clienttool.TurClientToolParkException;

import reactor.core.publisher.Flux;

/**
 * Regression guard for the Spring AI 2.0.0-RC1 tool-execution change.
 *
 * <p>RC1 removed the in-{@code call()} tool-execution loop from
 * {@code ChatModel} (it moved to {@code ChatClient}). Without
 * {@link TurToolExecutionLoop} re-driving it, a {@code chatModel.call(prompt)}
 * with registered tools returns the raw <em>tool-call</em> response and the
 * tool never runs — surfacing in production as an empty assistant reply
 * ("response: 0 chars" with non-zero output tokens) when a user asked, e.g.,
 * for the weather.
 *
 * <p>These tests stand in a fake {@link ChatModel} that first answers with a
 * tool call and then (after the tool runs) with plain text, and assert the
 * loop (a) executes the registered {@link ToolCallback} and (b) re-calls the
 * model so the final text is surfaced. With the pre-fix code (a bare
 * {@code call()}), the tool callback would never be invoked and the returned
 * response would still carry the tool call — both assertions would fail.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurToolExecutionLoopTest {

    private static final String TOOL_NAME = "test_echo";

    /** A {@link ChatModel} that replays a fixed FIFO of canned responses. */
    private static final class QueueChatModel implements ChatModel {
        private final Deque<ChatResponse> queue = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();

        void enqueue(ChatResponse... responses) {
            for (ChatResponse r : responses) {
                queue.add(r);
            }
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            ChatResponse next = queue.poll();
            // Never NPE the loop if a test under-queues — emit empty text.
            return next != null ? next
                    : new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }

        int callCount() {
            return calls.get();
        }
    }

    /** A {@link ToolCallback} that records whether it was actually executed. */
    private static final class RecordingToolCallback implements ToolCallback {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(TOOL_NAME)
                    .description("Echoes back a fixed value (test stub).")
                    .inputSchema("{\"type\":\"object\",\"properties\":{"
                            + "\"value\":{\"type\":\"string\"}}}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            invocations.incrementAndGet();
            return "{\"echoed\":true}";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }

        int invocations() {
            return invocations.get();
        }
    }

    private static ChatResponse toolCallResponse() {
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("call_1", "function", TOOL_NAME, "{\"value\":\"hi\"}");
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static Prompt promptWith(ToolCallback callback) {
        ToolCallingChatOptions options = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(callback)
                .build();
        return new Prompt(List.of(new UserMessage("qual é a previsão do tempo?")), options);
    }

    @Test
    void executesTool_thenRecallsModel_andReturnsFinalText() {
        RecordingToolCallback tool = new RecordingToolCallback();
        QueueChatModel model = new QueueChatModel();
        // Turn 1: the model asks to call the tool. Turn 2 (post tool result):
        // the model produces the user-facing answer.
        model.enqueue(toolCallResponse(), textResponse("Hoje faz 25°C em São Paulo."));

        ChatResponse result = new TurToolExecutionLoop().call(model, promptWith(tool));

        assertThat(tool.invocations())
                .as("the registered tool MUST be executed by the loop")
                .isEqualTo(1);
        assertThat(model.callCount())
                .as("the model must be re-called after the tool result is appended")
                .isEqualTo(2);
        assertThat(result.hasToolCalls())
                .as("the final response must be a plain answer, not a dangling tool call")
                .isFalse();
        assertThat(result.getResult().getOutput().getText())
                .isEqualTo("Hoje faz 25°C em São Paulo.");
    }

    @Test
    void noToolCalls_returnsSingleResponseVerbatim() {
        RecordingToolCallback tool = new RecordingToolCallback();
        QueueChatModel model = new QueueChatModel();
        model.enqueue(textResponse("resposta direta"));

        ChatResponse result = new TurToolExecutionLoop().call(model, promptWith(tool));

        assertThat(tool.invocations())
                .as("no tool call in the response → tool must not run")
                .isZero();
        assertThat(model.callCount())
                .as("a tool-free answer needs exactly one model call")
                .isEqualTo(1);
        assertThat(result.getResult().getOutput().getText()).isEqualTo("resposta direta");
    }

    private static final String CLIENT_TOOL = "get_user_location";

    private static ChatResponse clientToolCallResponse() {
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("call_loc", "function", CLIENT_TOOL, "{\"hint\":\"city\"}");
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    @Test
    void callWithClientTools_parksOnClientToolCall() {
        RecordingToolCallback tool = new RecordingToolCallback();
        QueueChatModel model = new QueueChatModel();
        model.enqueue(clientToolCallResponse());

        TurClientToolParkException park = catchThrowableOfType(
                TurClientToolParkException.class,
                () -> new TurToolExecutionLoop().callWithClientTools(
                        model, promptWith(tool), java.util.Set.of(CLIENT_TOOL)));

        assertThat(park).isNotNull();
        assertThat(park.toolCall().name()).isEqualTo(CLIENT_TOOL);
        assertThat(park.toolCall().id()).isEqualTo("call_loc");
        assertThat(park.toolCall().arguments()).contains("city");
        assertThat(park.assistantMessage()).isNotNull();
        assertThat(park.promptMessages()).isNotEmpty();
        // A client tool must NOT be executed server-side.
        assertThat(tool.invocations()).isZero();
        // The model was called once (produced the client-tool request) and not re-called.
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void callWithClientTools_passesThroughServerToolsAndAnswers() {
        RecordingToolCallback tool = new RecordingToolCallback();
        QueueChatModel model = new QueueChatModel();
        // A server tool call (not a client tool) runs normally, then the model answers.
        model.enqueue(toolCallResponse(), textResponse("pronto"));

        ChatResponse result = new TurToolExecutionLoop().callWithClientTools(
                model, promptWith(tool), java.util.Set.of(CLIENT_TOOL));

        assertThat(tool.invocations()).isEqualTo(1);
        assertThat(result.getResult().getOutput().getText()).isEqualTo("pronto");
    }

    @Test
    void callWithClientTools_emptyNames_behavesLikePlainCall() {
        RecordingToolCallback tool = new RecordingToolCallback();
        QueueChatModel model = new QueueChatModel();
        model.enqueue(textResponse("direto"));

        ChatResponse result = new TurToolExecutionLoop().callWithClientTools(
                model, promptWith(tool), java.util.Set.of());

        assertThat(result.getResult().getOutput().getText()).isEqualTo("direto");
        assertThat(model.callCount()).isEqualTo(1);
    }

    @Test
    void stopsAtIterationCap_whenModelKeepsRequestingTools() {
        RecordingToolCallback tool = new RecordingToolCallback();
        QueueChatModel model = new QueueChatModel();
        // Simulate a model stuck in a tool-calling loop: queue more tool-call
        // responses than the cap so every poll() keeps returning a tool call.
        ChatResponse[] toolCalls = new ChatResponse[TurToolExecutionLoop.MAX_TOOL_ITERATIONS + 5];
        for (int i = 0; i < toolCalls.length; i++) {
            toolCalls[i] = toolCallResponse();
        }
        model.enqueue(toolCalls);

        ChatResponse result = new TurToolExecutionLoop().call(model, promptWith(tool));

        // Initial call + MAX_TOOL_ITERATIONS re-calls, then the loop bails out.
        assertThat(model.callCount())
                .as("the loop must stop re-calling after the iteration cap")
                .isEqualTo(TurToolExecutionLoop.MAX_TOOL_ITERATIONS + 1);
        assertThat(result).isNotNull();
    }
}
