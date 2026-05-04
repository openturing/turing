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
package com.viglet.turing.domain.store;

/**
 * Domain entity for a vector store vendor (Qdrant, Milvus, pgvector, ...).
 * Immutable; the {@code plugin} field carries the registered plugin name
 * used by the runtime to resolve the store backend.
 *
 * <p>Identical shape to {@link com.viglet.turing.domain.se.TurSEVendorDomain}
 * and {@link com.viglet.turing.domain.llm.TurLLMVendorDomain} — closes the
 * vendor catalog that {@link TurStoreInstanceDomain#vendorId()} references.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurStoreVendorDomain(
        String id,
        String title,
        String description,
        String plugin,
        String website) {
}
