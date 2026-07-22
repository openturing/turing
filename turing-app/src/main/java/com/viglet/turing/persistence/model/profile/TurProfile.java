/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.persistence.model.profile;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A generic, system-wide (global) profile, created and managed under the console
 * "Management" section.
 * <p>
 * The profile is intentionally <b>neutral</b>: it carries no configuration of its
 * own. Each kind of configuration lives in its own entity that references this
 * profile — the first being facet ordering
 * ({@code TurProfileFacetOrdering}, per profile × SN site). This keeps the model
 * extensible without changing the profile as new configuration types are added.
 * <p>
 * Exactly one profile in the system is the default; it is protected (cannot be
 * renamed or deleted) and used as the runtime fallback.
 * <p>
 * Like the facet profile entities, this intentionally does NOT register a
 * snapshot eviction listener: facet order is resolved outside the search
 * snapshot.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "tur_profile", uniqueConstraints = @UniqueConstraint(name = "uq_tur_profile_name", columnNames = "name"))
public class TurProfile implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Builder.Default
    @Column(name = "default_profile", nullable = false)
    private boolean defaultProfile = false;
}
