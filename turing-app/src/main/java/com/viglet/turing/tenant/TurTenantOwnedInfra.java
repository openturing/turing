/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.tenant;

/**
 * §XIV.5.1 — marker for the "bring-your-own-infra" instance entities
 * ({@code TurSEInstance}, {@code TurLLMInstance}, {@code TurEmbeddingModel},
 * {@code TurStoreInstance}, {@code TurIntegrationInstance},
 * {@code TurMcpServer}) whose {@code tenantId} is a <em>plain column</em>
 * (deliberately NOT {@code @TenantId}) so the platform-provided GLOBAL pool
 * ({@code tenantId IS NULL}) stays visible to every tenant.
 *
 * <p>Because Hibernate does not auto-stamp or auto-filter these, the
 * read/write paths must do it by hand. {@link TurInfraTenantScope} centralizes
 * that logic and operates on this interface, so the policy lives in one place
 * instead of being copy-pasted into each controller.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurTenantOwnedInfra {

    /** The owning tenant id, or {@code null} for a GLOBAL (platform-provided) instance. */
    String getTenantId();

    /** Set the owning tenant id ({@code null} = GLOBAL). */
    void setTenantId(String tenantId);
}
