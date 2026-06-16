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
 * T336 / §XIV.8.3 — the seam the tenant-deprovision sweep uses to ask the
 * identity provider "is this principal still around?". Decoupled from Keycloak
 * so the {@link TurTenantDeprovisionService} stays unit-testable and a future
 * IdP (or a reconciliation feed) can be dropped in without touching the
 * orchestration.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurTenantOwnerIdentityProvider {

    /**
     * @return {@code true} only when the provider is fully configured and can
     *         actually answer {@link #statusOf(String)}. When {@code false} the
     *         sweep does nothing (it must never act on a guess).
     */
    boolean isEnabled();

    /** The current state of {@code username} in the identity provider. */
    TurIdentityStatus statusOf(String username);
}
