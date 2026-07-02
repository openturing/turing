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
package com.viglet.turing.persistence.model.se;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.core.tenancy.VigletTenantOwnedInfra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the TurSEInstance database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name = "se_instance")
public class TurSEInstance implements Serializable, VigletTenantOwnedInfra {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@VigletAssignableUuidGenerator
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

	@Column(name = "endpoint_url", nullable = false)
	private String endpointUrl;

	// bi-directional many-to-one association to TurSEVendor
	@ManyToOne
	@JoinColumn(name = "se_vendor_id", nullable = false)
	private TurSEVendor turSEVendor;
}