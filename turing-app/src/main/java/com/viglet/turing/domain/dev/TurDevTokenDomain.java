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

/**
 * Domain entity for a developer / integration API token. Free of JPA /
 * Jackson annotations and immutable.
 *
 * <p><strong>The secret token value is intentionally excluded.</strong>
 * Same policy as {@code TurUserDomain.password},
 * {@code TurLLMInstanceDomain.apiKey} and
 * {@code TurStoreInstanceDomain.credential}: secrets stay on the JPA
 * entity for the auth path to consume and never ride along on the domain
 * record. Authentication flows can still <em>query by</em> a candidate
 * token via {@link TurDevTokenRepositoryPort#findByToken(String)} — the
 * secret is the input, not the output.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurDevTokenDomain(
        String id,
        String title,
        String description) {
}
