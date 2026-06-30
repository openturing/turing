/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.resilience.llm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T518 / §XXVIII.14 — builds the cost-aware meta {@link ChatModel}: wraps a
 * primary model with an opt-in cross-provider <em>fallback chain</em> so a
 * provider error / rate-limit / timeout fails over to the next vendor, and
 * (in {@link TurLlmFallbackMode#CHEAPEST} mode) the cheapest-capable instance
 * serves first.
 *
 * <p>The chain is a Global-Settings list of {@code TurLLMInstance} ids
 * ({@code GLOBAL_LLM_FALLBACK_CHAIN}); when it is empty the primary model is
 * returned unchanged — so the legacy single-model path is byte-for-byte
 * preserved until an admin opts in. Composes with the existing per-instance
 * resilience pipeline (each candidate is a {@link TurResilientChatModel}, so the
 * meta layer only fails over once a candidate has exhausted its own retries) and
 * with the T291 budget downgrade (the primary handed in here is already
 * budget-resolved).
 *
 * <p>Cost attribution rides {@link TurFallbackChatModel#lastServedInstanceId()}
 * and the existing per-tenant token-usage recording, so a failover answer's cost
 * lands on the vendor that actually served it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurMetaChatModelResolver {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLlmModelFactory llmModelFactory;
    private final TurLLMPriceService priceService;

    public TurMetaChatModelResolver(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSecretCryptoService secretCryptoService,
            TurLlmModelFactory llmModelFactory,
            TurLLMPriceService priceService) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.secretCryptoService = secretCryptoService;
        this.llmModelFactory = llmModelFactory;
        this.priceService = priceService;
    }

    /**
     * Wraps {@code primaryModel} (already built for {@code primary}) with the
     * configured fallback chain. Returns {@code primaryModel} unchanged when no
     * chain is configured or none of its instances resolve to a usable candidate.
     *
     * @param primary      the budget-resolved primary instance
     * @param primaryModel the resilience-wrapped model already built for it
     */
    public ChatModel wrapWithFallback(TurLLMInstance primary, ChatModel primaryModel) {
        if (primary == null || primaryModel == null) {
            return primaryModel;
        }
        List<String> chainIds = globalSettingsService.getLlmFallbackChainIds();
        if (chainIds.isEmpty()) {
            return primaryModel;
        }
        try {
            List<TurLLMInstance> ordered = orderedInstances(primary);
            if (ordered.size() < 2) {
                return primaryModel;
            }
            List<TurFallbackChatModel.Candidate> candidates = new ArrayList<>(ordered.size());
            for (TurLLMInstance instance : ordered) {
                ChatModel model = instance.getId().equals(primary.getId())
                        ? primaryModel
                        : buildModel(instance);
                if (model != null) {
                    candidates.add(new TurFallbackChatModel.Candidate(instance.getId(), model));
                }
            }
            if (candidates.size() < 2) {
                return primaryModel;
            }
            log.info("[Meta] fallback chain active ({} candidates, mode {})",
                    candidates.size(), globalSettingsService.getLlmFallbackMode());
            return new TurFallbackChatModel(candidates);
        } catch (RuntimeException e) {
            log.warn("[Meta] failed to build fallback chain ({}); using primary only", e.getMessage());
            return primaryModel;
        }
    }

    /**
     * The ordered, de-duplicated candidate instances: the primary plus the
     * configured chain (enabled instances only). {@code PRIORITY} keeps the
     * primary first then the chain's config order; {@code CHEAPEST} sorts the
     * whole set by ascending price.
     */
    private List<TurLLMInstance> orderedInstances(TurLLMInstance primary) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(primary.getId());
        ids.addAll(globalSettingsService.getLlmFallbackChainIds());

        List<TurLLMInstance> instances = new ArrayList<>(ids.size());
        instances.add(primary);
        for (String id : ids) {
            if (id.equals(primary.getId())) {
                continue;
            }
            llmInstanceRepository.findById(id)
                    .filter(instance -> instance.getEnabled() == 1)
                    .ifPresent(instances::add);
        }
        if (globalSettingsService.getLlmFallbackMode() == TurLlmFallbackMode.CHEAPEST) {
            instances.sort(Comparator.comparingDouble(this::cheapness));
        }
        return instances;
    }

    /** Sum of input+output price-per-million as a cheapness proxy (0 = free/local). */
    private double cheapness(TurLLMInstance instance) {
        String vendorId = instance.getTurLLMVendor() != null ? instance.getTurLLMVendor().getId() : "";
        TurLLMPriceService.Rates rates = priceService.rates(vendorId, instance.getModelName());
        return rates.inputPerMillion() + rates.outputPerMillion();
    }

    /** Decrypts the key and builds a resilience-wrapped model for a chain instance. */
    private ChatModel buildModel(TurLLMInstance instance) {
        try {
            String apiKey = secretCryptoService.decrypt(instance.getApiKeyEncrypted());
            return llmModelFactory.createChatModel(instance, apiKey);
        } catch (RuntimeException e) {
            log.warn("[Meta] could not build fallback candidate {}: {}", instance.getId(), e.getMessage());
            return null;
        }
    }
}
