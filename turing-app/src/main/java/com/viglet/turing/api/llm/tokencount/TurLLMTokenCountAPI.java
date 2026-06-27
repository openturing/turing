/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.llm.tokencount;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.TurTokenCountingService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * F.7 / §X.8.f — pre-flight token-count endpoint (T161). Returns the prompt's
 * input-token count for a given LLM instance — exact via the vendor's
 * {@code count_tokens} endpoint when available (Anthropic), else the
 * {@code chars/4} heuristic. Powers the T162 pre-flight cost quote in the chat UI.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/llm/{id}/token-count")
@Tag(name = "LLM Token Count", description = "F.7 §X.8.f — pre-flight token estimator")
public class TurLLMTokenCountAPI {

    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurTokenCountingService tokenCountingService;

    public TurLLMTokenCountAPI(TurLLMInstanceRepository llmInstanceRepository,
            TurTokenCountingService tokenCountingService) {
        this.llmInstanceRepository = llmInstanceRepository;
        this.tokenCountingService = tokenCountingService;
    }

    public record TokenCountRequest(String systemPrompt, String text) {
    }

    public record TokenCountResponse(int inputTokens, boolean exact) {
    }

    @Operation(summary = "Pre-flight count the prompt's input tokens for an LLM instance")
    @PostMapping
    public ResponseEntity<TokenCountResponse> count(@PathVariable String id,
            @RequestBody TokenCountRequest request) {
        TurLLMInstance instance = llmInstanceRepository.findById(id).orElse(null);
        if (instance == null || request == null) {
            return ResponseEntity.notFound().build();
        }
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(new SystemMessage(request.systemPrompt()));
        }
        messages.add(new UserMessage(request.text() == null ? "" : request.text()));

        boolean exact = tokenCountingService.isExactAvailable(instance);
        int tokens = tokenCountingService.count(instance, messages);
        return ResponseEntity.ok(new TokenCountResponse(tokens, exact));
    }
}
