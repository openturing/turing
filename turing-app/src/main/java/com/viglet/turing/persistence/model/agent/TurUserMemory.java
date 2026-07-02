/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T446 / §XXIII.5 — one long-horizon fact the assistant remembers about an end
 * user, surviving ACROSS conversations (unlike the per-conversation T163 memory).
 *
 * <p>Scoped by {@code (tenantId, userId, agentId, memoryKey)}: tenant isolation
 * via Hibernate's {@code @TenantId} filter, {@code userId} is the host-supplied
 * stable visitor id, {@code agentId} keeps one agent's memory of a user separate
 * from another's, and {@code memoryKey} is the fact's category ("preferences",
 * "role", …) so writing the same key updates rather than duplicates. The user can
 * see and delete every row (GDPR posture as a feature).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "user_memory")
public class TurUserMemory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    /** Host-supplied stable visitor/user id (e.g. a logged-in email or opaque token). */
    @Column(name = "userId", nullable = false, length = 200)
    private String userId;

    /** The agent that remembered this fact. */
    @Column(name = "agentId", nullable = false, length = 40)
    private String agentId;

    /** Fact category — writing the same key for a user/agent updates in place. */
    @Column(name = "memoryKey", nullable = false, length = 200)
    private String memoryKey;

    /** The remembered fact. */
    @Lob
    @Column(name = "content")
    private String content;

    /** Epoch millis of first write. */
    @Column(name = "createdAt", nullable = false)
    private long createdAt;

    /** Epoch millis of the last update. */
    @Column(name = "updatedAt", nullable = false)
    private long updatedAt;
}
