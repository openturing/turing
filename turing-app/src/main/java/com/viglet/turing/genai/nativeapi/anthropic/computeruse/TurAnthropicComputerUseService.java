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
package com.viglet.turing.genai.nativeapi.anthropic.computeruse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaToolComputerUse20251124;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.BetaToolUnion;
import com.anthropic.models.beta.messages.BetaToolUseBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseAction;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseDriver;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseEnvironment;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseScreenshot;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * T143 / §X.4.e — runs a chat turn through the Anthropic <b>beta Messages API</b>
 * {@code computer_use_20251124} tool: the multi-turn screenshot/action loop, the
 * Anthropic analog of {@link com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService}.
 *
 * <p>Unlike the one-shot server tools in {@code TurAnthropicMessagesService}, the
 * computer-use tool is conversational with the client: Claude emits a
 * {@code tool_use} block (an {@code action} the {@link TurComputerUseDriver}
 * performs — click / type / scroll / …), Turing executes it, captures a
 * screenshot, and returns it as a {@code tool_result} image block — repeatedly,
 * until Claude produces a final text answer. Anthropic is stateless, so the whole
 * conversation is resent each step (no {@code previous_response_id} shortcut);
 * the running {@link BetaMessageParam} list is threaded through every call, and
 * each response is appended via {@link BetaMessage#toParam()}.
 *
 * <p>Action execution + screenshots are delegated to the same pluggable
 * {@link TurComputerUseDriver} seam the OpenAI loop uses (its DTOs are
 * vendor-neutral). Turing ships a no-op driver; when none is available the loop
 * replies with guidance rather than failing — errors-as-text. Anthropic's
 * computer-use tool additionally runs a server-side prompt-injection classifier
 * on each screenshot, a property the OpenAI variant lacks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAnthropicComputerUseService {

    /** A Claude model that supports computer use; overridable per capability via {@code model}. */
    static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    static final long DEFAULT_MAX_TOKENS = 4096L;
    static final int DEFAULT_WIDTH = 1024;
    static final int DEFAULT_HEIGHT = 768;
    static final int DEFAULT_MAX_ITERATIONS = 12;

    private final TurProviderOptionsParser optionsParser;
    private final TurComputerUseDriver driver;

    public TurAnthropicComputerUseService(TurProviderOptionsParser optionsParser,
            TurComputerUseDriver driver) {
        this.optionsParser = optionsParser;
        this.driver = driver;
    }

    /**
     * Drive the computer-use loop and emit Claude's final text as a single SSE
     * token event (CALL semantics — the loop must finish before the answer is
     * known).
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt, EnabledCapability capability) {

        Config config = parseConfig(capability);

        if (!driver.isAvailable()) {
            log.info("[Native][Anthropic-ComputerUse] instance '{}' has computer_use enabled but no "
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
                .doOnError(err -> log.error("[Native][Anthropic-ComputerUse] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    /** The synchronous loop body, run on a bounded-elastic worker. */
    String runLoop(AnthropicClient client, TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, Config config) {
        long t0 = System.currentTimeMillis();
        BetaToolUnion computerTool = BetaToolUnion.ofComputerUse20251124(
                BetaToolComputerUse20251124.builder()
                        .displayWidthPx(config.width())
                        .displayHeightPx(config.height())
                        .build());

        try (TurComputerUseDriver.Session session =
                driver.start(config.environment(), config.width(), config.height(), config.startUrl())) {

            List<BetaMessageParam> conversation = seedConversation(history);
            StringBuilder finalText = new StringBuilder();
            int steps = 0;

            for (int iteration = 0; iteration < config.maxIterations(); iteration++) {
                MessageCreateParams params = buildParams(instance, config, computerTool, systemPrompt,
                        conversation);
                BetaMessage response = client.beta().messages().create(params);
                appendMessageText(response, finalText);

                Optional<BetaToolUseBlock> call = firstComputerToolUse(response);
                if (call.isEmpty()) {
                    log.info("[Native][Anthropic-ComputerUse] instance '{}' finished after {} action(s) "
                            + "in {} ms", instance.getId(), steps, System.currentTimeMillis() - t0);
                    return finalText.toString();
                }

                // Append the assistant turn (carrying the tool_use), then the
                // user turn carrying the screenshot tool_result.
                conversation.add(response.toParam());

                TurComputerUseAction action = translate(call.get());
                TurComputerUseScreenshot shot = (action.kind() == TurComputerUseAction.Kind.SCREENSHOT)
                        ? session.screenshot()
                        : session.execute(action);
                steps++;
                log.debug("[Native][Anthropic-ComputerUse] instance '{}' step {} action {} -> url {}",
                        instance.getId(), steps, action.kind(), shot.currentUrl());

                conversation.add(toolResultMessage(call.get().id(), shot));
            }

            log.info("[Native][Anthropic-ComputerUse] instance '{}' hit the {}-step cap",
                    instance.getId(), config.maxIterations());
            return appendCapNote(finalText, config.maxIterations());
        }
    }

    /** Seed the conversation with the assembled chat history (plain text turns). */
    private static List<BetaMessageParam> seedConversation(List<ChatMessageItem> history) {
        List<BetaMessageParam> conversation = new ArrayList<>();
        if (history == null) {
            return conversation;
        }
        for (ChatMessageItem message : history) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            conversation.add(BetaMessageParam.builder()
                    .role(isAssistant(message.role())
                            ? BetaMessageParam.Role.ASSISTANT
                            : BetaMessageParam.Role.USER)
                    .content(message.content())
                    .build());
        }
        return conversation;
    }

    MessageCreateParams buildParams(TurLLMInstance instance, Config config, BetaToolUnion computerTool,
            String systemPrompt, List<BetaMessageParam> conversation) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .addBeta(AnthropicBeta.COMPUTER_USE_2025_01_24)
                .addTool(computerTool)
                .messages(conversation);
        if (StringUtils.hasText(systemPrompt)) {
            builder.system(systemPrompt);
        }
        if (instance.getTemperature() != null) {
            applyTemperature(builder, instance.getTemperature());
        }
        return builder.build();
    }

    /**
     * Apply the per-instance temperature. The Anthropic SDK deprecated
     * {@code temperature(double)} because models after Claude Opus 4.6 reject any
     * non-1.0 temperature (HTTP 400). Turing still honours the configured
     * temperature deliberately — for the many older Claude models, and
     * OpenAI-compatible Anthropic proxies, that accept it — so the deprecation is
     * suppressed here, scoped to this one call to keep the signal for any other
     * deprecated API elsewhere.
     */
    @SuppressWarnings("deprecation")
    private static void applyTemperature(MessageCreateParams.Builder builder, double temperature) {
        builder.temperature(temperature);
    }

    /** Build the user turn that returns a screenshot as the tool_result image. */
    private static BetaMessageParam toolResultMessage(String toolUseId, TurComputerUseScreenshot shot) {
        BetaImageBlockParam image = BetaImageBlockParam.builder()
                .source(BetaImageBlockParam.Source.ofBase64(BetaBase64ImageSource.builder()
                        .data(shot.pngBase64() == null ? "" : shot.pngBase64())
                        .mediaType(BetaBase64ImageSource.MediaType.IMAGE_PNG)
                        .build()))
                .build();
        BetaContentBlockParam toolResult = BetaContentBlockParam.ofToolResult(
                BetaToolResultBlockParam.builder()
                        .toolUseId(toolUseId)
                        .contentOfBlocks(List.of(
                                BetaToolResultBlockParam.Content.Block.ofImage(image)))
                        .build());
        return BetaMessageParam.builder()
                .role(BetaMessageParam.Role.USER)
                .contentOfBetaContentBlockParams(List.of(toolResult))
                .build();
    }

    /**
     * Translate Claude's {@code computer_use} {@code tool_use} input (a JSON
     * object {@code {action, coordinate, text, …}}) to the neutral
     * {@link TurComputerUseAction} the driver understands.
     */
    TurComputerUseAction translate(BetaToolUseBlock call) {
        try {
            return translateAction(call.input(Map.class));
        } catch (RuntimeException e) {
            log.debug("[Native][Anthropic-ComputerUse] could not read tool_use input ({}); "
                    + "treating as screenshot", e.getMessage());
            return TurComputerUseAction.screenshot();
        }
    }

    /**
     * Map Claude's {@code computer_use} action object ({@code {action, coordinate,
     * text, …}}) to the neutral {@link TurComputerUseAction}. Split out from
     * {@link #translate(BetaToolUseBlock)} so it's unit-testable without SDK types.
     */
    TurComputerUseAction translateAction(Map<?, ?> input) {
        if (input == null) {
            return TurComputerUseAction.screenshot();
        }
        String action = stringValue(input.get("action"));
        if (action == null) {
            return TurComputerUseAction.screenshot();
        }
        int[] xy = point(input.get("coordinate"));
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "left_click", "key_click" -> TurComputerUseAction.click(xy[0], xy[1], "left");
            case "right_click" -> TurComputerUseAction.click(xy[0], xy[1], "right");
            case "middle_click" -> TurComputerUseAction.click(xy[0], xy[1], "middle");
            case "double_click" -> TurComputerUseAction.doubleClick(xy[0], xy[1]);
            case "mouse_move" -> TurComputerUseAction.move(xy[0], xy[1]);
            case "type" -> TurComputerUseAction.type(stringValue(input.get("text")));
            case "key" -> TurComputerUseAction.keypress(keys(stringValue(input.get("text"))));
            case "scroll" -> scrollAction(input, xy);
            case "left_click_drag" -> dragAction(input, xy);
            case "wait" -> TurComputerUseAction.waitAction();
            // "screenshot", "cursor_position", or any unrecognised action: just re-capture.
            default -> TurComputerUseAction.screenshot();
        };
    }

    private static TurComputerUseAction scrollAction(Map<?, ?> input, int[] xy) {
        String direction = stringValue(input.get("scroll_direction"));
        int amount = intValue(input.get("scroll_amount"), 3);
        int dx = 0;
        int dy = 0;
        if (direction != null) {
            switch (direction.toLowerCase(Locale.ROOT)) {
                case "up" -> dy = -amount;
                case "down" -> dy = amount;
                case "left" -> dx = -amount;
                case "right" -> dx = amount;
                default -> { /* leave at zero */ }
            }
        }
        return TurComputerUseAction.scroll(xy[0], xy[1], dx, dy);
    }

    private static TurComputerUseAction dragAction(Map<?, ?> input, int[] end) {
        int[] start = point(input.get("start_coordinate"));
        return TurComputerUseAction.drag(List.of(
                new TurComputerUseAction.Point(start[0], start[1]),
                new TurComputerUseAction.Point(end[0], end[1])));
    }

    /** Concatenate the text of every text content block in the response. */
    void appendMessageText(BetaMessage response, StringBuilder sink) {
        for (BetaContentBlock block : response.content()) {
            if (block.isText()) {
                sink.append(block.asText().text());
            }
        }
    }

    private static Optional<BetaToolUseBlock> firstComputerToolUse(BetaMessage response) {
        for (BetaContentBlock block : response.content()) {
            if (block.isToolUse()) {
                return Optional.of(block.asToolUse());
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
        Integer maxTokens = optionsParser.intValue(config, "maxTokens");
        TurComputerUseEnvironment environment = TurComputerUseEnvironment.fromString(
                optionsParser.stringValue(config, "environment"), TurComputerUseEnvironment.BROWSER);
        return new Config(
                StringUtils.hasText(model) ? model : DEFAULT_MODEL,
                environment,
                width != null && width > 0 ? width : DEFAULT_WIDTH,
                height != null && height > 0 ? height : DEFAULT_HEIGHT,
                optionsParser.stringValue(config, "startUrl"),
                maxIterations != null && maxIterations > 0 ? maxIterations : DEFAULT_MAX_ITERATIONS,
                maxTokens != null && maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS);
    }

    private static boolean isAssistant(String role) {
        return role != null && "assistant".equals(role.toLowerCase(Locale.ROOT));
    }

    private static List<String> keys(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(text.split("\\+"));
    }

    /** Read a {@code [x, y]} coordinate list into an int pair, defaulting to {@code (0, 0)}. */
    private static int[] point(Object value) {
        if (value instanceof List<?> list && list.size() >= 2) {
            return new int[] { intValue(list.get(0), 0), intValue(list.get(1), 0) };
        }
        return new int[] { 0, 0 };
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && StringUtils.hasText(s)) {
            try {
                return (int) Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String appendCapNote(StringBuilder text, int cap) {
        if (text.length() > 0) {
            text.append("\n\n");
        }
        text.append("(Computer-use stopped after the ").append(cap)
                .append("-step limit without reaching a final answer.)");
        return text.toString();
    }

    /** Resolved per-turn computer-use configuration. */
    record Config(String model, TurComputerUseEnvironment environment, int width, int height,
            String startUrl, int maxIterations, long maxTokens) {
    }
}
