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

package com.viglet.turing.persistence.model.dev.token;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.turing.spring.security.TurAuditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the turDevToken database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name = "dev_token")
public class TurDevToken extends TurAuditable<String> implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@VigletAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	/** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
	@org.hibernate.annotations.TenantId
	@jakarta.persistence.Column(name = "tenantId", length = 40)
	private String tenantId;

	@Column
	private String description;

	@Column(nullable = false, length = 100)
	private String title;

	@Column
	private String token;

	/**
	 * T646 / §XXXVII.8 — soft revocation. {@code false} disables the token
	 * without deleting the row. Defaults to enabled so existing rows keep working.
	 */
	@Column(nullable = false)
	private boolean enabled = true;

	/**
	 * T646 / §XXXVII.8 — optional expiry. {@code null} = never expires (legacy
	 * behaviour); otherwise the token is rejected once {@code now >= expiresAt}.
	 */
	@Column
	private Instant expiresAt;

	/** True when this token may still authenticate: enabled and not past expiry. */
	@com.fasterxml.jackson.annotation.JsonIgnore
	public boolean isUsable() {
		return enabled && (expiresAt == null || Instant.now().isBefore(expiresAt));
	}
}