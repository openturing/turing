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
package com.viglet.turing.genai.provider.llm;

/**
 * A single selectable HuggingFace embedding model exposed to the provider-aware
 * model picker (T624/T626).
 *
 * <p>{@code repoId} is the exact string stored in
 * {@link com.viglet.turing.persistence.model.embedding.TurEmbeddingModel}'s
 * {@code modelReference} column when the {@code HUGGINGFACE} provider is chosen
 * (e.g. {@code sentence-transformers/all-MiniLM-L6-v2}). The remaining fields
 * are display/ranking metadata: {@code downloads}/{@code likes} for popularity
 * ranking, {@code dimensions} when the repo declares it (else {@code null} —
 * T627 probes it on select), and {@code onnxVerified} whether the repo tree was
 * confirmed to expose a loadable {@code .onnx} + {@code tokenizer.json}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurHuggingFaceModelOption(String repoId, String label, Long downloads, Long likes,
        Integer dimensions, boolean onnxVerified) {

    /** Convenience for a catalog entry with known dimensions and verified ONNX. */
    public static TurHuggingFaceModelOption catalog(String repoId, Integer dimensions) {
        return new TurHuggingFaceModelOption(repoId, repoId, null, null, dimensions, true);
    }
}
