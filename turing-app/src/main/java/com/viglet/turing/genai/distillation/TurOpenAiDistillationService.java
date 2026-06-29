/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionListParams;
import com.openai.models.chat.completions.ChatCompletionStoreMessage;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FilePurpose;
import com.openai.models.finetuning.jobs.FineTuningJob;
import com.openai.models.finetuning.jobs.JobCreateParams;
import com.viglet.turing.genai.distillation.TurOpenAiDistillationJsonl.TrainingExample;
import com.viglet.turing.genai.distillation.TurOpenAiDistillationJsonl.TrainingMessage;
import com.viglet.turing.genai.eval.TurAgentEvalRunnerService;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalReportDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.distillation.TurDistillationJob;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.distillation.TurDistillationJobRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * F.9 / §X.10.c — T169. The production self-improvement loop's distillation
 * stage: export an agent's Stored Completions (T167), submit them as an OpenAI
 * fine-tuning job, and — driven by {@link TurDistillationJobPoller} once the
 * fine-tune finishes — run the agent eval gate (T286) against the fine-tuned
 * candidate and swap the agent's LLM instance model only if it passes.
 *
 * <p>Strictly opt-in and fail-open: {@link #distill} does nothing unless
 * {@code turing.distillation.enabled=true} and the agent's eval LLM is
 * OpenAI-backed; any missing precondition (no client, too few examples, API
 * error) returns a {@link DistillResult} explaining why, and never throws into
 * the caller.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurOpenAiDistillationService {

    private final TurNativeProviderClient nativeClient;
    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurConfigProperties configProperties;
    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurDistillationJobRepository jobRepository;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurAgentEvalRunnerService evalRunnerService;

    public TurOpenAiDistillationService(TurNativeProviderClient nativeClient,
            TurGenAiLlmProviderFactory providerFactory,
            TurConfigProperties configProperties,
            TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurDistillationJobRepository jobRepository,
            TurGlobalSettingsService globalSettingsService,
            TurAgentEvalRunnerService evalRunnerService) {
        this.nativeClient = nativeClient;
        this.providerFactory = providerFactory;
        this.configProperties = configProperties;
        this.agentRepository = agentRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.jobRepository = jobRepository;
        this.globalSettingsService = globalSettingsService;
        this.evalRunnerService = evalRunnerService;
    }

    /** Outcome of a distill request: the job when started, or a reason when not. */
    public record DistillResult(boolean started, String reason, TurDistillationJob job) {
        static DistillResult notStarted(String reason) {
            return new DistillResult(false, reason, null);
        }
    }

    /**
     * Export the agent's Stored Completions, build a fine-tuning training file,
     * and submit a fine-tune job. The {@link TurDistillationJobPoller} later runs
     * the eval gate and swaps the model. Returns a {@link DistillResult} that the
     * API surfaces (started + job, or not-started + reason).
     */
    public DistillResult distill(String agentId) {
        if (!configProperties.getDistillation().isEnabled()) {
            return DistillResult.notStarted("Distillation tier disabled (turing.distillation.enabled)");
        }
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return DistillResult.notStarted("Agent not found: " + agentId);
        }
        TurLLMInstance instance = resolveLlm(agent);
        if (instance == null || !isOpenAi(instance)) {
            return DistillResult.notStarted("Agent's eval LLM is missing or not OpenAI-backed");
        }
        Optional<OpenAIClient> client = nativeClient.openAi(instance);
        if (client.isEmpty()) {
            return DistillResult.notStarted("No usable native OpenAI client for the instance");
        }
        try {
            List<TrainingExample> examples = exportStoredCompletions(client.get(), agentId);
            long usable = TurOpenAiDistillationJsonl.usableCount(examples);
            int min = configProperties.getDistillation().getMinExamples();
            if (usable < min) {
                return DistillResult.notStarted("Not enough stored completions to distill ("
                        + usable + " usable, need " + min + "). Enable stored completions (T167) "
                        + "on the agent and let it accumulate traffic.");
            }
            String jsonl = TurOpenAiDistillationJsonl.build(examples);
            String baseModel = configProperties.getDistillation().getBaseModel();
            String fileId = uploadTrainingFile(client.get(), jsonl);
            String jobId = createFineTuneJob(client.get(), baseModel, fileId);

            TurDistillationJob job = new TurDistillationJob();
            job.setAgentId(agentId);
            job.setInstanceId(instance.getId());
            job.setBaseModel(baseModel);
            job.setTrainingFileId(fileId);
            job.setFineTuneJobId(jobId);
            job.setExampleCount((int) usable);
            job.setState(TurDistillationState.RUNNING);
            job = jobRepository.save(job);
            log.info("[Distill] agent '{}' submitted fine-tune job '{}' over {} example(s) (base '{}')",
                    agentId, jobId, usable, baseModel);
            return new DistillResult(true, null, job);
        } catch (RuntimeException e) {
            log.warn("[Distill] agent '{}' distillation failed: {}", agentId, e.getMessage());
            return DistillResult.notStarted("Distillation submit failed: " + e.getMessage());
        }
    }

    /**
     * Drive every RUNNING job to completion (invoked by the poller). When a
     * fine-tune succeeds, the eval gate is run against the candidate and the
     * instance model is swapped only if it passes; otherwise the original model
     * is restored. Each job is isolated — one failure never blocks the rest.
     */
    public void pollPending() {
        if (!configProperties.getDistillation().isEnabled()) {
            return;
        }
        List<TurDistillationJob> running =
                jobRepository.findByStateOrderByCreatedAtAsc(TurDistillationState.RUNNING);
        for (TurDistillationJob job : running) {
            try {
                pollOne(job);
            } catch (RuntimeException e) {
                log.warn("[Distill] failed to poll job {} (ft {}): {}", job.getId(),
                        job.getFineTuneJobId(), e.getMessage());
            }
        }
    }

    private void pollOne(TurDistillationJob job) {
        TurLLMInstance instance = llmInstanceRepository.findById(job.getInstanceId()).orElse(null);
        Optional<OpenAIClient> client = instance == null
                ? Optional.empty() : nativeClient.openAi(instance);
        if (instance == null || client.isEmpty()) {
            log.info("[Distill] job {} instance/client unavailable — leaving RUNNING", job.getId());
            return;
        }
        FineTuningJob ft = client.get().fineTuning().jobs().retrieve(job.getFineTuneJobId());
        job.setLastPolledAt(Instant.now());
        String status = ft.status().asString().toLowerCase(Locale.ROOT);
        switch (status) {
            case "succeeded" -> onFineTuneSucceeded(job, instance, ft.fineTunedModel().orElse(null));
            case "failed", "cancelled" -> finishFailed(job, "Fine-tune ended: " + status);
            default -> jobRepository.save(job); // still running — just record lastPolledAt
        }
    }

    /**
     * The fine-tune produced a candidate model. Swap it in, run the eval gate,
     * and keep it only if the gate passes; otherwise revert to the original
     * model. The swap-before-eval ordering is deliberate: the eval must run
     * against the candidate, not the incumbent.
     */
    private void onFineTuneSucceeded(TurDistillationJob job, TurLLMInstance instance,
            String fineTunedModel) {
        if (!StringUtils.hasText(fineTunedModel)) {
            finishFailed(job, "Fine-tune succeeded but no fine_tuned_model returned");
            return;
        }
        String originalModel = instance.getModelName();
        job.setOriginalModel(originalModel);
        job.setFineTunedModel(fineTunedModel);
        instance.setModelName(fineTunedModel);
        llmInstanceRepository.save(instance);

        TurAgentEvalReportDto report = evalRunnerService.runAgent(job.getAgentId());
        boolean passed = report != null && report.passed();
        if (passed) {
            job.setState(TurDistillationState.SWAPPED);
            log.info("[Distill] job {} eval PASSED — swapped instance '{}' model '{}' -> '{}'",
                    job.getId(), instance.getId(), originalModel, fineTunedModel);
        } else {
            instance.setModelName(originalModel);
            llmInstanceRepository.save(instance);
            job.setState(TurDistillationState.EVAL_FAILED);
            job.setErrorMessage("Eval gate did not pass on the fine-tuned candidate — model reverted");
            log.info("[Distill] job {} eval FAILED — reverted instance '{}' model to '{}'",
                    job.getId(), instance.getId(), originalModel);
        }
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
    }

    private void finishFailed(TurDistillationJob job, String error) {
        job.setState(TurDistillationState.FAILED);
        job.setErrorMessage(error);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        log.info("[Distill] job {} marked FAILED: {}", job.getId(), error);
    }

    // ─────────────────────────── OpenAI export / submit ───────────────────────────

    /**
     * List the agent's Stored Completions (filtered by the {@code agentId}
     * metadata T167 writes) and reconstruct each into a {@link TrainingExample}
     * (input turns + the assistant answer). Capped at {@code maxExamples}.
     */
    private List<TrainingExample> exportStoredCompletions(OpenAIClient client, String agentId) {
        ChatCompletionListParams params = ChatCompletionListParams.builder()
                .metadata(ChatCompletionListParams.Metadata.builder()
                        .putAdditionalProperty("agentId", agentId)
                        .build())
                .limit((long) configProperties.getDistillation().getMaxExamples())
                .build();
        List<TrainingExample> examples = new ArrayList<>();
        int max = configProperties.getDistillation().getMaxExamples();
        for (ChatCompletion completion : client.chat().completions().list(params).autoPager()) {
            examples.add(toExample(client, completion));
            if (examples.size() >= max) {
                break;
            }
        }
        return examples;
    }

    private TrainingExample toExample(OpenAIClient client, ChatCompletion completion) {
        List<TrainingMessage> messages = new ArrayList<>();
        // Input turns come from the stored completion's messages sub-resource;
        // skip the stored assistant turn here — the canonical answer is taken
        // from the completion's choice below (so it is never double-counted).
        for (ChatCompletionStoreMessage message
                : client.chat().completions().messages().list(completion.id()).data()) {
            String role = safeRole(message);
            String content = message.content().orElse(null);
            if (!"assistant".equals(role) && StringUtils.hasText(content)) {
                messages.add(new TrainingMessage(role, content));
            }
        }
        if (!completion.choices().isEmpty()) {
            String answer = completion.choices().get(0).message().content().orElse(null);
            if (StringUtils.hasText(answer)) {
                messages.add(new TrainingMessage("assistant", answer));
            }
        }
        return new TrainingExample(messages);
    }

    private String uploadTrainingFile(OpenAIClient client, String jsonl) {
        return client.files().create(FileCreateParams.builder()
                .file(jsonl.getBytes(StandardCharsets.UTF_8))
                .purpose(FilePurpose.FINE_TUNE)
                .build()).id();
    }

    private String createFineTuneJob(OpenAIClient client, String baseModel, String fileId) {
        return client.fineTuning().jobs().create(JobCreateParams.builder()
                .model(baseModel)
                .trainingFile(fileId)
                .build()).id();
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** Role of a stored message; {@code _role()} is an untyped discriminator JsonValue. */
    private static String safeRole(ChatCompletionStoreMessage message) {
        try {
            String role = message._role().convert(String.class);
            return StringUtils.hasText(role) ? role : "user";
        } catch (RuntimeException e) {
            return "user";
        }
    }

    private TurLLMInstance resolveLlm(TurAIAgent agent) {
        if (agent.getLlmInstances() != null) {
            Optional<TurLLMInstance> agentLlm = agent.getLlmInstances().stream()
                    .filter(l -> l.getEnabled() == 1)
                    .findFirst();
            if (agentLlm.isPresent()) {
                return agentLlm.get();
            }
        }
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(defaultLlmId)) {
            return null;
        }
        return llmInstanceRepository.findById(defaultLlmId)
                .filter(l -> l.getEnabled() == 1)
                .orElse(null);
    }

    private boolean isOpenAi(TurLLMInstance instance) {
        try {
            return "openai".equals(providerFactory.getProvider(instance)
                    .getPluginType().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
