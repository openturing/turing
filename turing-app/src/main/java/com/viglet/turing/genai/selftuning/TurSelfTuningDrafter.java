/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.selftuning;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T447 / §XXIII.6 — drafts a revised system prompt for an agent via a single
 * (non-streaming) LLM call, given the agent's current prompt and a digest of
 * recent failures. Resolves the agent's enabled LLM, else the global default.
 * Fail-open: returns empty when no LLM is usable or the call fails.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSelfTuningDrafter {

    private static final String JUDGE_SYSTEM = """
            You are a senior prompt engineer improving a chat agent's system prompt.
            Given the CURRENT prompt and a digest of recent FAILED conversations,
            rewrite the system prompt to address those failures while preserving the
            agent's purpose, tone and constraints. Make the smallest effective change.
            Output ONLY the revised system prompt — no preamble, no code fences.""";

    private final TurLlmModelFactory llmModelFactory;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSecretCryptoService secretCryptoService;
    private final TurGlobalSettingsService globalSettingsService;

    public TurSelfTuningDrafter(TurLlmModelFactory llmModelFactory,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSecretCryptoService secretCryptoService,
            TurGlobalSettingsService globalSettingsService) {
        this.llmModelFactory = llmModelFactory;
        this.llmInstanceRepository = llmInstanceRepository;
        this.secretCryptoService = secretCryptoService;
        this.globalSettingsService = globalSettingsService;
    }

    /** A revised system prompt, or empty if undraftable. */
    public Optional<String> draftSystemPrompt(TurAIAgent agent, String failureDigest) {
        TurLLMInstance llm = resolveLlm(agent);
        if (llm == null) {
            return Optional.empty();
        }
        String current = agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt();
        String user = """
                CURRENT SYSTEM PROMPT:
                %s

                RECENT FAILURES:
                %s

                Return the improved system prompt only.""".formatted(current, failureDigest);
        try {
            String apiKey = secretCryptoService.decrypt(llm.getApiKeyEncrypted());
            ChatModel model = llmModelFactory.createChatModel(llm, apiKey);
            String reply = model.call(new Prompt(List.of(
                    new SystemMessage(JUDGE_SYSTEM), new UserMessage(user))))
                    .getResult().getOutput().getText();
            if (reply == null || reply.isBlank()) {
                return Optional.empty();
            }
            String trimmed = reply.strip();
            // Discard a no-op (identical to current after trimming).
            return trimmed.equals(current.strip()) ? Optional.empty() : Optional.of(trimmed);
        } catch (RuntimeException e) {
            log.warn("[SelfTuning] draft failed for agent={}: {}", agent.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private TurLLMInstance resolveLlm(TurAIAgent agent) {
        if (agent.getLlmInstances() != null) {
            Optional<TurLLMInstance> agentLlm = agent.getLlmInstances().stream()
                    .filter(l -> l.getEnabled() == 1).findFirst();
            if (agentLlm.isPresent()) {
                return agentLlm.get();
            }
        }
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (defaultLlmId == null || defaultLlmId.isBlank()) {
            return null;
        }
        return llmInstanceRepository.findById(defaultLlmId)
                .filter(l -> l.getEnabled() == 1)
                .orElse(null);
    }
}
