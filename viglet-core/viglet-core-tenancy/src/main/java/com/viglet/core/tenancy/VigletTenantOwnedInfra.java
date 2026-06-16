/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.tenancy;

/**
 * Marker for "bring-your-own-infra" instance entities whose {@code tenantId} is a
 * <em>plain column</em> (deliberately NOT {@code @TenantId}) so a
 * platform-provided GLOBAL pool ({@code tenantId IS NULL}) stays visible to every
 * tenant.
 *
 * <p>Because Hibernate does not auto-stamp or auto-filter these, the read/write
 * paths must do it by hand. {@link VigletInfraTenantScope} centralizes that logic
 * and operates on this interface, so the policy lives in one place instead of
 * being copy-pasted into each controller.
 *
 * <p>Lifted from Turing's {@code TurTenantOwnedInfra} into {@code viglet-core}
 * (T370 / Block Q); the six Turing BYO-infra entities ({@code TurSEInstance},
 * {@code TurLLMInstance}, {@code TurEmbeddingModel}, {@code TurStoreInstance},
 * {@code TurIntegrationInstance}, {@code TurMcpServer}) implement it directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface VigletTenantOwnedInfra {

    /** The owning tenant id, or {@code null} for a GLOBAL (platform-provided) instance. */
    String getTenantId();

    /** Set the owning tenant id ({@code null} = GLOBAL). */
    void setTenantId(String tenantId);
}
