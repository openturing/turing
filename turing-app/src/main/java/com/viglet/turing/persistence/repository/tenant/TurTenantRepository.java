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

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.tenant.TurTenant;

/**
 * T257 / §XIV.2.1 — persistence for the {@link TurTenant} registry.
 *
 * <p>Follows the project cache convention: read methods are {@code @Cacheable}
 * and every cache name is evicted by {@code save}/{@code delete}
 * ({@code TurCacheAnnotationConventionsTest} lints the pairing).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurTenantRepository extends JpaRepository<TurTenant, String> {

    @Override
    @Cacheable("turTenantfindAll")
    @NotNull
    List<TurTenant> findAll();

    @Override
    @Cacheable("turTenantfindById")
    @NotNull
    Optional<TurTenant> findById(@NotNull String id);

    /** Resolve a tenant by its public slug/subdomain (T259 resolution). */
    @Cacheable("turTenantfindBySlug")
    Optional<TurTenant> findBySlug(String slug);

    @CacheEvict(value = { "turTenantfindAll", "turTenantfindById", "turTenantfindBySlug" }, allEntries = true)
    @NotNull
    @Override
    <S extends TurTenant> S save(@NotNull S entity);

    @Modifying
    @Query("delete from TurTenant t where t.id = ?1")
    @CacheEvict(value = { "turTenantfindAll", "turTenantfindById", "turTenantfindBySlug" }, allEntries = true)
    void delete(String id);
}
