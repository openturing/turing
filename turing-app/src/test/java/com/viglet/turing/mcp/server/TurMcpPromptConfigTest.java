/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;

import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Unit tests for {@link TurMcpPromptConfig} — the T251 persona→MCP-prompt mapping.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurMcpPromptConfigTest {

    @Mock
    private TurPersonaRepository personaRepository;
    @Mock
    private TurPersonaPromptComposer personaPromptComposer;

    private final TurMcpPromptConfig config = new TurMcpPromptConfig();

    private TurPersona persona(String name, String description, int enabled) {
        TurPersona p = new TurPersona();
        p.setName(name);
        p.setDescription(description);
        p.setEnabled(enabled);
        return p;
    }

    @Test
    void publishesOnePromptPerEnabledPersona_withComposedVoice() {
        TurPersona support = persona("Support Hero", "Friendly support voice", 1);
        when(personaRepository.findAll()).thenReturn(List.of(
                support,
                persona("Draft Voice", "n/a", 0)));
        when(personaPromptComposer.compose(eq(support), any(), any(), any()))
                .thenReturn("TONE: friendly\nVERBOSITY: 3");

        List<SyncPromptSpecification> specs =
                config.turingMcpPrompts(personaRepository, personaPromptComposer);

        assertEquals(1, specs.size(), "only the enabled persona should be published");
        SyncPromptSpecification spec = specs.get(0);
        assertEquals("persona-support-hero", spec.prompt().name());
        assertEquals("Friendly support voice", spec.prompt().description());

        // Fetching the prompt composes the persona's brand voice into a USER message.
        GetPromptResult result = spec.promptHandler().apply(null, null);
        assertEquals(1, result.messages().size());
        assertEquals(Role.USER, result.messages().get(0).role());
        TextContent content = (TextContent) result.messages().get(0).content();
        assertTrue(content.text().contains("Adopt the following persona"), content.text());
        assertTrue(content.text().contains("TONE: friendly"), content.text());
    }

    @Test
    void emptyWhenNoEnabledPersonas() {
        when(personaRepository.findAll()).thenReturn(List.of(persona("Draft", "n/a", 0)));
        assertTrue(config.turingMcpPrompts(personaRepository, personaPromptComposer).isEmpty());
    }
}
