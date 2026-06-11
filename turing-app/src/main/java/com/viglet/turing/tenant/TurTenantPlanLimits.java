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

import java.util.Locale;

/**
 * T277 / §XIV.5.3 — per-plan resource limits. A value of {@code -1} means
 * <em>unlimited</em>.
 *
 * <p>Plans are matched case-insensitively by {@code TurTenant.getPlan()}.
 * The default {@code FREE} plan is bounded; any other (paid / custom) plan is
 * unlimited until a richer billing model lands — so this never blocks a paying
 * tenant by accident.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurTenantPlanLimits(long maxAgents, long maxSites, long monthlyTokens) {

    public static final TurTenantPlanLimits UNLIMITED = new TurTenantPlanLimits(-1, -1, -1);
    public static final TurTenantPlanLimits FREE = new TurTenantPlanLimits(3, 2, 100_000);

    public static TurTenantPlanLimits forPlan(String plan) {
        if (plan == null) {
            return FREE;
        }
        return "FREE".equals(plan.trim().toUpperCase(Locale.ROOT)) ? FREE : UNLIMITED;
    }

    public boolean isUnlimited(long limit) {
        return limit < 0;
    }
}
