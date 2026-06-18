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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;

import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import lombok.extern.slf4j.Slf4j;

/**
 * T251 / §XIII.3 — publishes each configured Persona as a reusable MCP
 * <em>prompt</em>. One pick in the client's prompt menu and the customer's brand
 * voice + guardrails (tone, verbosity, mandatory/forbidden terms) come along —
 * the same persona the internal agent uses, now adoptable by any external AI
 * client.
 *
 * <p><b>Reuse, don't fork (§XIII.5)</b>: the prompt body is produced by the same
 * {@link TurPersonaPromptComposer} the chat runtime uses (its deterministic
 * static block — no few-shot, since there is no user query at prompt-fetch
 * time), so the MCP-exposed voice is byte-identical to the in-product voice.
 *
 * <p>The enabled personas are enumerated when this bean is built; a persona
 * added afterwards appears on the next restart (prompts are an admin-curated,
 * rarely-changing set). Gated on {@code spring.ai.mcp.server.enabled}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
// See TurMcpResourceConfig: the MCP SDK 2.0.0-RC1 marks several record
// constructors @Deprecated (pre-GA churn, not forRemoval); suppress here too.
@SuppressWarnings("deprecation")
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class TurMcpPromptConfig {

    @Bean
    List<SyncPromptSpecification> turingMcpPrompts(TurPersonaRepository personaRepository,
            TurPersonaPromptComposer personaPromptComposer) {
        List<SyncPromptSpecification> specs = new ArrayList<>();
        for (TurPersona persona : personaRepository.findAll()) {
            if (persona.getEnabled() != 1) {
                continue;
            }
            specs.add(personaPrompt(persona, personaPromptComposer));
        }
        log.info("[MCP] Turing MCP server publishing {} persona prompt(s)", specs.size());
        return specs;
    }

    private SyncPromptSpecification personaPrompt(TurPersona persona, TurPersonaPromptComposer composer) {
        String description = (persona.getDescription() != null && !persona.getDescription().isBlank())
                ? persona.getDescription()
                : "Adopt the \"" + persona.getName() + "\" brand voice.";
        Prompt prompt = new Prompt("persona-" + slug(persona.getName()), description, List.of());

        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            String composed = composer.compose(persona, null, null, null);
            String text = "Adopt the following persona for all of your responses:\n\n" + composed;
            return new GetPromptResult(description,
                    List.of(new PromptMessage(Role.USER, new TextContent(text))));
        });
    }

    private String slug(String name) {
        if (name == null || name.isBlank()) {
            return "persona";
        }
        String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
        return s.isBlank() ? "persona" : s;
    }
}
