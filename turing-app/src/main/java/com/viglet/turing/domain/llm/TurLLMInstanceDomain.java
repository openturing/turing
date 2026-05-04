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
 * Domain entity for a Large Language Model instance — the configuration the
 * application needs to identify and call a specific model on a specific
 * provider. Free of JPA / Jackson annotations and immutable.
 *
 * <p><strong>Sensitive fields are intentionally excluded.</strong> The
 * encrypted API key ({@code apiKeyEncrypted}) and the transient plaintext
 * ({@code apiKey}) live on the JPA entity and are accessed through dedicated
 * services ({@code TurSecretCryptoService}) — exposing them here would let
 * them ride along in caches, logs, and JSON serialisation paths that have no
 * need for them.
 *
 * <p>The vendor reference is projected to its ID; resolve through a vendor
 * port when the full vendor aggregate is needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurLLMInstanceDomain(
        String id,
        String title,
        String description,
        String icon,
        int enabled,
        String url,
        String vendorId,
        String modelName,
        Double temperature,
        Integer topK,
        Double topP,
        Double repeatPenalty,
        Integer seed,
        Integer numPredict,
        String stop,
        String responseFormat,
        String supportedCapabilities,
        String timeout,
        Integer maxRetries,
        Integer contextWindow,
        String providerOptionsJson,
        boolean toolsEnabled) {

    /** True when the instance is enabled for runtime use (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }
}
