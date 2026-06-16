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
 * T336 / §XIV.8.3 — the state of one principal in the identity provider, as
 * seen by the tenant-deprovision reconciliation sweep.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurIdentityStatus {

    /** The user exists and is enabled — the tenant must be left alone. */
    ACTIVE,

    /** The user exists but is disabled — treated as gone for deprovisioning. */
    DISABLED,

    /** The user no longer exists in the IdP — treated as gone. */
    ABSENT,

    /**
     * The IdP could not be queried (network error, mis-config, feature off).
     * The sweep <strong>never</strong> acts on a tenant with any UNKNOWN member
     * — uncertainty is fail-safe, not destructive.
     */
    UNKNOWN
}
