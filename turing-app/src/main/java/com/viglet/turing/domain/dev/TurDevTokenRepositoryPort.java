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
package com.viglet.turing.domain.dev;

import java.util.List;
import java.util.Optional;

/**
 * Domain-side port for retrieving {@link TurDevTokenDomain} aggregates.
 * Read-only — token rotation / creation paths still write through the JPA
 * repository directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public interface TurDevTokenRepositoryPort {

    Optional<TurDevTokenDomain> findById(String id);

    List<TurDevTokenDomain> findAll();

    /**
     * Look up a token entry by its secret value. The secret is the input;
     * the returned {@link TurDevTokenDomain} does <em>not</em> echo it back
     * — callers asking for "is this token valid?" should test the result's
     * {@code Optional} for presence and use {@code id} / {@code title} for
     * audit logs, never round-trip the secret.
     */
    Optional<TurDevTokenDomain> findByToken(String token);
}
