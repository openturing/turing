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
package com.viglet.turing.genai.nativeapi.openai.computeruse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.ComputerUsePreviewTool;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseComputerToolCall;
import com.openai.models.responses.ResponseComputerToolCall.PendingSafetyCheck;
import com.openai.models.responses.ResponseComputerToolCallOutputScreenshot;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputItem.ComputerCallOutput;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.Tool;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * T136 / §X.3.d — runs a chat turn through the OpenAI <b>Responses API</b>
 * {@code computer_use} built-in tool: the multi-turn screenshot/action loop the
 * other built-ins (web_search, file_search, …) don't need.
 *
 * <p>Unlike the one-shot tools wired in {@code TurOpenAiResponsesService}, the
 * computer-use tool is conversational with the client: the model emits a
 * {@code computer_call} (click / type / scroll / …), Turing executes it against
 * a {@link TurComputerUseDriver}, captures a screenshot, and feeds it back as a
 * {@code computer_call_output} — repeatedly, until the model produces a final
 * text answer. Conversation state is carried by {@code previous_response_id}, so
 * each step sends only the new screenshot.
 *
 * <p>The action execution + screenshots are delegated to the pluggable
 * {@link TurComputerUseDriver} seam. Turing ships {@link TurNoOpComputerUseDriver}
 * (never available); when no real driver is configured the loop replies with an
 * explanatory message rather than failing — errors-as-text, like the skill
 * activation harness (T322).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOpenAiComputerUseService {

    /** OpenAI's computer-use model; overridable per capability via {@code model}. */
    static final String DEFAULT_MODEL = "computer-use-preview";
    static final int DEFAULT_WIDTH = 1024;
    static final int DEFAULT_HEIGHT = 768;
    static final int DEFAULT_MAX_ITERATIONS = 12;

    private final TurProviderOptionsParser optionsParser;
    private final TurComputerUseDriver driver;

    public TurOpenAiComputerUseService(TurProviderOptionsParser optionsParser,
            TurComputerUseDriver driver) {
        this.optionsParser = optionsParser;
        this.driver = driver;
    }

    /**
     * Drive the computer-use loop and emit the model's final text as a single SSE
     * token event (CALL semantics — the loop must finish before the answer is
     * known).
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt, EnabledCapability capability) {

        Config config = parseConfig(capability);

        if (!driver.isAvailable()) {
            log.info("[Native][OpenAI-ComputerUse] instance '{}' has computer_use enabled but no "
                    + "driver is available — replying with guidance", instance.getId());
            return Flux.just(new ChatResponse("assistant",
                    "Computer use is enabled for this assistant, but no computer-use driver "
                            + "(e.g. a browser backend) is configured on this deployment, so I "
                            + "can't operate a screen yet."));
        }

        return Mono.fromCallable(() -> runLoop(client, instance, history, systemPrompt, config))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(text -> Flux.just(new ChatResponse("assistant",
                        StringUtils.hasText(text) ? text : "")))
                .doOnError(err -> log.error("[Native][OpenAI-ComputerUse] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    /** The synchronous loop body, run on a bounded-elastic worker. */
    String runLoop(OpenAIClient client, TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, Config config) {
        long t0 = System.currentTimeMillis();
        Tool computerTool = Tool.ofComputerUsePreview(ComputerUsePreviewTool.builder()
                .displayWidth(config.width())
                .displayHeight(config.height())
                .environment(environmentFor(config.environment()))
                .build());

        try (TurComputerUseDriver.Session session =
                driver.start(config.environment(), config.width(), config.height(), config.startUrl())) {

            ResponseCreateParams params = firstRequest(instance, history, systemPrompt, config, computerTool);
            StringBuilder finalText = new StringBuilder();
            int steps = 0;

            for (int iteration = 0; iteration < config.maxIterations(); iteration++) {
                Response response = client.responses().create(params);
                appendMessageText(response, finalText);

                Optional<ResponseComputerToolCall> call = firstComputerCall(response);
                if (call.isEmpty()) {
                    log.info("[Native][OpenAI-ComputerUse] instance '{}' finished after {} action(s) "
                            + "in {} ms", instance.getId(), steps, System.currentTimeMillis() - t0);
                    return finalText.toString();
                }

                List<PendingSafetyCheck> pending = call.get().pendingSafetyChecks();
                if (!pending.isEmpty() && !config.autoAcknowledgeSafetyChecks()) {
                    log.warn("[Native][OpenAI-ComputerUse] instance '{}' paused: {} pending safety "
                            + "check(s), auto-acknowledge disabled", instance.getId(), pending.size());
                    return appendPausedNote(finalText, pending.size());
                }

                TurComputerUseAction action = translate(call.get());
                TurComputerUseScreenshot shot = (action.kind() == TurComputerUseAction.Kind.SCREENSHOT)
                        ? session.screenshot()
                        : session.execute(action);
                steps++;
                log.debug("[Native][OpenAI-ComputerUse] instance '{}' step {} action {} -> url {}",
                        instance.getId(), steps, action.kind(), shot.currentUrl());

                params = nextRequest(config, computerTool, response.id(), call.get(), pending, shot);
            }

            log.info("[Native][OpenAI-ComputerUse] instance '{}' hit the {}-step cap", instance.getId(),
                    config.maxIterations());
            return appendCapNote(finalText, config.maxIterations());
        }
    }

    /** Initial request: the conversation history + the computer-use tool. */
    ResponseCreateParams firstRequest(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, Config config, Tool computerTool) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(config.model())
                // Required by the computer-use tool: lets the server drop older
                // turns/screenshots instead of erroring when the context fills.
                .truncation(ResponseCreateParams.Truncation.AUTO)
                .addTool(computerTool);

        if (StringUtils.hasText(systemPrompt)) {
            builder.instructions(systemPrompt);
        }
        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature());
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
        return builder.build();
    }

    /**
     * Follow-up request: carry state via {@code previous_response_id} and send
     * only the new screenshot as the {@code computer_call_output} (plus any
     * acknowledged safety checks).
     */
    ResponseCreateParams nextRequest(Config config, Tool computerTool, String previousResponseId,
            ResponseComputerToolCall call, List<PendingSafetyCheck> pending,
            TurComputerUseScreenshot shot) {
        ComputerCallOutput.Builder output = ComputerCallOutput.builder()
                .callId(call.callId())
                .output(ResponseComputerToolCallOutputScreenshot.builder()
                        .imageUrl(toDataUri(shot.pngBase64()))
                        .build());

        if (!pending.isEmpty()) {
            output.acknowledgedSafetyChecks(pending.stream()
                    .map(check -> ComputerCallOutput.AcknowledgedSafetyCheck.builder()
                            .id(check.id())
                            .build())
                    .toList());
        }

        // Carry conversation state by referencing the response that produced this
        // computer_call, so the follow-up only sends the new screenshot.
        return ResponseCreateParams.builder()
                .model(config.model())
                .truncation(ResponseCreateParams.Truncation.AUTO)
                .addTool(computerTool)
                .previousResponseId(previousResponseId)
                .inputOfResponse(List.of(ResponseInputItem.ofComputerCallOutput(output.build())))
                .build();
    }

    /** Translate the model's {@code computer_call} action union to the neutral DTO. */
    TurComputerUseAction translate(ResponseComputerToolCall call) {
        Optional<ResponseComputerToolCall.Action> maybe = call.action();
        if (maybe.isEmpty()) {
            return TurComputerUseAction.screenshot();
        }
        ResponseComputerToolCall.Action action = maybe.get();
        if (action.isClick()) {
            var click = action.asClick();
            return TurComputerUseAction.click((int) click.x(), (int) click.y(),
                    click.button().asString());
        }
        if (action.isDoubleClick()) {
            var dc = action.asDoubleClick();
            return TurComputerUseAction.doubleClick((int) dc.x(), (int) dc.y());
        }
        if (action.isMove()) {
            var move = action.asMove();
            return TurComputerUseAction.move((int) move.x(), (int) move.y());
        }
        if (action.isScroll()) {
            var scroll = action.asScroll();
            return TurComputerUseAction.scroll((int) scroll.x(), (int) scroll.y(),
                    (int) scroll.scrollX(), (int) scroll.scrollY());
        }
        if (action.isKeypress()) {
            return TurComputerUseAction.keypress(action.asKeypress().keys());
        }
        if (action.isType()) {
            return TurComputerUseAction.type(action.asType().text());
        }
        if (action.isDrag()) {
            List<TurComputerUseAction.Point> path = action.asDrag().path().stream()
                    .map(p -> new TurComputerUseAction.Point((int) p.x(), (int) p.y()))
                    .toList();
            return TurComputerUseAction.drag(path);
        }
        if (action.isWait()) {
            return TurComputerUseAction.waitAction();
        }
        // isScreenshot() or any unrecognised variant: just re-capture the screen.
        return TurComputerUseAction.screenshot();
    }

    /** Concatenate the text of every assistant message item in the response. */
    void appendMessageText(Response response, StringBuilder sink) {
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage message = item.asMessage();
            for (ResponseOutputMessage.Content content : message.content()) {
                if (content.isOutputText()) {
                    sink.append(content.asOutputText().text());
                }
            }
        }
    }

    private static Optional<ResponseComputerToolCall> firstComputerCall(Response response) {
        for (ResponseOutputItem item : response.output()) {
            if (item.isComputerCall()) {
                return Optional.of(item.asComputerCall());
            }
        }
        return Optional.empty();
    }

    Config parseConfig(EnabledCapability capability) {
        Map<String, Object> config = optionsParser.parse(capability == null ? null : capability.configJson());
        String model = optionsParser.stringValue(config, "model");
        Integer width = optionsParser.intValue(config, "displayWidth");
        Integer height = optionsParser.intValue(config, "displayHeight");
        Integer maxIterations = optionsParser.intValue(config, "maxIterations");
        Boolean autoAck = optionsParser.booleanValue(config, "autoAcknowledgeSafetyChecks");
        TurComputerUseEnvironment environment = TurComputerUseEnvironment.fromString(
                optionsParser.stringValue(config, "environment"), TurComputerUseEnvironment.BROWSER);
        return new Config(
                StringUtils.hasText(model) ? model : DEFAULT_MODEL,
                environment,
                width != null && width > 0 ? width : DEFAULT_WIDTH,
                height != null && height > 0 ? height : DEFAULT_HEIGHT,
                optionsParser.stringValue(config, "startUrl"),
                maxIterations != null && maxIterations > 0 ? maxIterations : DEFAULT_MAX_ITERATIONS,
                Boolean.TRUE.equals(autoAck));
    }

    private static String toDataUri(String pngBase64) {
        String base64 = pngBase64 == null ? "" : pngBase64;
        return "data:image/png;base64," + base64;
    }

    private static String appendPausedNote(StringBuilder text, int pendingCount) {
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append("(Computer-use paused: the requested action raised ").append(pendingCount)
                .append(" safety check(s) that require explicit approval.)");
        return text.toString();
    }

    private static String appendCapNote(StringBuilder text, int cap) {
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append("(Computer-use stopped after the ").append(cap)
                .append("-step limit without reaching a final answer.)");
        return text.toString();
    }

    private static ComputerUsePreviewTool.Environment environmentFor(TurComputerUseEnvironment env) {
        return switch (env) {
            case MAC -> ComputerUsePreviewTool.Environment.MAC;
            case WINDOWS -> ComputerUsePreviewTool.Environment.WINDOWS;
            case UBUNTU -> ComputerUsePreviewTool.Environment.UBUNTU;
            case LINUX -> ComputerUsePreviewTool.Environment.LINUX;
            case BROWSER -> ComputerUsePreviewTool.Environment.BROWSER;
        };
    }

    private static EasyInputMessage.Role roleFor(String role) {
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

    /** Resolved per-turn computer-use configuration. */
    record Config(String model, TurComputerUseEnvironment environment, int width, int height,
            String startUrl, int maxIterations, boolean autoAcknowledgeSafetyChecks) {
    }
}
