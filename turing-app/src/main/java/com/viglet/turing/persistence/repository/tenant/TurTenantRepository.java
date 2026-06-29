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

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.tenant.TurTenant;

/**
 * T257 / §XIV.2.1 — persistence for the {@link TurTenant} registry.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurTenantRepository extends JpaRepository<TurTenant, String> {

    /** Resolve a tenant by its public slug/subdomain (T259 resolution). */
    Optional<TurTenant> findBySlug(String slug);

    @Modifying
    @Query("delete from TurTenant t where t.id = ?1")
    void delete(String id);
}
