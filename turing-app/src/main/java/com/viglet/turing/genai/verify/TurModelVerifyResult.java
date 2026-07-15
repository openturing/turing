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

/**
 * Outcome of a live "verify" probe against a configured LLM instance or
 * embedding model — a tiny, secret-free result the admin UI renders as a
 * success/error badge with the round-trip latency.
 *
 * @param ok        {@code true} when the model answered a minimal probe call.
 * @param message   Human-readable detail (model name + dimensions on success,
 *                  the provider's root-cause message on failure). Never carries
 *                  the API key or any request payload.
 * @param latencyMs Wall-clock milliseconds the probe round-trip took.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurModelVerifyResult(boolean ok, String message, long latencyMs) {

    public static TurModelVerifyResult success(String message, long latencyMs) {
        return new TurModelVerifyResult(true, message, latencyMs);
    }

    public static TurModelVerifyResult failure(String message, long latencyMs) {
        return new TurModelVerifyResult(false, message, latencyMs);
    }
}
