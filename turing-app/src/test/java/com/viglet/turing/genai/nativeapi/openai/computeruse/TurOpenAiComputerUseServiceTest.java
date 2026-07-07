/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.openai.computeruse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService.Config;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import reactor.core.publisher.Flux;

class TurOpenAiComputerUseServiceTest {

    private final TurProviderOptionsParser parser = new TurProviderOptionsParser();

    private TurOpenAiComputerUseService serviceWith(TurComputerUseDriver driver) {
        return new TurOpenAiComputerUseService(parser, driver);
    }

    private static EnabledCapability cap(String configJson) {
        return new EnabledCapability(TurNativeCapability.OPENAI_COMPUTER_USE, configJson);
    }

    @Test
    void unavailableDriverRepliesWithGuidanceAndNeverTouchesTheClient() {
        TurOpenAiComputerUseService service = serviceWith(new TurNoOpComputerUseDriver());
        // A null client is fine: the unavailable-driver branch must short-circuit
        // before any SDK call.
        Flux<ChatResponse> flux = service.chat((OpenAIClient) null, new TurLLMInstance(),
                List.of(new ChatMessageItem("user", "open confluence")), "be helpful", cap(null));

        List<ChatResponse> emitted = flux.collectList().block();
        assertThat(emitted).hasSize(1);
        assertThat(emitted.get(0).role()).isEqualTo("assistant");
        assertThat(emitted.get(0).content()).contains("no computer-use driver");
    }

    @Test
    void parseConfigAppliesDefaultsWhenConfigIsBlank() {
        Config config = serviceWith(new TurNoOpComputerUseDriver()).parseConfig(cap(null));
        assertThat(config.model()).isEqualTo(TurOpenAiComputerUseService.DEFAULT_MODEL);
        assertThat(config.environment()).isEqualTo(TurComputerUseEnvironment.BROWSER);
        assertThat(config.width()).isEqualTo(TurOpenAiComputerUseService.DEFAULT_WIDTH);
        assertThat(config.height()).isEqualTo(TurOpenAiComputerUseService.DEFAULT_HEIGHT);
        assertThat(config.maxIterations()).isEqualTo(TurOpenAiComputerUseService.DEFAULT_MAX_ITERATIONS);
        assertThat(config.startUrl()).isNull();
        assertThat(config.autoAcknowledgeSafetyChecks()).isFalse();
    }

    @Test
    void parseConfigReadsOverrides() {
        Config config = serviceWith(new TurNoOpComputerUseDriver()).parseConfig(cap(
                "{\"model\":\"computer-use-preview-2025\",\"environment\":\"ubuntu\","
                        + "\"displayWidth\":1280,\"displayHeight\":800,\"maxIterations\":5,"
                        + "\"startUrl\":\"https://example.org\",\"autoAcknowledgeSafetyChecks\":true}"));
        assertThat(config.model()).isEqualTo("computer-use-preview-2025");
        assertThat(config.environment()).isEqualTo(TurComputerUseEnvironment.UBUNTU);
        assertThat(config.width()).isEqualTo(1280);
        assertThat(config.height()).isEqualTo(800);
        assertThat(config.maxIterations()).isEqualTo(5);
        assertThat(config.startUrl()).isEqualTo("https://example.org");
        assertThat(config.autoAcknowledgeSafetyChecks()).isTrue();
    }

    @Test
    void parseConfigIgnoresNonPositiveDimensions() {
        Config config = serviceWith(new TurNoOpComputerUseDriver())
                .parseConfig(cap("{\"displayWidth\":0,\"displayHeight\":-10,\"maxIterations\":0}"));
        assertThat(config.width()).isEqualTo(TurOpenAiComputerUseService.DEFAULT_WIDTH);
        assertThat(config.height()).isEqualTo(TurOpenAiComputerUseService.DEFAULT_HEIGHT);
        assertThat(config.maxIterations()).isEqualTo(TurOpenAiComputerUseService.DEFAULT_MAX_ITERATIONS);
    }

    @Test
    void environmentFallsBackToBrowserOnUnknownValue() {
        Config config = serviceWith(new TurNoOpComputerUseDriver())
                .parseConfig(cap("{\"environment\":\"mainframe\"}"));
        assertThat(config.environment()).isEqualTo(TurComputerUseEnvironment.BROWSER);
    }
}
