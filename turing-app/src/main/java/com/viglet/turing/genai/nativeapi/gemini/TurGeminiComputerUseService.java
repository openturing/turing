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
package com.viglet.turing.genai.nativeapi.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.ComputerUse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.FunctionResponseBlob;
import com.google.genai.types.FunctionResponsePart;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;
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
 * T494 / §X.19 — runs a chat turn through the Gemini {@code computer_use} tool
 * (model family {@code gemini-2.5-computer-use-*}): the multi-turn
 * screenshot/action loop, the Gemini analog of
 * {@link com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService}
 * and {@code TurAnthropicComputerUseService}.
 *
 * <p>Gemini drives the screen via <b>function calling</b>: the model emits a
 * {@link FunctionCall} (a predefined UI action — {@code click_at} /
 * {@code type_text_at} / {@code scroll_document} / …), Turing performs it through
 * the shared vendor-neutral {@link TurComputerUseDriver} seam (T136) and returns
 * a {@link FunctionResponse} carrying the resulting screenshot as inline image
 * data, repeatedly, until the model produces a final text answer.
 *
 * <p>Reuses the same driver seam as the other two vendors — Turing ships a no-op
 * driver, so when none is available the loop replies with guidance rather than
 * failing (errors-as-text). The capability {@code owns the turn}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGeminiComputerUseService {

    /** A Gemini model that supports computer use; overridable per capability via {@code model}. */
    static final String DEFAULT_MODEL = "gemini-2.5-computer-use-preview-10-2025";
    static final int DEFAULT_WIDTH = 1440;
    static final int DEFAULT_HEIGHT = 900;
    static final int DEFAULT_MAX_ITERATIONS = 12;
    private static final String ASSISTANT = "assistant";

    private final TurProviderOptionsParser optionsParser;
    private final TurComputerUseDriver driver;

    public TurGeminiComputerUseService(TurProviderOptionsParser optionsParser,
            TurComputerUseDriver driver) {
        this.optionsParser = optionsParser;
        this.driver = driver;
    }

    /**
     * Drive the computer-use loop and emit Gemini's final text as a single SSE
     * token event (the loop must finish before the answer is known).
     */
    public Flux<ChatResponse> chat(Client client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt, EnabledCapability capability) {
        Config config = parseConfig(capability);

        if (!driver.isAvailable()) {
            log.info("[Native][Gemini-ComputerUse] instance '{}' has computer_use enabled but no "
                    + "driver is available — replying with guidance", instance.getId());
            return Flux.just(new ChatResponse(ASSISTANT,
                    "Computer use is enabled for this assistant, but no computer-use driver "
                            + "(e.g. a browser backend) is configured on this deployment, so I "
                            + "can't operate a screen yet."));
        }

        return Mono.fromCallable(() -> runLoop(client, instance, history, systemPrompt, config))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(text -> Flux.just(new ChatResponse(ASSISTANT,
                        StringUtils.hasText(text) ? text : "")))
                .doOnError(err -> log.error("[Native][Gemini-ComputerUse] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    /** The synchronous loop body, run on a bounded-elastic worker. */
    String runLoop(Client client, TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, Config config) {
        long t0 = System.currentTimeMillis();
        Tool computerTool = Tool.builder()
                .computerUse(ComputerUse.builder()
                        .environment(geminiEnvironment(config.environment())).build())
                .build();

        try (TurComputerUseDriver.Session session =
                driver.start(config.environment(), config.width(), config.height(), config.startUrl())) {

            List<Content> conversation = seedConversation(history);
            StringBuilder finalText = new StringBuilder();
            int steps = 0;

            for (int iteration = 0; iteration < config.maxIterations(); iteration++) {
                GenerateContentConfig genConfig = buildConfig(systemPrompt, computerTool, instance);
                GenerateContentResponse response =
                        client.models.generateContent(config.model(), conversation, genConfig);
                appendText(response, finalText);

                Optional<FunctionCall> call = firstFunctionCall(response);
                if (call.isEmpty()) {
                    log.info("[Native][Gemini-ComputerUse] instance '{}' finished after {} action(s) "
                            + "in {} ms", instance.getId(), steps, System.currentTimeMillis() - t0);
                    return finalText.toString();
                }

                // Append the model turn (with the functionCall), then the user
                // turn carrying the screenshot functionResponse.
                response.candidates().filter(c -> !c.isEmpty())
                        .flatMap(c -> c.get(0).content())
                        .ifPresent(conversation::add);

                TurComputerUseAction action = translate(call.get());
                TurComputerUseScreenshot shot = (action.kind() == TurComputerUseAction.Kind.SCREENSHOT)
                        ? session.screenshot()
                        : session.execute(action);
                steps++;
                log.debug("[Native][Gemini-ComputerUse] instance '{}' step {} action {} -> url {}",
                        instance.getId(), steps, action.kind(), shot.currentUrl());

                conversation.add(functionResponseContent(call.get().name().orElse("action"), shot));
            }

            log.info("[Native][Gemini-ComputerUse] instance '{}' hit the {}-step cap",
                    instance.getId(), config.maxIterations());
            return appendCapNote(finalText, config.maxIterations());
        }
    }

    /** Seed the conversation with the assembled chat history (plain text turns). */
    private static List<Content> seedConversation(List<ChatMessageItem> history) {
        List<Content> conversation = new ArrayList<>();
        if (history == null) {
            return conversation;
        }
        for (ChatMessageItem message : history) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            conversation.add(Content.builder()
                    .role(isAssistant(message.role()) ? "model" : "user")
                    .parts(Part.fromText(message.content()))
                    .build());
        }
        return conversation;
    }

    private static GenerateContentConfig buildConfig(String systemPrompt, Tool computerTool,
            TurLLMInstance instance) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder().tools(List.of(computerTool));
        if (StringUtils.hasText(systemPrompt)) {
            builder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)));
        }
        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature().floatValue());
        }
        return builder.build();
    }

    /** Map the neutral environment to the Gemini SDK's enum value. */
    private static String geminiEnvironment(TurComputerUseEnvironment environment) {
        return environment == TurComputerUseEnvironment.BROWSER
                ? "ENVIRONMENT_BROWSER"
                : "ENVIRONMENT_DESKTOP";
    }

    /** Build the user turn that returns the screenshot as a functionResponse image part. */
    private static Content functionResponseContent(String functionName, TurComputerUseScreenshot shot) {
        byte[] png = decodeBase64(shot.pngBase64());
        FunctionResponsePart imagePart = FunctionResponsePart.builder()
                .inlineData(FunctionResponseBlob.builder()
                        .mimeType("image/png")
                        .data(png)
                        .build())
                .build();
        FunctionResponse functionResponse = FunctionResponse.builder()
                .name(functionName)
                .parts(List.of(imagePart))
                .response(Map.of("url", shot.currentUrl() == null ? "" : shot.currentUrl()))
                .build();
        return Content.builder()
                .role("user")
                .parts(Part.builder().functionResponse(functionResponse).build())
                .build();
    }

    /** The first function call in the response's first candidate, or empty. */
    private static Optional<FunctionCall> firstFunctionCall(GenerateContentResponse response) {
        return response.candidates().filter(c -> !c.isEmpty())
                .map(c -> c.get(0))
                .flatMap(Candidate::content)
                .flatMap(Content::parts)
                .flatMap(parts -> parts.stream()
                        .map(p -> p.functionCall().orElse(null))
                        .filter(fc -> fc != null && fc.name().isPresent())
                        .findFirst());
    }

    /** Concatenate the text of every text part in the response. */
    void appendText(GenerateContentResponse response, StringBuilder sink) {
        response.candidates().filter(c -> !c.isEmpty())
                .map(c -> c.get(0))
                .flatMap(Candidate::content)
                .flatMap(Content::parts)
                .ifPresent(parts -> parts.forEach(p ->
                        p.text().filter(StringUtils::hasText).ifPresent(sink::append)));
    }

    /** Translate a Gemini computer-use {@link FunctionCall} to the neutral action. */
    TurComputerUseAction translate(FunctionCall call) {
        if (call == null || call.name().isEmpty()) {
            return TurComputerUseAction.screenshot();
        }
        return translateAction(call.name().get(), call.args().orElse(Map.of()));
    }

    /**
     * Map a Gemini computer-use predefined function ({@code click_at},
     * {@code type_text_at}, {@code scroll_document}, …) + its args to the neutral
     * {@link TurComputerUseAction}. Split out so it's unit-testable without SDK
     * response types. Coordinates are Gemini's 0–999 normalised ints, passed
     * through for the driver to scale to its display.
     */
    TurComputerUseAction translateAction(String name, Map<?, ?> args) {
        if (name == null) {
            return TurComputerUseAction.screenshot();
        }
        Map<?, ?> a = args == null ? Map.of() : args;
        int x = intValue(a.get("x"), 0);
        int y = intValue(a.get("y"), 0);
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "click_at" -> TurComputerUseAction.click(x, y, "left");
            case "double_click_at" -> TurComputerUseAction.doubleClick(x, y);
            case "hover_at", "mouse_move" -> TurComputerUseAction.move(x, y);
            case "type_text_at" -> TurComputerUseAction.type(stringValue(a.get("text")));
            case "key_combination", "key" -> TurComputerUseAction.keypress(keys(stringValue(a.get("keys"))));
            case "scroll_document", "scroll_at" -> scrollAction(a, x, y);
            case "drag_and_drop" -> dragAction(a, x, y);
            case "wait_5_seconds", "wait" -> TurComputerUseAction.waitAction();
            // open_web_browser / go_back / go_forward / search / navigate and any
            // unrecognised function: just re-capture the screen for the next step.
            default -> TurComputerUseAction.screenshot();
        };
    }

    private static TurComputerUseAction scrollAction(Map<?, ?> args, int x, int y) {
        String direction = stringValue(args.get("direction"));
        int amount = intValue(args.get("magnitude"), 3);
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
        return TurComputerUseAction.scroll(x, y, dx, dy);
    }

    private static TurComputerUseAction dragAction(Map<?, ?> args, int endX, int endY) {
        int startX = intValue(args.get("startX"), intValue(args.get("x"), 0));
        int startY = intValue(args.get("startY"), intValue(args.get("y"), 0));
        int destX = intValue(args.get("destinationX"), endX);
        int destY = intValue(args.get("destinationY"), endY);
        return TurComputerUseAction.drag(List.of(
                new TurComputerUseAction.Point(startX, startY),
                new TurComputerUseAction.Point(destX, destY)));
    }

    Config parseConfig(EnabledCapability capability) {
        Map<String, Object> config = optionsParser.parse(capability == null ? null : capability.configJson());
        String model = optionsParser.stringValue(config, "model");
        Integer width = optionsParser.intValue(config, "displayWidth");
        Integer height = optionsParser.intValue(config, "displayHeight");
        Integer maxIterations = optionsParser.intValue(config, "maxIterations");
        TurComputerUseEnvironment environment = TurComputerUseEnvironment.fromString(
                optionsParser.stringValue(config, "environment"), TurComputerUseEnvironment.BROWSER);
        return new Config(
                StringUtils.hasText(model) ? model : DEFAULT_MODEL,
                environment,
                width != null && width > 0 ? width : DEFAULT_WIDTH,
                height != null && height > 0 ? height : DEFAULT_HEIGHT,
                optionsParser.stringValue(config, "startUrl"),
                maxIterations != null && maxIterations > 0 ? maxIterations : DEFAULT_MAX_ITERATIONS);
    }

    private static boolean isAssistant(String role) {
        return role != null && ASSISTANT.equals(role.toLowerCase(Locale.ROOT));
    }

    private static List<String> keys(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(text.split("[+\\s]+"));
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

    private static byte[] decodeBase64(String base64) {
        if (!StringUtils.hasText(base64)) {
            return new byte[0];
        }
        try {
            return java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
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
            String startUrl, int maxIterations) {
    }
}
