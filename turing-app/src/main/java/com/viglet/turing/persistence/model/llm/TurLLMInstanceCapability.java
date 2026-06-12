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
package com.viglet.turing.persistence.model.llm;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T132 / §X.2 — one row per (LLM instance × native capability) opt-in.
 *
 * <p>The capability matrix that makes the {@code TurNativeProviderClient} seam
 * explicit: enabling {@code openai-web-search} on instance X turns on the
 * Responses {@code web_search} built-in tool for that instance only; every
 * other instance keeps falling through to the Spring AI executor. There is no
 * monolithic flag day — capabilities ship one row at a time.
 *
 * <p>The {@code capabilityKey} is the stable string from
 * {@link com.viglet.turing.genai.nativeapi.TurNativeCapability#getKey()}.
 * {@code configJson} carries per-capability configuration (e.g. the
 * {@code vectorStoreIds} for {@code file_search} or the {@code serverLabel} /
 * {@code serverUrl} for the remote {@code mcp} tool).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "llm_instance_capability",
        uniqueConstraints = @UniqueConstraint(name = "uq_llm_instance_capability",
                columnNames = {"instanceId", "capabilityKey"}))
public class TurLLMInstanceCapability implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** FK-by-value to {@code llm_instance.id}. Kept as a plain column (not a
     *  {@code @ManyToOne}) so the per-turn capability lookup never drags the
     *  full instance graph into the session. */
    @Column(name = "instanceId", length = 40, nullable = false)
    private String instanceId;

    @Column(name = "capabilityKey", length = 64, nullable = false)
    private String capabilityKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Lob
    @Column(name = "configJson")
    private String configJson;
}
