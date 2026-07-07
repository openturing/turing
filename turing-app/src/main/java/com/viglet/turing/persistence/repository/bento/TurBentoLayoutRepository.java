/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.bento;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.bento.TurBentoLayout;
import com.viglet.turing.persistence.model.bento.TurBentoLayoutScope;

/**
 * T574 / §XXXI.11 — reads and upserts persisted Bento list layouts. Tenant
 * filtering is automatic via the entity's {@code @TenantId}; do not add
 * {@code tenantId} to method names.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurBentoLayoutRepository extends JpaRepository<TurBentoLayout, String> {

    Optional<TurBentoLayout> findByScopeAndOwnerIdAndListId(TurBentoLayoutScope scope, String ownerId,
            String listId);

    void deleteByScopeAndOwnerIdAndListId(TurBentoLayoutScope scope, String ownerId, String listId);
}
