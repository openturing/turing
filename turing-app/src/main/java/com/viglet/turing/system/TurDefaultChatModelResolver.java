/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system;

import java.util.Optional;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.lane.TurModelLaneService;
import com.viglet.turing.system.lane.TurModelStage;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort resolver for the platform's <strong>default</strong> chat model
 * (the LLM selected in Global Settings), decrypting its API key and building a
 * resilience-wrapped {@link ChatModel} the same way the summary / authoring
 * paths do.
 *
 * <p>Introduced for T389 / §XX.9 so the public Semantic Navigation ranking
 * pipeline can drive the LLM rerank strategy without re-implementing the
 * lookup-decrypt-build dance inline. It is <strong>fail-open</strong>: any
 * problem (no default configured, instance missing/disabled, decrypt or factory
 * failure) returns {@link Optional#empty()} and is logged at DEBUG, never thrown
 * — callers that can't get a model simply skip the LLM-dependent step.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurDefaultChatModelResolver {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLlmModelFactory llmModelFactory;
    private final TurModelLaneService modelLaneService;
    private final com.viglet.turing.resilience.llm.TurMetaChatModelResolver metaChatModelResolver;

    public TurDefaultChatModelResolver(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSecretCryptoService secretCryptoService,
            TurLlmModelFactory llmModelFactory,
            TurModelLaneService modelLaneService,
            com.viglet.turing.resilience.llm.TurMetaChatModelResolver metaChatModelResolver) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.secretCryptoService = secretCryptoService;
        this.llmModelFactory = llmModelFactory;
        this.modelLaneService = modelLaneService;
        this.metaChatModelResolver = metaChatModelResolver;
    }

    /**
     * @return the default chat model, or {@link Optional#empty()} when none is
     *         configured/enabled or it cannot be built.
     */
    public Optional<ChatModel> resolve() {
        return buildFor(globalSettingsService.getDefaultLlmId());
    }

    /**
     * T517 / §XXVIII.13 — resolves the chat model for a specific pipeline
     * {@code stage}, honouring its cross-provider model "lane" binding (falling
     * back to the default LLM when the lane is unbound or its instance is
     * missing/disabled). Same fail-open contract as {@link #resolve()}.
     *
     * @return the lane-resolved chat model, or {@link Optional#empty()} when it
     *         cannot be built.
     */
    public Optional<ChatModel> resolve(TurModelStage stage) {
        return buildFor(modelLaneService.resolveInstanceId(stage));
    }

    /** Looks up, decrypts and builds a resilience-wrapped model for {@code llmId}. */
    private Optional<ChatModel> buildFor(String llmId) {
        if (!StringUtils.hasText(llmId)) {
            return Optional.empty();
        }
        try {
            TurLLMInstance llmInstance = llmInstanceRepository.findById(llmId).orElse(null);
            if (llmInstance == null || llmInstance.getEnabled() != 1) {
                return Optional.empty();
            }
            String apiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel model = llmModelFactory.createChatModel(llmInstance, apiKey);
            // T518 — wrap with the cost-aware cross-provider fallback chain when
            // one is configured (no-op pass-through when empty).
            return Optional.ofNullable(metaChatModelResolver.wrapWithFallback(llmInstance, model));
        } catch (RuntimeException e) {
            log.debug("[LLM] chat model unavailable for instance {}: {}", llmId, e.getMessage());
            return Optional.empty();
        }
    }
}
