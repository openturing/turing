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
package com.viglet.turing.genai.nativeapi.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport;
import com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport.ToolOutcome;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * F.2 / §X.3 — runs a chat turn through the OpenAI <b>Responses API</b>
 * ({@code /v1/responses}) with server-side built-in tools.
 *
 * <p>Each enabled {@link TurNativeCapability} maps to one Responses built-in
 * tool that executes inside OpenAI's infrastructure — Turing writes <em>zero</em>
 * Java tool callbacks for any of them:
 * <ul>
 *   <li>{@code OPENAI_WEB_SEARCH}        → {@code web_search}</li>
 *   <li>{@code OPENAI_FILE_SEARCH}       → {@code file_search} (hosted vector stores)</li>
 *   <li>{@code OPENAI_CODE_INTERPRETER}  → {@code code_interpreter} (auto container)</li>
 *   <li>{@code OPENAI_IMAGE_GENERATION}  → {@code image_generation}</li>
 *   <li>{@code OPENAI_MCP}               → remote {@code mcp} connector</li>
 * </ul>
 *
 * <p>{@code OPENAI_COMPUTER_USE} is intentionally <em>not</em> wired here: the
 * computer-use tool needs a multi-turn screenshot/action loop, not a one-shot
 * server-side call, so it is handled by
 * {@link com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService}
 * (T136). This service skips it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurOpenAiResponsesService {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    /**
     * T433 — hard ceiling on tool-execution rounds for a single turn, mirroring
     * {@link com.viglet.turing.genai.TurToolExecutionLoop#MAX_TOOL_ITERATIONS}.
     * A misbehaving model must not spin forever holding the request thread.
     */
    static final int MAX_TOOL_ITERATIONS = 10;

    private final TurProviderOptionsParser optionsParser;
    private final TurNativeFunctionToolSupport toolSupport;

    public TurOpenAiResponsesService(TurProviderOptionsParser optionsParser,
            TurNativeFunctionToolSupport toolSupport) {
        this.optionsParser = optionsParser;
        this.toolSupport = toolSupport;
    }

    /**
     * Execute the turn and emit the assistant text as a single SSE token event
     * (CALL semantics — the Responses tool turn must complete before the answer
     * is known).
     *
     * <p>T433 / §X.18.b — {@code coexistingTools} are the agent's Turing/MCP/
     * custom tools whose abstract {@code function} isn't claimed by a selected
     * provider-native capability. When present they are advertised as Responses
     * {@code function} tools alongside the server-side built-ins, and the turn
     * runs a client-side tool-execution loop: the model emits {@code function_call}
     * items, Turing executes the matching {@link ToolCallback} and feeds the
     * result back, until a plain-text answer comes out. When empty (the legacy
     * native path, or an agent with no coexisting tools) the loop runs exactly
     * once — identical to the original one-shot behaviour.
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools) {
        return Mono.fromCallable(() -> runLoop(client, instance, history, systemPrompt,
                        capabilities, coexistingTools))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(text -> Flux.just(new ChatResponse("assistant",
                        StringUtils.hasText(text) ? text : "")))
                .doOnError(err -> log.error("[Native][OpenAI-Responses] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    String runLoop(OpenAIClient client, TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities, ToolCallback[] coexistingTools) {
        long t0 = System.currentTimeMillis();
        List<Tool> serverTools = buildTools(capabilities);
        List<Tool> functionTools = buildFunctionTools(coexistingTools);
        Map<String, ToolCallback> toolIndex = toolSupport.indexByName(coexistingTools);
        List<ResponseInputItem> items = seedItems(history);
        StringBuilder text = new StringBuilder();
        int toolCalls = 0;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            ResponseCreateParams params =
                    buildParamsFromItems(instance, items, systemPrompt, serverTools, functionTools);
            Response response = client.responses().create(params);
            text.append(extractText(response));

            List<ResponseFunctionToolCall> calls = functionCalls(response);
            if (calls.isEmpty()) {
                log.info("[Native][OpenAI-Responses] instance '{}' model '{}' server-tools {} "
                                + "function-tools {} -> {} chars, {} tool call(s) in {} ms",
                        instance.getId(), resolveModel(instance),
                        capabilities.stream().map(c -> c.capability().getKey()).toList(),
                        functionTools.size(), text.length(), toolCalls,
                        System.currentTimeMillis() - t0);
                return text.toString();
            }
            for (ResponseFunctionToolCall call : calls) {
                items.add(ResponseInputItem.ofFunctionCall(call));
                ToolOutcome outcome = toolSupport.execute(toolIndex, call.name(), call.arguments());
                items.add(ResponseInputItem.ofFunctionCallOutput(
                        ResponseInputItem.FunctionCallOutput.builder()
                                .callId(call.callId())
                                .output(outcome.output())
                                .build()));
                toolCalls++;
            }
        }

        log.warn("[Native][OpenAI-Responses] instance '{}' hit the {}-round tool-execution cap",
                instance.getId(), MAX_TOOL_ITERATIONS);
        return text.toString();
    }

    /** Legacy/test overload — no coexisting client tools (one-shot server-tool turn). */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities) {
        return chat(client, instance, history, systemPrompt, capabilities, new ToolCallback[0]);
    }

    ResponseCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities) {
        return buildParamsFromItems(instance, seedItems(history), systemPrompt,
                buildTools(capabilities), List.of());
    }

    private ResponseCreateParams buildParamsFromItems(TurLLMInstance instance,
            List<ResponseInputItem> items, String systemPrompt, List<Tool> serverTools,
            List<Tool> functionTools) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(resolveModel(instance));

        if (StringUtils.hasText(systemPrompt)) {
            builder.instructions(systemPrompt);
        }
        builder.inputOfResponse(items);

        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature());
        }
        for (Tool tool : serverTools) {
            builder.addTool(tool);
        }
        for (Tool tool : functionTools) {
            builder.addTool(tool);
        }
        return builder.build();
    }

    private List<ResponseInputItem> seedItems(List<ChatMessageItem> history) {
        List<ResponseInputItem> items = new ArrayList<>();
        if (history == null) {
            return items;
        }
        for (ChatMessageItem message : history) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            items.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(roleFor(message.role()))
                    .content(message.content())
                    .build()));
        }
        return items;
    }

    /**
     * T433 — translate the agent's coexisting {@link ToolCallback}s into Responses
     * {@code function} tools (client-executed). The JSON-Schema string each
     * callback exposes becomes the function's {@code parameters}; {@code strict}
     * is left {@code false} because Turing's tool schemas aren't authored to the
     * strict-mode subset.
     */
    List<Tool> buildFunctionTools(ToolCallback[] callbacks) {
        List<Tool> tools = new ArrayList<>();
        if (callbacks == null) {
            return tools;
        }
        for (ToolCallback callback : callbacks) {
            ToolDefinition definition = callback.getToolDefinition();
            FunctionTool.Parameters.Builder parameters = FunctionTool.Parameters.builder();
            for (Map.Entry<String, Object> entry : toolSupport.parseSchema(callback).entrySet()) {
                parameters.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
            }
            tools.add(Tool.ofFunction(FunctionTool.builder()
                    .name(definition.name())
                    .description(definition.description() == null ? "" : definition.description())
                    .parameters(parameters.build())
                    .strict(false)
                    .build()));
        }
        return tools;
    }

    private static List<ResponseFunctionToolCall> functionCalls(Response response) {
        List<ResponseFunctionToolCall> calls = new ArrayList<>();
        for (ResponseOutputItem item : response.output()) {
            if (item.isFunctionCall()) {
                calls.add(item.asFunctionCall());
            }
        }
        return calls;
    }

    /**
     * Translate the enabled capability set into Responses built-in tools.
     * Capabilities that need configuration they don't have (file_search with no
     * vector store, mcp with no server) are skipped with a warning rather than
     * producing an invalid request.
     */
    List<Tool> buildTools(List<EnabledCapability> capabilities) {
        List<Tool> tools = new ArrayList<>();
        if (capabilities == null) {
            return tools;
        }
        for (EnabledCapability enabled : capabilities) {
            Map<String, Object> config = optionsParser.parse(enabled.configJson());
            switch (enabled.capability()) {
                case OPENAI_WEB_SEARCH -> tools.add(Tool.ofWebSearch(WebSearchTool.builder()
                        .type(WebSearchTool.Type.WEB_SEARCH)
                        .build()));
                case OPENAI_FILE_SEARCH -> {
                    List<String> vectorStoreIds = optionsParser.stringListValue(config, "vectorStoreIds");
                    if (vectorStoreIds.isEmpty()) {
                        log.warn("[Native][OpenAI-Responses] file_search enabled but no "
                                + "vectorStoreIds configured — skipping tool");
                    } else {
                        tools.add(Tool.ofFileSearch(FileSearchTool.builder()
                                .vectorStoreIds(vectorStoreIds)
                                .build()));
                    }
                }
                case OPENAI_CODE_INTERPRETER -> tools.add(Tool.ofCodeInterpreter(
                        Tool.CodeInterpreter.builder()
                                .container(Tool.CodeInterpreter.Container.ofCodeInterpreterToolAuto(
                                        Tool.CodeInterpreter.Container.CodeInterpreterToolAuto.builder()
                                                .build()))
                                .build()));
                case OPENAI_IMAGE_GENERATION -> tools.add(Tool.ofImageGeneration(
                        Tool.ImageGeneration.builder().build()));
                case OPENAI_MCP -> {
                    String serverLabel = optionsParser.stringValue(config, "serverLabel");
                    String serverUrl = optionsParser.stringValue(config, "serverUrl");
                    if (StringUtils.hasText(serverLabel) && StringUtils.hasText(serverUrl)) {
                        tools.add(Tool.ofMcp(Tool.Mcp.builder()
                                .serverLabel(serverLabel)
                                .serverUrl(serverUrl)
                                .requireApproval(Tool.Mcp.RequireApproval.ofMcpToolApprovalSetting(
                                        Tool.Mcp.RequireApproval.McpToolApprovalSetting.NEVER))
                                .build()));
                    } else {
                        log.warn("[Native][OpenAI-Responses] mcp enabled but serverLabel/serverUrl "
                                + "missing — skipping tool");
                    }
                }
                case OPENAI_COMPUTER_USE -> log.debug("[Native][OpenAI-Responses] computer_use is "
                        + "handled by the dedicated multi-turn loop, not as a one-shot tool — skipping here");
            }
        }
        return tools;
    }

    /** Concatenate the text of every assistant message item in the response. */
    String extractText(Response response) {
        StringBuilder text = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage message = item.asMessage();
            for (ResponseOutputMessage.Content content : message.content()) {
                if (content.isOutputText()) {
                    text.append(content.asOutputText().text());
                }
            }
        }
        return text.toString();
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }

    private EasyInputMessage.Role roleFor(String role) {
        if (role == null) {
            return EasyInputMessage.Role.USER;
        }
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant" -> EasyInputMessage.Role.ASSISTANT;
            case "system" -> EasyInputMessage.Role.SYSTEM;
            case "developer" -> EasyInputMessage.Role.DEVELOPER;
            default -> EasyInputMessage.Role.USER;
        };
    }
}
