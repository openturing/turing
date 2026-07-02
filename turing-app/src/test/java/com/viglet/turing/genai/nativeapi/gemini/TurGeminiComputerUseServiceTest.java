/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.genai.Client;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseAction;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseDriver;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import reactor.core.publisher.Flux;

/**
 * T494 / §X.19 — unit coverage for the Gemini computer-use loop's pure pieces:
 * config parsing, action translation, and the no-driver guidance reply (the
 * shipped default since Turing ships a no-op driver).
 */
@ExtendWith(MockitoExtension.class)
class TurGeminiComputerUseServiceTest {

    @Mock private TurComputerUseDriver driver;
    @Mock private Client client;

    private TurGeminiComputerUseService service() {
        return new TurGeminiComputerUseService(new TurProviderOptionsParser(), driver);
    }

    private static TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        return instance;
    }

    @Test
    void repliesWithGuidanceWhenNoDriverAvailable() {
        when(driver.isAvailable()).thenReturn(false);
        Flux<ChatResponse> result = service().chat(client, instance(),
                List.of(new ChatMessageItem("user", "open example.com")), "sys",
                new EnabledCapability(TurNativeCapability.GEMINI_COMPUTER_USE, null));

        ChatResponse first = result.blockFirst();
        assertThat(first).isNotNull();
        assertThat(first.content()).contains("no computer-use driver");
    }

    @Test
    void parseConfigDefaultsWhenNoConfigJson() {
        TurGeminiComputerUseService.Config config =
                service().parseConfig(new EnabledCapability(TurNativeCapability.GEMINI_COMPUTER_USE, null));
        assertThat(config.model()).isEqualTo(TurGeminiComputerUseService.DEFAULT_MODEL);
        assertThat(config.maxIterations()).isEqualTo(TurGeminiComputerUseService.DEFAULT_MAX_ITERATIONS);
        assertThat(config.width()).isEqualTo(TurGeminiComputerUseService.DEFAULT_WIDTH);
    }

    @Test
    void parseConfigReadsOverrides() {
        TurGeminiComputerUseService.Config config = service().parseConfig(new EnabledCapability(
                TurNativeCapability.GEMINI_COMPUTER_USE,
                "{\"model\":\"gemini-2.5-computer-use-x\",\"maxIterations\":3,\"displayWidth\":800}"));
        assertThat(config.model()).isEqualTo("gemini-2.5-computer-use-x");
        assertThat(config.maxIterations()).isEqualTo(3);
        assertThat(config.width()).isEqualTo(800);
    }

    @Test
    void translatesClickType() {
        TurGeminiComputerUseService svc = service();
        TurComputerUseAction click = svc.translateAction("click_at", Map.of("x", 100, "y", 200));
        assertThat(click.kind()).isEqualTo(TurComputerUseAction.Kind.CLICK);

        TurComputerUseAction type = svc.translateAction("type_text_at", Map.of("text", "hi"));
        assertThat(type.kind()).isEqualTo(TurComputerUseAction.Kind.TYPE);
    }

    @Test
    void translatesScrollWithDirection() {
        TurComputerUseAction scroll = service().translateAction("scroll_document",
                Map.of("direction", "down", "magnitude", 5));
        assertThat(scroll.kind()).isEqualTo(TurComputerUseAction.Kind.SCROLL);
    }

    @Test
    void unknownActionFallsBackToScreenshot() {
        TurComputerUseAction action = service().translateAction("open_web_browser", Map.of());
        assertThat(action.kind()).isEqualTo(TurComputerUseAction.Kind.SCREENSHOT);
    }
}
