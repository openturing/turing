/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.mcp.TurMcpServer;

/**
 * Tests for {@link TurMcpInstructionsProvider}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurMcpInstructionsProviderTest {

    private final TurMcpInstructionsProvider provider = new TurMcpInstructionsProvider();

    private static TurMcpServer server(String title, String instructions, int enabled) {
        TurMcpServer s = new TurMcpServer();
        s.setTitle(title);
        s.setLlmInstructions(instructions);
        s.setEnabled(enabled);
        return s;
    }

    @Test
    void nullSetReturnsEmpty() {
        assertThat(provider.buildSystemPromptBlock(null)).isEmpty();
    }

    @Test
    void emptySetReturnsEmpty() {
        assertThat(provider.buildSystemPromptBlock(Set.of())).isEmpty();
    }

    @Test
    void enabledServerWithInstructionsContributes() {
        String block = provider.buildSystemPromptBlock(
                Set.of(server("Weather", "Use this for forecasts.", 1)));

        assertThat(block)
                .contains(TurMcpInstructionsProvider.BLOCK_HEADER)
                .contains("## Weather")
                .contains("Use this for forecasts.");
    }

    @Test
    void disabledServerIsExcluded() {
        assertThat(provider.buildSystemPromptBlock(
                Set.of(server("Weather", "Use this for forecasts.", 0))))
                .isEmpty();
    }

    @Test
    void blankAndNullInstructionsAreExcluded() {
        Set<TurMcpServer> servers = new LinkedHashSet<>();
        servers.add(server("Blank", "   ", 1));
        servers.add(server("Null", null, 1));
        assertThat(provider.buildSystemPromptBlock(servers)).isEmpty();
    }

    @Test
    void instructionsAreTrimmedAndOrderedByTitle() {
        Set<TurMcpServer> servers = new LinkedHashSet<>();
        servers.add(server("Zeta", "  zeta guidance  ", 1));
        servers.add(server("Alpha", "alpha guidance", 1));

        String block = provider.buildSystemPromptBlock(servers);

        // Alpha precedes Zeta regardless of insertion order.
        assertThat(block.indexOf("## Alpha")).isLessThan(block.indexOf("## Zeta"));
        // Surrounding whitespace is trimmed off the instruction body.
        assertThat(block).contains("## Zeta\nzeta guidance");
    }

    @Test
    void onlyContributingServersSurviveAMixedSet() {
        Set<TurMcpServer> servers = new LinkedHashSet<>();
        servers.add(server("Enabled", "keep me", 1));
        servers.add(server("Disabled", "drop me", 0));
        servers.add(server("Blank", "  ", 1));

        String block = provider.buildSystemPromptBlock(servers);

        assertThat(block).contains("## Enabled").contains("keep me");
        assertThat(block).doesNotContain("drop me").doesNotContain("## Disabled").doesNotContain("## Blank");
    }
}
