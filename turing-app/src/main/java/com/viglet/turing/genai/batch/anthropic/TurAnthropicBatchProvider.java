/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.anthropic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchRequestCounts;
import com.anthropic.models.messages.batches.MessageBatchResult;
import com.anthropic.models.messages.batches.MessageBatchSucceededResult;
import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchChatResult;
import com.viglet.turing.genai.batch.TurBatchProvider;
import com.viglet.turing.genai.batch.TurBatchState;
import com.viglet.turing.genai.batch.TurBatchStatus;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * F.7 / §X.8.a — Anthropic Message Batches implementation of
 * {@link TurBatchProvider}.
 *
 * <p>Unlike OpenAI's file-based flow, Anthropic takes the requests inline:
 * {@code POST /v1/messages/batches} with a list of {@code {custom_id, params}}
 * entries, each {@code params} a normal Messages request (model, max_tokens,
 * system, messages). Results stream back as a JSONL of individual responses,
 * each carrying a per-request {@code succeeded / errored / canceled / expired}
 * outcome — the same 50%-discount tier as OpenAI Batch.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurAnthropicBatchProvider implements TurBatchProvider {

    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final long DEFAULT_MAX_TOKENS = 4096L;

    private final TurNativeProviderClient nativeProviderClient;

    public TurAnthropicBatchProvider(TurNativeProviderClient nativeProviderClient) {
        this.nativeProviderClient = nativeProviderClient;
    }

    @Override
    public String getPluginType() {
        return "anthropic";
    }

    @Override
    public String submitChat(TurLLMInstance instance, List<TurBatchChatRequest> requests) {
        AnthropicClient client = client(instance);
        String defaultModel = StringUtils.hasText(instance.getModelName())
                ? instance.getModelName()
                : DEFAULT_MODEL;

        BatchCreateParams.Builder builder = BatchCreateParams.builder();
        for (TurBatchChatRequest request : requests) {
            builder.addRequest(BatchCreateParams.Request.builder()
                    .customId(request.customId())
                    .params(buildParams(request, defaultModel))
                    .build());
        }

        MessageBatch batch = client.messages().batches().create(builder.build());
        log.info("[Batch][Anthropic] submitted batch {} ({} requests) for instance {}",
                batch.id(), requests.size(), instance.getId());
        return batch.id();
    }

    private BatchCreateParams.Request.Params buildParams(TurBatchChatRequest request,
            String defaultModel) {
        String model = StringUtils.hasText(request.model()) ? request.model() : defaultModel;
        long maxTokens = request.maxTokens() != null && request.maxTokens() > 0
                ? request.maxTokens().longValue()
                : DEFAULT_MAX_TOKENS;

        BatchCreateParams.Request.Params.Builder params = BatchCreateParams.Request.Params.builder()
                .model(model)
                .maxTokens(maxTokens)
                .addUserMessage(request.userPrompt() == null ? "" : request.userPrompt());
        if (StringUtils.hasText(request.systemPrompt())) {
            params.system(request.systemPrompt());
        }
        if (request.temperature() != null) {
            params.temperature(request.temperature());
        }
        return params.build();
    }

    @Override
    public TurBatchStatus status(TurLLMInstance instance, String vendorBatchId) {
        MessageBatch batch = client(instance).messages().batches().retrieve(vendorBatchId);
        MessageBatchRequestCounts counts = batch.requestCounts();
        long total = counts.succeeded() + counts.errored() + counts.canceled()
                + counts.expired() + counts.processing();
        long completed = counts.succeeded();
        long failed = counts.errored() + counts.canceled() + counts.expired();
        return new TurBatchStatus(vendorBatchId, mapState(batch), total, completed, failed);
    }

    @Override
    public List<TurBatchChatResult> results(TurLLMInstance instance, String vendorBatchId) {
        List<TurBatchChatResult> results = new ArrayList<>();
        try (StreamResponse<MessageBatchIndividualResponse> stream =
                client(instance).messages().batches().resultsStreaming(vendorBatchId);
                Stream<MessageBatchIndividualResponse> items = stream.stream()) {
            items.forEach(item -> results.add(mapResult(item)));
        }
        return results;
    }

    @Override
    public void cancel(TurLLMInstance instance, String vendorBatchId) {
        client(instance).messages().batches().cancel(vendorBatchId);
    }

    private TurBatchChatResult mapResult(MessageBatchIndividualResponse item) {
        String customId = item.customId();
        MessageBatchResult result = item.result();
        if (result.succeeded().isPresent()) {
            MessageBatchSucceededResult succeeded = result.succeeded().get();
            Message message = succeeded.message();
            Usage usage = message.usage();
            return TurBatchChatResult.ok(customId, extractText(message),
                    usage.inputTokens(), usage.outputTokens());
        }
        if (result.errored().isPresent()) {
            return TurBatchChatResult.failed(customId,
                    "errored: " + result.errored().get().error());
        }
        if (result.canceled().isPresent()) {
            return TurBatchChatResult.failed(customId, "canceled");
        }
        if (result.expired().isPresent()) {
            return TurBatchChatResult.failed(customId, "expired");
        }
        return TurBatchChatResult.failed(customId, "unknown result");
    }

    private String extractText(Message message) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.content()) {
            if (block.isText()) {
                text.append(block.asText().text());
            }
        }
        return text.toString();
    }

    private AnthropicClient client(TurLLMInstance instance) {
        return nativeProviderClient.anthropic(instance).orElseThrow(() -> new IllegalStateException(
                "Anthropic native client unavailable for instance " + instance.getId()));
    }

    /** Normalize Anthropic's processing status to the vendor-neutral state. */
    static TurBatchState mapState(MessageBatch batch) {
        MessageBatch.ProcessingStatus status = batch.processingStatus();
        if (status.equals(MessageBatch.ProcessingStatus.ENDED)) {
            return TurBatchState.COMPLETED;
        }
        // IN_PROGRESS and CANCELING are both still transitioning toward ENDED.
        return TurBatchState.IN_PROGRESS;
    }
}
