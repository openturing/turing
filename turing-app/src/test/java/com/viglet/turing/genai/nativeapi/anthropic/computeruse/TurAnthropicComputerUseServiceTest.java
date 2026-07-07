/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.anthropic.computeruse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseAction;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseDriver;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurComputerUseEnvironment;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import reactor.core.publisher.Flux;

class TurAnthropicComputerUseServiceTest {

    /** A driver that is never available — exercises the guidance path. */
    private static final TurComputerUseDriver UNAVAILABLE = new TurComputerUseDriver() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public Session start(TurComputerUseEnvironment environment, int width, int height,
                String startUrl) {
            throw new UnsupportedOperationException("unavailable");
        }
    };

    private final TurAnthropicComputerUseService service =
            new TurAnthropicComputerUseService(new TurProviderOptionsParser(), UNAVAILABLE);

    @Test
    void repliesWithGuidanceWhenNoDriverAvailable() {
        Flux<?> flux = service.chat(null, instance(), List.of(), "be helpful",
                new EnabledCapability(TurNativeCapability.ANTHROPIC_COMPUTER_USE, null));
        Object emitted = flux.blockFirst();
        assertThat(emitted).hasToString(
                "ChatResponse[role=assistant, content=Computer use is enabled for this assistant, "
                        + "but no computer-use driver (e.g. a browser backend) is configured on this "
                        + "deployment, so I can't operate a screen yet., type=token]");
    }

    @Test
    void translatesCoreActions() {
        assertThat(service.translateAction(Map.of("action", "left_click",
                "coordinate", List.of(10, 20))))
                .extracting(TurComputerUseAction::kind, TurComputerUseAction::x,
                        TurComputerUseAction::y, TurComputerUseAction::button)
                .containsExactly(TurComputerUseAction.Kind.CLICK, 10, 20, "left");

        assertThat(service.translateAction(Map.of("action", "double_click",
                "coordinate", List.of(5, 6))).kind())
                .isEqualTo(TurComputerUseAction.Kind.DOUBLE_CLICK);

        assertThat(service.translateAction(Map.of("action", "type", "text", "hello")))
                .extracting(TurComputerUseAction::kind, TurComputerUseAction::text)
                .containsExactly(TurComputerUseAction.Kind.TYPE, "hello");

        assertThat(service.translateAction(Map.of("action", "key", "text", "ctrl+a")).keys())
                .containsExactly("ctrl", "a");
    }

    @Test
    void translatesScrollDirectionToDeltas() {
        TurComputerUseAction down = service.translateAction(Map.of("action", "scroll",
                "coordinate", List.of(1, 2), "scroll_direction", "down", "scroll_amount", 4));
        assertThat(down.kind()).isEqualTo(TurComputerUseAction.Kind.SCROLL);
        assertThat(down.scrollY()).isEqualTo(4);
        assertThat(down.scrollX()).isZero();
    }

    @Test
    void translatesDragToStartAndEndWaypoints() {
        TurComputerUseAction drag = service.translateAction(Map.of("action", "left_click_drag",
                "start_coordinate", List.of(1, 2), "coordinate", List.of(8, 9)));
        assertThat(drag.kind()).isEqualTo(TurComputerUseAction.Kind.DRAG);
        assertThat(drag.path()).containsExactly(
                new TurComputerUseAction.Point(1, 2), new TurComputerUseAction.Point(8, 9));
    }

    @Test
    void unknownAndNullActionsFallBackToScreenshot() {
        assertThat(service.translateAction(Map.of("action", "cursor_position")).kind())
                .isEqualTo(TurComputerUseAction.Kind.SCREENSHOT);
        assertThat(service.translateAction(null).kind())
                .isEqualTo(TurComputerUseAction.Kind.SCREENSHOT);
        assertThat(service.translateAction(Map.of()).kind())
                .isEqualTo(TurComputerUseAction.Kind.SCREENSHOT);
    }

    @Test
    void parseConfigAppliesDefaultsAndOverrides() {
        TurAnthropicComputerUseService.Config defaults =
                service.parseConfig(new EnabledCapability(TurNativeCapability.ANTHROPIC_COMPUTER_USE, null));
        assertThat(defaults.width()).isEqualTo(TurAnthropicComputerUseService.DEFAULT_WIDTH);
        assertThat(defaults.height()).isEqualTo(TurAnthropicComputerUseService.DEFAULT_HEIGHT);
        assertThat(defaults.model()).isEqualTo(TurAnthropicComputerUseService.DEFAULT_MODEL);
        assertThat(defaults.maxTokens()).isEqualTo(TurAnthropicComputerUseService.DEFAULT_MAX_TOKENS);

        TurAnthropicComputerUseService.Config custom = service.parseConfig(
                new EnabledCapability(TurNativeCapability.ANTHROPIC_COMPUTER_USE,
                        "{\"model\":\"claude-x\",\"displayWidth\":1440,\"displayHeight\":900,"
                                + "\"maxIterations\":5,\"maxTokens\":8192,\"environment\":\"ubuntu\"}"));
        assertThat(custom.model()).isEqualTo("claude-x");
        assertThat(custom.width()).isEqualTo(1440);
        assertThat(custom.height()).isEqualTo(900);
        assertThat(custom.maxIterations()).isEqualTo(5);
        assertThat(custom.maxTokens()).isEqualTo(8192L);
        assertThat(custom.environment()).isEqualTo(TurComputerUseEnvironment.UBUNTU);
    }

    private static TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-anthropic");
        return instance;
    }
}
