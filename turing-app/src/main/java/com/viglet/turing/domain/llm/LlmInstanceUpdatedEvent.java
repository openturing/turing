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

import java.time.OffsetDateTime;

/**
 * Domain event raised when an LLM instance is created or updated. Listeners
 * can react to invalidate caches keyed by the instance, refresh resilience
 * registries, or audit the change — without the controller knowing about
 * any of them.
 *
 * <p>Carries the instance id and provider type plus the kind of change
 * (create vs update). Sensitive fields like API keys are intentionally not
 * included.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record LlmInstanceUpdatedEvent(
        String instanceId,
        LlmProviderType providerType,
        Kind kind,
        OffsetDateTime occurredAt) {

    public enum Kind {
        CREATED, UPDATED
    }

    public static LlmInstanceUpdatedEvent created(String instanceId, LlmProviderType type) {
        return new LlmInstanceUpdatedEvent(instanceId, type, Kind.CREATED, OffsetDateTime.now());
    }

    public static LlmInstanceUpdatedEvent updated(String instanceId, LlmProviderType type) {
        return new LlmInstanceUpdatedEvent(instanceId, type, Kind.UPDATED, OffsetDateTime.now());
    }
}
