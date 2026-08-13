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
package com.viglet.turing.exchange.mixin;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place an export bundle's {@code JsonMapper} is built (T373 /
 * §XIV.8.7), so both producers — the SN site export and the AI agent export —
 * are tenant-blind by construction instead of by each remembering to be.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurExportMappers {

    /**
     * The six BYO-infra types: everything implementing
     * {@code VigletTenantOwnedInfra}, i.e. carrying a plain {@code tenantId}
     * column the import path preserves verbatim. Registered by concrete class
     * rather than by the interface so the mixin binding is explicit and does
     * not depend on Jackson's supertype mixin resolution.
     */
    private static final Class<?>[] TENANT_OWNED_INFRA = {
            TurSEInstance.class,
            TurLLMInstance.class,
            TurStoreInstance.class,
            TurEmbeddingModel.class,
            TurMcpServer.class,
            TurIntegrationInstance.class
    };

    private TurExportMappers() {
        // utility
    }

    /**
     * A pretty-printing {@link JsonMapper.Builder} that already drops
     * {@code tenantId} from every BYO-infra instance. Callers add their own
     * mixins on top and {@code build()} it.
     */
    public static JsonMapper.Builder exportMapperBuilder() {
        JsonMapper.Builder builder = JsonMapper.builder()
                .configure(SerializationFeature.INDENT_OUTPUT, true);
        for (Class<?> infraType : TENANT_OWNED_INFRA) {
            builder.addMixIn(infraType, TurTenantBlindInfraMixin.class);
        }
        return builder;
    }

    /** The BYO-infra types this mapper keeps tenant-blind. */
    public static Class<?>[] tenantOwnedInfraTypes() {
        return TENANT_OWNED_INFRA.clone();
    }
}
