/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.gateway;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.gateway.TurGatewayKey;

/**
 * T741 / §XLIX — repository for {@link TurGatewayKey} virtual keys. Lookup by
 * {@code keyHash} is the authentication path (tenant-agnostic — see the entity
 * Javadoc); the raw key is never queried.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurGatewayKeyRepository extends JpaRepository<TurGatewayKey, String> {

    /** Authentication lookup by the SHA-256 hex of the presented raw key. */
    Optional<TurGatewayKey> findByKeyHash(String keyHash);
}
