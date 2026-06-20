/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.chatanalytics;

/**
 * Request body for creating/updating a {@code TurAnalyticsIntent}. Mirrors the
 * client-editable fields (the {@code id}, {@code tenantId} and the owning agent
 * are server-managed) so the persistent entity is never bound directly from the
 * HTTP request. {@code enabled} is boxed so an omitted value can fall back to
 * the entity default (enabled = 1), preserving the prior binding behavior. JSON
 * field names match the entity's, so the admin contract is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurAnalyticsIntentRequest(
        String label,
        String samples,
        String description,
        Integer enabled) {

    /** Effective enabled flag — defaults to 1 (the entity default) when omitted. */
    public int enabledOrDefault() {
        return enabled == null ? 1 : enabled;
    }
}
