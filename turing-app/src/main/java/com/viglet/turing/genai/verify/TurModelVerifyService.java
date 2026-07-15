/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.genai.verify;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates that a configured LLM instance or embedding model actually works by
 * making a single, minimal live call against it — a chat probe for an LLM
 * instance and a one-string embedding for an embedding model. Without this an
 * operator can only guess whether the vendor URL, model id, and (decrypted) API
 * key are correct; the "Verify" buttons on the admin forms surface the answer.
 *
 * <p>
 * Every failure is caught and mapped to a {@link TurModelVerifyResult} rather
 * than thrown, so a bad key or unreachable host renders as a red badge instead
 * of a 500. The returned message is the provider's root-cause text — it never
 * echoes the API key or the request payload.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurModelVerifyService {

    /** Minimal, cheap probe — kept short so the round-trip and token cost are tiny. */
    private static final String PROBE_TEXT = "ping";

    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurRagContextBuilder ragContextBuilder;

    public TurModelVerifyService(TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService,
            TurRagContextBuilder ragContextBuilder) {
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
        this.ragContextBuilder = ragContextBuilder;
    }

    /**
     * Fires a minimal chat call at the instance's configured model and reports
     * whether it answered.
     */
    public TurModelVerifyResult verifyLlmInstance(TurLLMInstance instance) {
        long start = System.nanoTime();
        try {
            String apiKey = decryptKey(instance.getApiKeyEncrypted());
            ChatModel model = llmModelFactory.createChatModel(instance, apiKey);
            String reply = model.call(PROBE_TEXT);
            long ms = elapsedMillis(start);
            if (!StringUtils.hasText(reply)) {
                return TurModelVerifyResult.failure("The model returned an empty response.", ms);
            }
            return TurModelVerifyResult.success(
                    "Model '%s' responded successfully.".formatted(nullToEmpty(instance.getModelName())), ms);
        } catch (Exception e) {
            log.warn("LLM instance verify failed for '{}': {}", instance.getModelName(), describe(e));
            return TurModelVerifyResult.failure(rootCauseMessage(e), elapsedMillis(start));
        }
    }

    /**
     * Resolves the embedding model (local ONNX, HuggingFace ONNX, or a cloud LLM
     * instance) and embeds a single probe string, reporting the vector dimension
     * on success.
     */
    public TurModelVerifyResult verifyEmbeddingModel(TurEmbeddingModel embModel) {
        long start = System.nanoTime();
        try {
            EmbeddingModel model = ragContextBuilder.resolveEmbeddingModel(embModel);
            float[] vector = model.embed(PROBE_TEXT);
            long ms = elapsedMillis(start);
            if (vector == null || vector.length == 0) {
                return TurModelVerifyResult.failure("The embedding model returned an empty vector.", ms);
            }
            return TurModelVerifyResult.success(
                    "Embedding generated successfully (%d dimensions).".formatted(vector.length), ms);
        } catch (Exception e) {
            log.warn("Embedding model verify failed for '{}': {}", embModel.getModelName(), describe(e));
            return TurModelVerifyResult.failure(rootCauseMessage(e), elapsedMillis(start));
        }
    }

    /** Keyless providers (e.g. a local Ollama) legitimately store no key. */
    private String decryptKey(String encrypted) {
        return StringUtils.hasText(encrypted) ? secretCryptoService.decrypt(encrypted) : null;
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String describe(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** Unwrap to the deepest cause so the badge shows the actionable message. */
    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return StringUtils.hasText(msg) ? msg : root.getClass().getSimpleName();
    }
}
