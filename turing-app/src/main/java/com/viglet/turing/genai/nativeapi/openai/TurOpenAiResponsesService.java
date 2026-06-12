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

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
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
 * server-side call.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurOpenAiResponsesService {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final TurProviderOptionsParser optionsParser;

    public TurOpenAiResponsesService(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    /**
     * Execute the turn and emit the assistant text as a single SSE token event
     * (CALL semantics — the Responses built-in-tool turn must complete before
     * the answer is known).
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities) {
        ResponseCreateParams params = buildParams(instance, history, systemPrompt, capabilities);
        return Mono.fromCallable(() -> {
                    long t0 = System.currentTimeMillis();
                    Response response = client.responses().create(params);
                    String text = extractText(response);
                    log.info("[Native][OpenAI-Responses] instance '{}' model '{}' tools {} -> {} chars in {} ms",
                            instance.getId(), resolveModel(instance),
                            capabilities.stream().map(c -> c.capability().getKey()).toList(),
                            text.length(), System.currentTimeMillis() - t0);
                    return text;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(text -> Flux.just(new ChatResponse("assistant",
                        StringUtils.hasText(text) ? text : "")))
                .doOnError(err -> log.error("[Native][OpenAI-Responses] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    ResponseCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(resolveModel(instance));

        if (StringUtils.hasText(systemPrompt)) {
            builder.instructions(systemPrompt);
        }

        List<ResponseInputItem> items = new ArrayList<>();
        if (history != null) {
            for (ChatMessageItem message : history) {
                if (message == null || !StringUtils.hasText(message.content())) {
                    continue;
                }
                items.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                        .role(roleFor(message.role()))
                        .content(message.content())
                        .build()));
            }
        }
        builder.inputOfResponse(items);

        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature());
        }

        for (Tool tool : buildTools(capabilities)) {
            builder.addTool(tool);
        }
        return builder.build();
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
                case OPENAI_COMPUTER_USE -> log.info("[Native][OpenAI-Responses] computer_use is "
                        + "advertised but not wired (needs the screenshot/action loop) — skipping");
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
