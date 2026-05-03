/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.domain.llm;

/**
 * Number of components in an embedding vector. Restricts the value to the
 * positive range mandated by every embedding provider (a zero or negative
 * dimension is meaningless and historically produced cryptic Lucene errors
 * deep inside vector store code).
 *
 * <p>The upper bound (16384) is generous — current production models top out
 * around 4096 — but high enough to accommodate experimental long-vector
 * embeddings without code changes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record EmbeddingDimension(int value) {

    /** Hard ceiling that flags obviously wrong inputs without restricting reasonable models. */
    public static final int MAX = 16_384;

    public EmbeddingDimension {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "EmbeddingDimension must be positive; got: " + value);
        }
        if (value > MAX) {
            throw new IllegalArgumentException(
                    "EmbeddingDimension exceeds maximum (" + MAX + "); got: " + value);
        }
    }

    public static EmbeddingDimension of(int value) {
        return new EmbeddingDimension(value);
    }
}
