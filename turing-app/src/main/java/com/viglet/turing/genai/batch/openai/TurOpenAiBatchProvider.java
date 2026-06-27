/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.openai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.core.http.HttpResponse;
import com.openai.models.batches.Batch;
import com.openai.models.batches.BatchCancelParams;
import com.openai.models.batches.BatchCreateParams;
import com.openai.models.batches.BatchRequestCounts;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchChatResult;
import com.viglet.turing.genai.batch.TurBatchEmbeddingProvider;
import com.viglet.turing.genai.batch.TurBatchEmbeddingRequest;
import com.viglet.turing.genai.batch.TurBatchEmbeddingResult;
import com.viglet.turing.genai.batch.TurBatchProvider;
import com.viglet.turing.genai.batch.TurBatchState;
import com.viglet.turing.genai.batch.TurBatchStatus;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * F.7 / §X.8.a — OpenAI Batch API implementation of {@link TurBatchProvider}.
 *
 * <p>Submission is a two-step Files-then-Batch dance: the requests are
 * serialized to JSONL ({@link TurOpenAiBatchJsonl}), uploaded through the Files
 * API with {@code purpose=batch}, and the returned file id is referenced when
 * creating the batch against {@code /v1/chat/completions} with the {@code 24h}
 * completion window (the only window the API accepts, and where the 50% discount
 * applies). Results are the symmetric reverse: download the output file content
 * and parse it back into per-request results.
 *
 * <p>The native SDK client comes from {@link TurNativeProviderClient}, reusing
 * the established decryption / base-URL plumbing — there is no parallel security
 * path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurOpenAiBatchProvider implements TurBatchProvider, TurBatchEmbeddingProvider {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    private final TurNativeProviderClient nativeProviderClient;

    public TurOpenAiBatchProvider(TurNativeProviderClient nativeProviderClient) {
        this.nativeProviderClient = nativeProviderClient;
    }

    @Override
    public String getPluginType() {
        return "openai";
    }

    @Override
    public String submitChat(TurLLMInstance instance, List<TurBatchChatRequest> requests) {
        String defaultModel = StringUtils.hasText(instance.getModelName())
                ? instance.getModelName()
                : DEFAULT_MODEL;
        String jsonl = TurOpenAiBatchJsonl.buildInputJsonl(requests, defaultModel);
        String batchId = createBatch(instance, jsonl, TurOpenAiBatchJsonl.CHAT_COMPLETIONS_URL);
        log.info("[Batch][OpenAI] submitted chat batch {} ({} requests) for instance {}",
                batchId, requests.size(), instance.getId());
        return batchId;
    }

    @Override
    public String submitEmbeddings(TurLLMInstance instance, List<TurBatchEmbeddingRequest> requests) {
        String defaultModel = StringUtils.hasText(instance.getModelName())
                ? instance.getModelName()
                : DEFAULT_EMBEDDING_MODEL;
        String jsonl = TurOpenAiEmbeddingBatchJsonl.buildInputJsonl(requests, defaultModel);
        String batchId = createBatch(instance, jsonl, TurOpenAiEmbeddingBatchJsonl.EMBEDDINGS_URL);
        log.info("[Batch][OpenAI] submitted embeddings batch {} ({} requests) for instance {}",
                batchId, requests.size(), instance.getId());
        return batchId;
    }

    @Override
    public List<TurBatchEmbeddingResult> embeddingResults(TurLLMInstance instance, String vendorBatchId) {
        return TurOpenAiEmbeddingBatchJsonl.parseOutputJsonl(downloadOutput(instance, vendorBatchId));
    }

    /** Shared Files-then-Batch submission used by both chat and embeddings. */
    private String createBatch(TurLLMInstance instance, String jsonl, String endpointUrl) {
        OpenAIClient client = client(instance);
        byte[] bytes = jsonl.getBytes(StandardCharsets.UTF_8);
        FileObject inputFile = client.files().create(FileCreateParams.builder()
                .purpose(FilePurpose.BATCH)
                .file(MultipartField.<InputStream>builder()
                        .value(new java.io.ByteArrayInputStream(bytes))
                        .filename("turing-batch-input.jsonl")
                        .contentType("application/jsonl")
                        .build())
                .build());
        Batch batch = client.batches().create(BatchCreateParams.builder()
                .inputFileId(inputFile.id())
                .endpoint(BatchCreateParams.Endpoint.of(endpointUrl))
                .completionWindow(BatchCreateParams.CompletionWindow._24H)
                .build());
        return batch.id();
    }

    @Override
    public TurBatchStatus status(TurLLMInstance instance, String vendorBatchId) {
        Batch batch = client(instance).batches().retrieve(vendorBatchId);
        TurBatchState state = mapState(batch);
        long total = 0;
        long completed = 0;
        long failed = 0;
        if (batch.requestCounts().isPresent()) {
            BatchRequestCounts counts = batch.requestCounts().get();
            total = counts.total();
            completed = counts.completed();
            failed = counts.failed();
        }
        return new TurBatchStatus(vendorBatchId, state, total, completed, failed);
    }

    @Override
    public List<TurBatchChatResult> results(TurLLMInstance instance, String vendorBatchId) {
        return TurOpenAiBatchJsonl.parseOutputJsonl(downloadOutput(instance, vendorBatchId));
    }

    /** Download the raw output-file JSONL for a completed batch (empty string when none). */
    private String downloadOutput(TurLLMInstance instance, String vendorBatchId) {
        OpenAIClient client = client(instance);
        Batch batch = client.batches().retrieve(vendorBatchId);
        String outputFileId = batch.outputFileId().orElse(null);
        if (!StringUtils.hasText(outputFileId)) {
            log.warn("[Batch][OpenAI] batch {} completed with no output file", vendorBatchId);
            return "";
        }
        try (HttpResponse content = client.files().content(outputFileId)) {
            return readBody(content.body());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read OpenAI batch output for " + vendorBatchId, e);
        }
    }

    @Override
    public void cancel(TurLLMInstance instance, String vendorBatchId) {
        client(instance).batches().cancel(BatchCancelParams.builder()
                .batchId(vendorBatchId)
                .build());
    }

    private OpenAIClient client(TurLLMInstance instance) {
        return nativeProviderClient.openAi(instance).orElseThrow(() -> new IllegalStateException(
                "OpenAI native client unavailable for instance " + instance.getId()));
    }

    private String readBody(InputStream in) throws IOException {
        return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    }

    /** Normalize OpenAI's batch status to the vendor-neutral {@link TurBatchState}. */
    static TurBatchState mapState(Batch batch) {
        Batch.Status status = batch.status();
        if (status.equals(Batch.Status.COMPLETED)) {
            return TurBatchState.COMPLETED;
        }
        if (status.equals(Batch.Status.FAILED)) {
            return TurBatchState.FAILED;
        }
        if (status.equals(Batch.Status.EXPIRED)) {
            return TurBatchState.EXPIRED;
        }
        if (status.equals(Batch.Status.CANCELLED) || status.equals(Batch.Status.CANCELLING)) {
            return TurBatchState.CANCELLED;
        }
        if (status.equals(Batch.Status.VALIDATING)) {
            return TurBatchState.VALIDATING;
        }
        // in_progress, finalizing, and any future state → still running
        return TurBatchState.IN_PROGRESS;
    }
}
