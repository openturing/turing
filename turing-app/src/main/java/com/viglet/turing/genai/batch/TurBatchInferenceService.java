/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.viglet.turing.persistence.model.batch.TurBatchJob;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.batch.TurBatchJobRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.properties.TurConfigProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * F.7 / §X.8.a — the unified, vendor-neutral entry point to the Batch tier.
 *
 * <p>Wraps the {@link TurBatchProvider} seam (OpenAI Batch API + Anthropic
 * Message Batches, both at the 50% discount) with the durable {@link TurBatchJob}
 * registry so callers get fire-and-forget submission: {@link #submit} returns a
 * persisted job immediately, the {@code TurBatchJobPoller} drives it to
 * completion, and the workload's {@link TurBatchCompletionHandler} consumes the
 * results up to 24h later. Callers never block.
 *
 * <p>Every method is a no-op-friendly fallback: when the tier is
 * {@link #isEnabled() disabled}, the instance's vendor has no batch API, or the
 * request list is empty, {@link #submit} returns {@link Optional#empty()} and the
 * caller runs its existing synchronous path — so the feature is purely additive.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurBatchInferenceService {

    private final TurBatchProviderFactory providerFactory;
    private final TurBatchJobRepository batchJobRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurConfigProperties configProperties;

    public TurBatchInferenceService(TurBatchProviderFactory providerFactory,
            TurBatchJobRepository batchJobRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurConfigProperties configProperties) {
        this.providerFactory = providerFactory;
        this.batchJobRepository = batchJobRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.configProperties = configProperties;
    }

    /** True when the Batch tier is switched on ({@code turing.batch.enabled}). */
    public boolean isEnabled() {
        return configProperties.getBatch().isEnabled();
    }

    /** True when the instance's vendor exposes a Batch API (OpenAI / Anthropic). */
    public boolean isSupported(TurLLMInstance instance) {
        return providerFactory.getProvider(instance).isPresent();
    }

    /**
     * Submit a chat batch and persist a tracking job. Returns empty (and submits
     * nothing) when the tier is disabled, the vendor has no batch API, or there
     * is nothing to send — the caller then falls back to its synchronous path.
     */
    public Optional<TurBatchJob> submit(TurLLMInstance instance, String purpose,
            List<TurBatchChatRequest> requests, String contextJson) {
        if (!isEnabled() || instance == null || CollectionUtils.isEmpty(requests)) {
            return Optional.empty();
        }
        Optional<TurBatchProvider> provider = providerFactory.getProvider(instance);
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        int max = configProperties.getBatch().getMaxRequestsPerBatch();
        if (requests.size() > max) {
            log.warn("[Batch] purpose '{}' has {} requests, exceeding cap {} — not submitting",
                    purpose, requests.size(), max);
            return Optional.empty();
        }
        try {
            String vendorBatchId = provider.get().submitChat(instance, requests);
            return Optional.of(persistJob(instance, provider.get().getPluginType(), vendorBatchId,
                    purpose, TurBatchKind.CHAT, requests.size(), contextJson));
        } catch (Exception e) {
            log.error("[Batch] failed to submit '{}' batch for instance {}",
                    purpose, instance.getId(), e);
            return Optional.empty();
        }
    }

    /** True when the instance's vendor exposes an embeddings Batch API (OpenAI / Gemini). */
    public boolean isEmbeddingSupported(TurLLMInstance instance) {
        return providerFactory.getEmbeddingProvider(instance).isPresent();
    }

    /**
     * Submit an embeddings batch (T158) and persist an {@link TurBatchKind#EMBEDDING}
     * tracking job. Same opt-in/fallback semantics as {@link #submit}.
     */
    public Optional<TurBatchJob> submitEmbeddings(TurLLMInstance instance, String purpose,
            List<TurBatchEmbeddingRequest> requests, String contextJson) {
        if (!isEnabled() || instance == null || CollectionUtils.isEmpty(requests)) {
            return Optional.empty();
        }
        Optional<TurBatchEmbeddingProvider> provider = providerFactory.getEmbeddingProvider(instance);
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        int max = configProperties.getBatch().getMaxRequestsPerBatch();
        if (requests.size() > max) {
            log.warn("[Batch] embeddings purpose '{}' has {} requests, exceeding cap {} — not submitting",
                    purpose, requests.size(), max);
            return Optional.empty();
        }
        try {
            String vendorBatchId = provider.get().submitEmbeddings(instance, requests);
            return Optional.of(persistJob(instance, provider.get().getPluginType(), vendorBatchId,
                    purpose, TurBatchKind.EMBEDDING, requests.size(), contextJson));
        } catch (Exception e) {
            log.error("[Batch] failed to submit embeddings '{}' batch for instance {}",
                    purpose, instance.getId(), e);
            return Optional.empty();
        }
    }

    /** Fetch the per-chunk embedding vectors of a completed embeddings job. */
    public List<TurBatchEmbeddingResult> fetchEmbeddingResults(TurBatchJob job) {
        TurLLMInstance instance = resolveInstance(job);
        TurBatchEmbeddingProvider provider = providerFactory.getEmbeddingProvider(instance)
                .orElseThrow(() -> new IllegalStateException(
                        "No embedding batch provider for instance " + instance.getId()));
        return provider.embeddingResults(instance, job.getVendorBatchId());
    }

    private TurBatchJob persistJob(TurLLMInstance instance, String pluginType, String vendorBatchId,
            String purpose, TurBatchKind kind, int total, String contextJson) {
        TurBatchJob job = new TurBatchJob();
        job.setInstanceId(instance.getId());
        job.setPluginType(pluginType);
        job.setVendorBatchId(vendorBatchId);
        job.setPurpose(purpose);
        job.setJobKind(kind);
        job.setState(TurBatchState.VALIDATING);
        job.setTotalRequests(total);
        job.setContextJson(contextJson);
        return batchJobRepository.save(job);
    }

    /** Refresh a job's status from the vendor and persist the new counts/state. */
    public TurBatchStatus refresh(TurBatchJob job) {
        TurLLMInstance instance = resolveInstance(job);
        TurBatchProvider provider = resolveProvider(instance);
        TurBatchStatus status = provider.status(instance, job.getVendorBatchId());
        job.setState(status.state());
        job.setTotalRequests(status.total());
        job.setCompletedRequests(status.completed());
        job.setFailedRequests(status.failed());
        job.setLastPolledAt(java.time.Instant.now());
        batchJobRepository.save(job);
        return status;
    }

    /** Fetch the per-request results of a completed job. */
    public List<TurBatchChatResult> fetchResults(TurBatchJob job) {
        TurLLMInstance instance = resolveInstance(job);
        TurBatchProvider provider = resolveProvider(instance);
        return provider.results(instance, job.getVendorBatchId());
    }

    /** Best-effort cancel of an in-flight job. */
    public void cancel(TurBatchJob job) {
        TurLLMInstance instance = resolveInstance(job);
        resolveProvider(instance).cancel(instance, job.getVendorBatchId());
    }

    private TurLLMInstance resolveInstance(TurBatchJob job) {
        return llmInstanceRepository.findById(job.getInstanceId())
                .orElseThrow(() -> new IllegalStateException(
                        "LLM instance " + job.getInstanceId() + " gone for batch job " + job.getId()));
    }

    private TurBatchProvider resolveProvider(TurLLMInstance instance) {
        return providerFactory.getProvider(instance).orElseThrow(() -> new IllegalStateException(
                "No batch provider for instance " + instance.getId()));
    }
}
