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
 * T257 / §XIV.2.1 — lifecycle state of a {@link TurTenant}.
 *
 * <p>Only {@link #ACTIVE} tenants may authenticate and serve traffic;
 * {@link #SUSPENDED} is the soft-off state T281's lifecycle teardown flips a
 * tenant into before (or instead of) a hard delete — auth is blocked while the
 * rows survive.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurTenantStatus {

    /** The tenant is live: members can sign in and its resources are served. */
    ACTIVE,

    /** Auth is blocked but data is retained (billing lapse, ops hold, pre-delete). */
    SUSPENDED
}
