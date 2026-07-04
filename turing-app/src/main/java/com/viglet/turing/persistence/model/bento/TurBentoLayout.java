/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.bento;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T574 / §XXXI.11 — one persisted Bento list layout: the emphasis + ordering a
 * user (or an admin, as a global template) chose for a single list surface.
 *
 * <p>A row is unique per {@code (tenantId, scope, ownerId, listId)}. {@code listId}
 * is the surface key (e.g. {@code "llm"}, {@code "aiAgent"}); {@code ownerId} is the
 * user's sub for {@link TurBentoLayoutScope#USER} and empty for
 * {@link TurBentoLayoutScope#GLOBAL}. The item placements themselves live in
 * {@code layoutJson} as a JSON array of {@code {itemId, emphasis, displayOrder}} —
 * a whole-layout blob so a save/reset is atomic and adding an item never needs a
 * schema change. Tenant isolation is Hibernate's {@code @TenantId} filter.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "bento_layout")
public class TurBentoLayout implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false, length = 40)
    private String id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    /** Which cascade layer this layout belongs to. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private TurBentoLayoutScope scope;

    /** User sub for {@code USER} scope; empty string for the {@code GLOBAL} template. */
    @Column(name = "ownerId", nullable = false, length = 200)
    private String ownerId = "";

    /** The list surface this layout applies to (e.g. {@code "llm"}, {@code "aiAgent"}). */
    @Column(name = "listId", nullable = false, length = 100)
    private String listId;

    /** JSON array of {@code {itemId, emphasis, displayOrder}} placements. */
    @Lob
    @Column(name = "layoutJson")
    private String layoutJson;

    /** Epoch millis of first write. */
    @Column(name = "createdAt", nullable = false)
    private long createdAt;

    /** Epoch millis of the last update. */
    @Column(name = "updatedAt", nullable = false)
    private long updatedAt;
}
