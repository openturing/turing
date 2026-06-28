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

import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.clienttool.TurClientToolParkException;

import lombok.extern.slf4j.Slf4j;

/**
 * Drives the agentic tool-calling loop around a raw {@link ChatModel}.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Up to Spring AI 2.0.0-M6, {@code ChatModel.call(prompt)} ran the
 * tool-execution loop internally when the options carried
 * {@code internalToolExecutionEnabled(true)}: the model returned a tool
 * call, the model itself executed the registered {@link org.springframework.ai.tool.ToolCallback},
 * fed the result back, and re-called until a plain-text answer came out.
 *
 * <p><b>Spring AI 2.0.0-RC1 moved that loop out of {@code ChatModel.call()}
 * and into {@code ChatClient}.</b> Verified against the RC1 artifact:
 * {@code OpenAiChatModel.internalCall(...)} only calls
 * {@code ToolCallingManager.resolveToolDefinitions(...)} (to advertise the
 * tools to the provider) and never {@code executeToolCalls(...)}. So a bare
 * {@code chatModel.call(prompt)} with tool callbacks now returns the raw
 * tool-call response — the tool is never run and the assistant text is empty
 * (observed in production as "response: 0 chars" with non-zero output tokens).
 *
 * <p>The RC1 migration ({@code 36dd6f8150}) removed the
 * {@code internalToolExecutionEnabled(true)} flag on the assumption that
 * execution had become automatic in {@code call()}; it had not. Rather than
 * rewrite every direct-{@code call()} site onto {@code ChatClient} (which
 * would change the resilience wrapping in {@link com.viglet.turing.resilience.llm.TurResilientChatModel}),
 * this collaborator reinstates the loop manually with a {@link ToolCallingManager},
 * keeping the existing {@code ChatModel} + resilience pipeline intact.
 *
 * <p>Callers that registered <em>no</em> tool callbacks are unaffected: the
 * first response has no tool calls, the loop body never runs, and the single
 * response is returned verbatim — identical to a plain {@code call()}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurToolExecutionLoop {

    /**
     * Hard ceiling on tool-execution rounds for a single user turn. A
     * misbehaving model (or a tool whose result keeps provoking another
     * tool call) must not spin forever holding the request thread. Ten
     * rounds is well above any legitimate multi-tool turn observed.
     */
    static final int MAX_TOOL_ITERATIONS = 10;

    private final ToolCallingManager toolCallingManager;

    public TurToolExecutionLoop() {
        // The default manager resolves the callbacks to execute from the
        // prompt's own ToolCallingChatOptions.getToolCallbacks() — exactly the
        // list every call site already registers — so no external
        // ToolCallbackResolver wiring is needed.
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    /**
     * Calls {@code chatModel} with {@code prompt} and, whenever the model
     * answers with tool calls, executes the registered tools and re-calls the
     * model with the tool results appended — until the model returns a
     * plain-text answer (or the iteration cap trips).
     *
     * @param chatModel the (resilience-wrapped) model to drive
     * @param prompt    the initial prompt; its options must carry the tool
     *                  callbacks the model may invoke
     * @return the final, tool-free {@link ChatResponse} (the one whose
     *         assistant text the caller should surface)
     */
    public ChatResponse call(ChatModel chatModel, Prompt prompt) {
        ChatResponse response = chatModel.call(prompt);
        int iterations = 0;
        while (response != null && response.hasToolCalls()) {
            if (iterations++ >= MAX_TOOL_ITERATIONS) {
                log.warn("[ToolLoop] aborting after {} tool-execution rounds — the model kept "
                        + "requesting tools without producing a final answer", MAX_TOOL_ITERATIONS);
                break;
            }
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            prompt = new Prompt(result.conversationHistory(), prompt.getOptions());
            response = chatModel.call(prompt);
        }
        return response;
    }

    /**
     * T438 — same as {@link #call(ChatModel, Prompt)} but client-tool aware: when
     * the model requests a tool whose name is in {@code clientToolNames} (a
     * frontend tool the server can't run), the loop stops <em>before</em>
     * executing tools and throws {@link TurClientToolParkException} carrying the
     * messages fed to that call plus the assistant message holding the tool call.
     * The dispatcher catches it, parks the turn, and emits a
     * {@code client_tool_call} SSE event. Server-side tool calls in earlier
     * rounds run normally; only a client-tool request parks.
     *
     * <p>When {@code clientToolNames} is empty this is identical to
     * {@link #call(ChatModel, Prompt)}.
     *
     * @throws TurClientToolParkException when a client tool is requested
     */
    public ChatResponse callWithClientTools(ChatModel chatModel, Prompt prompt,
            Set<String> clientToolNames) {
        if (clientToolNames == null || clientToolNames.isEmpty()) {
            return call(chatModel, prompt);
        }
        ChatResponse response = chatModel.call(prompt);
        int iterations = 0;
        while (response != null && response.hasToolCalls()) {
            AssistantMessage assistant = response.getResult().getOutput();
            AssistantMessage.ToolCall clientCall = assistant.getToolCalls().stream()
                    .filter(tc -> clientToolNames.contains(tc.name()))
                    .findFirst()
                    .orElse(null);
            if (clientCall != null) {
                // Park: hand the dispatcher the messages that produced this call
                // and the assistant message, so a resume can append the browser's
                // tool response and continue from exactly here.
                throw new TurClientToolParkException(
                        clientCall, prompt.getInstructions(), assistant);
            }
            if (iterations++ >= MAX_TOOL_ITERATIONS) {
                log.warn("[ToolLoop] aborting after {} tool-execution rounds — the model kept "
                        + "requesting tools without producing a final answer", MAX_TOOL_ITERATIONS);
                break;
            }
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            prompt = new Prompt(result.conversationHistory(), prompt.getOptions());
            response = chatModel.call(prompt);
        }
        return response;
    }
}
