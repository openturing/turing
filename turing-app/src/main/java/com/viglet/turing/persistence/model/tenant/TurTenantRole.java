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
 * T257 / §XIV.2.1 — a member's role <em>within a single tenant</em>, carried by
 * {@link TurTenantMembership}.
 *
 * <p>This is the tenant-scoped membership role, distinct from the global JPA
 * RBAC ({@code TurRole}/{@code TurPrivilege}) which is reused per membership
 * (§XIV.1, decision 2). T263 maps the membership + this role into per-tenant
 * authorities.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurTenantRole {

    /** Created the tenant (self-service signup, T264); full control incl. billing/teardown. */
    OWNER,

    /** Manages the tenant's resources and members, but is not the billing owner. */
    ADMIN,

    /** Ordinary member — uses the tenant's resources without administrative rights. */
    MEMBER
}
