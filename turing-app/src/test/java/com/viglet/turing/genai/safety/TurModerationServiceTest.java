/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T182 / §X.14.b — unit coverage for the moderation pre-filter: opt-out + the
 * chunk-size gate + the chunk-drop filter. The OpenAI call itself is exercised
 * via a spy so the test never reaches the network.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurModerationServiceTest {

    @Mock private TurGlobalSettingsService globalSettingsService;
    @Mock private TurLLMInstanceRepository llmInstanceRepository;
    @Mock private TurGenAiLlmProviderFactory providerFactory;
    @Mock private TurNativeProviderClient nativeClient;

    private TurModerationService service(boolean enabled, int chunkMinChars) {
        return new TurModerationService(globalSettingsService, llmInstanceRepository,
                providerFactory, nativeClient, enabled, "omni-moderation-latest", chunkMinChars);
    }

    @Test
    void disabledIsAlwaysCleanAndNeverTouchesProviders() {
        TurModerationService service = service(false, 10);
        assertThat(service.isEnabled()).isFalse();
        assertThat(service.moderate("anything")).isEqualTo(TurModerationService.Verdict.clean());
        assertThat(service.shouldModerateChunk("a very long string here")).isFalse();
        // Filter is a pass-through when disabled.
        List<String> chunks = List.of("a", "b");
        assertThat(service.filterModeratedChunks(chunks, Function.identity())).isEqualTo(chunks);
    }

    @Test
    void chunkSizeGateRespectsMinChars() {
        TurModerationService service = service(true, 8);
        assertThat(service.shouldModerateChunk("short")).isFalse(); // 5 < 8
        assertThat(service.shouldModerateChunk("longenough")).isTrue(); // 10 >= 8
        assertThat(service.shouldModerateChunk(null)).isFalse();
    }

    @Test
    void filterDropsOnlyFlaggedLargeChunks() {
        TurModerationService service = spy(service(true, 5));
        // Stub the network call: the chunk containing "banned" is flagged.
        doReturn(new TurModerationService.Verdict(true, List.of("violence")))
                .when(service).moderate("this is banned content");
        doReturn(TurModerationService.Verdict.clean())
                .when(service).moderate("this is fine and safe");

        List<String> chunks = List.of(
                "tiny",                      // under min-chars → never moderated, kept
                "this is fine and safe",     // clean → kept
                "this is banned content");   // flagged → dropped

        List<String> kept = service.filterModeratedChunks(chunks, Function.identity());

        assertThat(kept).containsExactly("tiny", "this is fine and safe");
    }
}
