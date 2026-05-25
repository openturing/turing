/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurChatFlowRecipeDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowRecipeSummaryDto;
import com.viglet.turing.service.chatflow.TurChatFlowRecipeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T96 / §VII.11.f — read-only browser for the Turing Recipes marketplace.
 * Not agent-scoped: the same catalog is offered to every admin regardless of
 * which agent they eventually install into. Pairs with
 * {@code POST /api/ai-agent/{agentId}/chat-flow/install-recipe/{recipeId}}
 * (in {@link TurChatFlowAPI}) which actually creates the flows on a chosen
 * agent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/chat-flow-recipes")
@Tag(name = "Chat Flow Recipes", description = "Turing Recipes marketplace browser")
public class TurChatFlowRecipeAPI {

    private final TurChatFlowRecipeService chatFlowRecipeService;

    public TurChatFlowRecipeAPI(TurChatFlowRecipeService chatFlowRecipeService) {
        this.chatFlowRecipeService = chatFlowRecipeService;
    }

    @Operation(summary = "List every bundled chat-flow recipe (catalog metadata, no full bundle)")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurChatFlowRecipeSummaryDto> list() {
        return chatFlowRecipeService.list();
    }

    @Operation(summary = "Full recipe payload — used by the preview pane before install")
    @GetMapping("/{recipeId}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatFlowRecipeDto get(@PathVariable String recipeId) {
        return chatFlowRecipeService.get(recipeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Recipe '" + recipeId + "' not found"));
    }
}
