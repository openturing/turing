/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the TurLLMInstance database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name = "llm_instance")
public class TurLLMInstance implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@TurAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	/**
	 * T275 / §XIV.5.1 — owning tenant for BYO infra. A non-null value is a
	 * tenant's own instance (their key/endpoint); {@code null} is a
	 * platform-provided GLOBAL instance every tenant may use. Deliberately
	 * NOT {@code @TenantId} — that would filter the shared NULLs out; the
	 * repositories use an explicit {@code tenantId = :current OR IS NULL}.
	 */
	@jakarta.persistence.Column(name = "tenantId", length = 40)
	private String tenantId;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = true, length = 500)
	private String description;

	@Column(length = 150)
	private String icon;

	@Column(nullable = false)
	private int enabled;

	@Column(nullable = false)
	private String url;

	@ManyToOne
	@JoinColumn(name = "llm_vendor_id", nullable = false)
	private TurLLMVendor turLLMVendor;

	@Column
	private String modelName;

	@Column
	private Double temperature;

	@Column
	private Integer topK;

	@Column
	private Double topP;

	@Column
	private Double repeatPenalty;

	@Column
	private Integer seed;

	@Column
	private Integer numPredict;

	@Column
	private String stop;

	@Column
	private String responseFormat;

	@Column
	private String supportedCapabilities;

	@Column
	private String timeout;

	@Column
	private Integer maxRetries;

	@Column(name = "context_window")
	private Integer contextWindow;

	@Lob
	@Column
	private String providerOptionsJson;

	@Column(name = "tools_enabled", nullable = false)
	private boolean toolsEnabled = true;

	@Column
	@JsonIgnore
	private String apiKeyEncrypted;

	@Transient
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String apiKey;
}