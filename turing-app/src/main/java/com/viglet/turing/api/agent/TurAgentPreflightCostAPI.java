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
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * F.7 / §X.8.g — pre-flight cost quote for a chat turn (T162).
 *
 * <p>When an agent has a {@code perTurnSoftCapUsd} configured, the chat UI calls
 * this before sending: it counts the draft prompt's input tokens (exact via
 * T161 where the vendor supports it), prices the turn against the model's rate
 * (T290 price table, assuming a configured/expected output budget), and reports
 * whether the projected cost breaches the soft cap. The SDK then shows a
 * "this turn will cost ~$X.XX — send anyway?" gate. Purely advisory: when no cap
 * is set the response is {@code enabled:false} and the UI sends without a prompt.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/preflight-cost")
@Tag(name = "Agent Pre-flight Cost", description = "F.7 §X.8.g — per-turn cost quote gate")
public class TurAgentPreflightCostAPI {

    /** Fallback expected output tokens when the instance has no numPredict. */
    private static final int DEFAULT_OUTPUT_TOKENS = 500;

    private final TurAIAgentRepository agentRepository;
    private final TurTokenCountingService tokenCountingService;
    private final TurLLMPriceService priceService;

    public TurAgentPreflightCostAPI(TurAIAgentRepository agentRepository,
            TurTokenCountingService tokenCountingService,
            TurLLMPriceService priceService) {
        this.agentRepository = agentRepository;
        this.tokenCountingService = tokenCountingService;
        this.priceService = priceService;
    }

    public record PreflightRequest(String text) {
    }

    public record PreflightQuote(
            boolean enabled,
            int inputTokens,
            int estimatedOutputTokens,
            double estimatedCostUsd,
            Double softCapUsd,
            boolean overSoftCap,
            boolean exact) {

        static PreflightQuote disabled() {
            return new PreflightQuote(false, 0, 0, 0d, null, false, false);
        }
    }

    @Operation(summary = "Quote the projected cost of the next turn (gates on the agent's soft cap)")
    @PostMapping
    public ResponseEntity<PreflightQuote> quote(@PathVariable String agentId,
            @RequestBody PreflightRequest request) {
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return ResponseEntity.notFound().build();
        }
        Double softCap = agent.getPerTurnSoftCapUsd();
        TurLLMInstance instance = firstEnabledInstance(agent);
        if (softCap == null || softCap <= 0 || instance == null) {
            return ResponseEntity.ok(PreflightQuote.disabled());
        }

        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(agent.getSystemPrompt())) {
            messages.add(new SystemMessage(agent.getSystemPrompt()));
        }
        messages.add(new UserMessage(request == null || request.text() == null ? "" : request.text()));

        boolean exact = tokenCountingService.isExactAvailable(instance);
        int inputTokens = tokenCountingService.count(instance, messages);
        int outputTokens = instance.getNumPredict() != null && instance.getNumPredict() > 0
                ? instance.getNumPredict()
                : DEFAULT_OUTPUT_TOKENS;

        String vendorId = instance.getTurLLMVendor() == null ? null : instance.getTurLLMVendor().getId();
        double cost = priceService.computeCost(vendorId, instance.getModelName(),
                inputTokens, outputTokens);

        return ResponseEntity.ok(new PreflightQuote(true, inputTokens, outputTokens, cost,
                softCap, cost > softCap, exact));
    }

    private TurLLMInstance firstEnabledInstance(TurAIAgent agent) {
        if (agent.getLlmInstances() == null) {
            return null;
        }
        return agent.getLlmInstances().stream()
                .filter(i -> i != null && i.getEnabled() == 1)
                .findFirst()
                .orElse(null);
    }
}
