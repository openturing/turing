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

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

/**
 * The persistent class for the vigServices database table.
 * 
 */
@Getter
@Entity
@Table(name = "turSEInstance")
@NamedQuery(name = "TurSEInstance.findAll", query = "SELECT si FROM TurSEInstance si")
public class TurSEInstance implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false, length = 100)
	private String description;

	@Column(nullable = false)
	private int enabled;

	@Column(nullable = false, length = 255)
	private String host;

	@Column(nullable = false)
	private int port;

	// bi-directional many-to-one association to VigService
	@ManyToOne
	@JoinColumn(name = "se_vendor_id", nullable = false)
	private TurSEVendor turSEVendor;

	public void setId(String id) {
		this.id = id;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setEnabled(int enabled) {
		this.enabled = enabled;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setTurSEVendor(TurSEVendor turSEVendor) {
		this.turSEVendor = turSEVendor;
	}
}