/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.batch.gemini;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.genai.Client;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.CancelBatchJobConfig;
import com.google.genai.types.CreateBatchJobConfig;
import com.google.genai.types.DownloadFileConfig;
import com.google.genai.types.GetBatchJobConfig;
import com.google.genai.types.JobState;
import com.google.genai.types.UploadFileConfig;
import com.viglet.turing.genai.batch.TurBatchChatRequest;
import com.viglet.turing.genai.batch.TurBatchChatResult;
import com.viglet.turing.genai.batch.TurBatchProvider;
import com.viglet.turing.genai.batch.TurBatchState;
import com.viglet.turing.genai.batch.TurBatchStatus;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

/**
 * T496 / §X.19 — Gemini Batch API implementation of {@link TurBatchProvider},
 * closing the T156 gap ("Gemini → no provider"). A Gemini-backed instance now
 * runs the T157 nightly summarization and T159 LLM-Judge eval batches at the
 * Gemini Batch ~50% discount instead of falling back to synchronous full price.
 *
 * <p>Submission mirrors the OpenAI Files-then-Batch dance: the requests are
 * serialized to keyed JSONL ({@link TurGeminiBatchJsonl}), uploaded through the
 * Files API, and the file referenced as the batch {@link BatchJobSource}. The
 * keyed format (not inlined requests) is required so each result round-trips back
 * to its Turing {@code customId}. Results download the destination file and parse
 * it symmetrically.
 *
 * <p>The native SDK client comes from {@link TurNativeProviderClient}, reusing
 * the established decryption/base-URL plumbing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurGeminiBatchProvider implements TurBatchProvider {

    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    private final TurNativeProviderClient nativeProviderClient;

    public TurGeminiBatchProvider(TurNativeProviderClient nativeProviderClient) {
        this.nativeProviderClient = nativeProviderClient;
    }

    @Override
    public String getPluginType() {
        return "gemini";
    }

    @Override
    public String submitChat(TurLLMInstance instance, List<TurBatchChatRequest> requests) {
        Client client = client(instance);
        String jsonl = TurGeminiBatchJsonl.buildInputJsonl(requests);
        byte[] bytes = jsonl.getBytes(StandardCharsets.UTF_8);

        com.google.genai.types.File inputFile = client.files.upload(bytes,
                UploadFileConfig.builder()
                        .mimeType("application/jsonl")
                        .displayName("turing-batch-input.jsonl")
                        .build());

        String model = StringUtils.hasText(instance.getModelName())
                ? instance.getModelName() : DEFAULT_MODEL;
        BatchJob batch = client.batches.create(model,
                BatchJobSource.builder().fileName(inputFile.name().orElseThrow(() ->
                        new IllegalStateException("Gemini Files upload returned no name"))).build(),
                CreateBatchJobConfig.builder().displayName("turing-batch").build());
        String batchId = batch.name().orElseThrow(() ->
                new IllegalStateException("Gemini batch create returned no name"));
        log.info("[Batch][Gemini] submitted chat batch {} ({} requests) for instance {}",
                batchId, requests.size(), instance.getId());
        return batchId;
    }

    @Override
    public TurBatchStatus status(TurLLMInstance instance, String vendorBatchId) {
        BatchJob batch = client(instance).batches.get(vendorBatchId, GetBatchJobConfig.builder().build());
        return new TurBatchStatus(vendorBatchId, mapState(batch), 0, 0, 0);
    }

    @Override
    public List<TurBatchChatResult> results(TurLLMInstance instance, String vendorBatchId) {
        Client client = client(instance);
        BatchJob batch = client.batches.get(vendorBatchId, GetBatchJobConfig.builder().build());
        String destFile = batch.dest().flatMap(d -> d.fileName()).orElse(null);
        if (!StringUtils.hasText(destFile)) {
            log.warn("[Batch][Gemini] batch {} completed with no destination file", vendorBatchId);
            return List.of();
        }
        return TurGeminiBatchJsonl.parseOutputJsonl(downloadToString(client, destFile, vendorBatchId));
    }

    @Override
    public void cancel(TurLLMInstance instance, String vendorBatchId) {
        client(instance).batches.cancel(vendorBatchId, CancelBatchJobConfig.builder().build());
    }

    /** Download a Gemini Files resource to a temp file and read it as UTF-8. */
    private String downloadToString(Client client, String fileName, String vendorBatchId) {
        Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("turing-gemini-batch", ".jsonl");
            client.files.download(fileName, tmp.toString(), DownloadFileConfig.builder().build());
            return java.nio.file.Files.readString(tmp, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read Gemini batch output for " + vendorBatchId, e);
        } finally {
            if (tmp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    log.debug("[Batch][Gemini] could not delete temp file {}", tmp);
                }
            }
        }
    }

    private Client client(TurLLMInstance instance) {
        return nativeProviderClient.gemini(instance).orElseThrow(() -> new IllegalStateException(
                "Gemini native client unavailable for instance " + instance.getId()));
    }

    /** Normalize Gemini's {@link JobState} to the vendor-neutral {@link TurBatchState}. */
    static TurBatchState mapState(BatchJob batch) {
        JobState.Known state = batch.state().map(JobState::knownEnum)
                .orElse(JobState.Known.JOB_STATE_UNSPECIFIED);
        return switch (state) {
            case JOB_STATE_SUCCEEDED, JOB_STATE_PARTIALLY_SUCCEEDED -> TurBatchState.COMPLETED;
            case JOB_STATE_FAILED -> TurBatchState.FAILED;
            case JOB_STATE_EXPIRED -> TurBatchState.EXPIRED;
            case JOB_STATE_CANCELLED, JOB_STATE_CANCELLING -> TurBatchState.CANCELLED;
            case JOB_STATE_PENDING, JOB_STATE_QUEUED -> TurBatchState.VALIDATING;
            // RUNNING / PAUSED / UPDATING / UNSPECIFIED and any future state → running
            default -> TurBatchState.IN_PROGRESS;
        };
    }
}
