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

import java.time.LocalDateTime;

/**
 * Domain entity for a single LLM token-usage row, recording how many
 * tokens a particular {@code TurLLMInstance} consumed for a given model
 * and username at a given point in time. Free of JPA / Jackson annotations
 * and immutable.
 *
 * <p>The {@code ManyToOne} neighbour {@code turLLMInstance} is projected as
 * the bare {@link #llmInstanceId()} per the aggregate-boundary rules.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurLLMTokenUsageDomain(
        String id,
        String llmInstanceId,
        String vendorId,
        String modelName,
        String username,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        LocalDateTime createdAt) {
}
