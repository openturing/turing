/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.tenant;

/**
 * T257 / §XIV.2.1 — state of a user's {@link TurTenantMembership}.
 *
 * <p>T259's resolution filter validates that the authenticated principal has an
 * {@link #ACTIVE} membership in the resolved tenant before letting the request
 * through (403 otherwise). {@link #INVITED} covers a pending invite that hasn't
 * been accepted yet; {@link #SUSPENDED} revokes access without deleting the
 * join row.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurTenantMembershipStatus {

    /** The member may act in the tenant. */
    ACTIVE,

    /** Invite issued, not yet accepted — no access until activated. */
    INVITED,

    /** Access revoked while the membership row is retained. */
    SUSPENDED
}
