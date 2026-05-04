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
package com.viglet.turing.domain.intent;

import java.util.Set;

/**
 * Domain entity for an intent — a category of suggested prompts surfaced
 * in the chat UI, scoped to a single AI agent. Free of JPA / Jackson
 * annotations and immutable.
 *
 * <p>Actions are aggregate parts loaded eagerly with the intent (no
 * standalone repository is needed beyond the cascade-managed save / delete
 * paths) and projected as an immutable set of full child records.
 * The owning agent is referenced by ID; resolve through
 * {@code TurAIAgentRepositoryPort} when the full agent is needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurIntentDomain(
        String id,
        String title,
        String description,
        String icon,
        int enabled,
        int sortOrder,
        String agentId,
        Set<TurIntentActionDomain> actions) {

    /** True when the intent is enabled for the chat UI (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }
}
