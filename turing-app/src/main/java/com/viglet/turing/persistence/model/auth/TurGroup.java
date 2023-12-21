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
package com.viglet.turing.persistence.model.auth;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the TurGroup database table.
 * 
 */

@Getter
@Entity
@Table(name = "tur_group")
public class TurGroup implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(updatable = false, nullable = false)
	private String id;

	private String name;

	private String description;

	@ManyToMany
	private Collection<TurRole> turRoles = new HashSet<>();

	@ManyToMany(mappedBy = "turGroups")
	private Collection<TurUser> turUsers = new HashSet<>();

	public void setId(String id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setTurUsers(Collection<TurUser> turUsers) {
		this.turUsers.clear();
		if (turUsers != null) {
			this.turUsers.addAll(turUsers);
		}
	}
	public void setTurRoles(Collection<TurRole> turRoles) {
		this.turRoles.clear();
		if (turRoles != null) {
			this.turRoles.addAll(turRoles);
		}
	}
}