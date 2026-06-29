/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.tenant.TurTenantMembership;

/**
 * T257 / §XIV.2.1 — persistence for {@link TurTenantMembership} (user ↔ tenant
 * ↔ role).
 *
 * <p>The membership-lookup finders back T259's access check and T265's "my
 * tenants".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurTenantMembershipRepository extends JpaRepository<TurTenantMembership, String> {

    /** Every tenant a user belongs to (T265 "my tenants"). */
    List<TurTenantMembership> findByUsername(String username);

    /** The membership joining one user to one tenant (T259 access check). */
    Optional<TurTenantMembership> findByTenant_IdAndUsername(String tenantId, String username);

    @Modifying
    @Query("delete from TurTenantMembership m where m.id = ?1")
    void delete(String id);
}
