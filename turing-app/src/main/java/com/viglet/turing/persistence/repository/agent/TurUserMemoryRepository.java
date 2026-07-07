/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurUserMemory;

/**
 * T446 / §XXIII.5 — cross-conversation per-user memory store. All queries are
 * tenant-filtered automatically by Hibernate's {@code @TenantId} on
 * {@link TurUserMemory}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurUserMemoryRepository extends JpaRepository<TurUserMemory, String> {

    List<TurUserMemory> findByUserIdAndAgentIdOrderByUpdatedAtDesc(String userId, String agentId);

    Optional<TurUserMemory> findByUserIdAndAgentIdAndMemoryKey(String userId, String agentId,
            String memoryKey);

    long deleteByUserIdAndAgentId(String userId, String agentId);
}
