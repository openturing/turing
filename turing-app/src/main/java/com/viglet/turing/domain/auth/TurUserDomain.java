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
package com.viglet.turing.domain.auth;

import java.time.Instant;
import java.util.Set;

/**
 * Domain entity for an authenticated user. Free of JPA / Jackson
 * annotations and immutable.
 *
 * <p><strong>Sensitive fields are intentionally excluded.</strong> The
 * password hash lives on the JPA entity and is consumed only by the
 * Spring Security authentication path — exposing it here would let it
 * ride along in caches, logs, and JSON paths that have no need for it.
 * Same policy as {@code TurLLMInstanceDomain.apiKey} and
 * {@code TurStoreInstanceDomain.credential}.
 *
 * <p>Group memberships are projected as an immutable set of group IDs
 * (resolve through {@link TurGroupRepositoryPort} when the full group is
 * needed).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurUserDomain(
        String username,
        String email,
        String firstName,
        String lastName,
        Instant lastLogin,
        String realm,
        int enabled,
        String avatarUrl,
        Set<String> groupIds) {

    /** True when the user is enabled for sign-in (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }
}
