/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.tenant;

import com.viglet.turing.persistence.model.tenant.TurTenant;

/**
 * T264 / §XIV.3 — public view of a {@link TurTenant} returned by the signup,
 * "my tenants" (T265) and platform-admin (T279) APIs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurTenantResponse(String id, String slug, String name, String status, String plan) {

    public static TurTenantResponse of(TurTenant tenant) {
        return new TurTenantResponse(tenant.getId(), tenant.getSlug(), tenant.getName(),
                tenant.getStatus() != null ? tenant.getStatus().name() : null, tenant.getPlan());
    }
}
