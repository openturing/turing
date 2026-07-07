/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.clienttool;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

/**
 * T438 — signal thrown out of the tool-execution loop when the model requests a
 * frontend ("client") tool. It is <em>not</em> an error: the dispatcher catches
 * it, parks the turn ({@link com.viglet.turing.genai.TurClientToolParkRegistry TurClientToolParkRegistry}), and emits a
 * {@code client_tool_call} SSE event so the browser runs the tool and POSTs the
 * result back to resume the loop.
 *
 * <p>Carries exactly what a resume needs: the messages that were fed to the call
 * that produced the tool request, plus the assistant message holding the tool
 * call. On resume the dispatcher appends a {@code ToolResponseMessage} for
 * {@link #toolCall()} and re-dispatches.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurClientToolParkException extends RuntimeException {

    private final transient AssistantMessage.ToolCall toolCall;
    private final transient List<Message> promptMessages;
    private final transient AssistantMessage assistantMessage;

    public TurClientToolParkException(AssistantMessage.ToolCall toolCall,
            List<Message> promptMessages, AssistantMessage assistantMessage) {
        super("client tool requested: " + (toolCall == null ? "?" : toolCall.name()));
        this.toolCall = toolCall;
        this.promptMessages = promptMessages;
        this.assistantMessage = assistantMessage;
    }

    public AssistantMessage.ToolCall toolCall() {
        return toolCall;
    }

    /** Messages fed to the model call that produced the client-tool request. */
    public List<Message> promptMessages() {
        return promptMessages;
    }

    /** The assistant message carrying the client-tool call (re-added on resume). */
    public AssistantMessage assistantMessage() {
        return assistantMessage;
    }
}
