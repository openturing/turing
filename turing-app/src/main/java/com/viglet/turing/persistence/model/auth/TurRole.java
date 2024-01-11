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
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the TurRole database table.
 * 
 */
@Getter
@Entity
@Table(name = "auth_role")
public class TurRole implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Setter
	@Id
	@UuidGenerator
	@GeneratedValue(generator = "UUID")

	@Column(updatable = false, nullable = false)
	private String id;


	@Setter
	@Column(nullable = false, length = 50)
	private String name;

	@Setter
	@Column
	private String description;

	@ManyToMany(mappedBy = "turRoles")
	private Collection<TurGroup> turGroups = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "auth_roles_privileges",
			joinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"),
			inverseJoinColumns = @JoinColumn(name = "privilege_id", referencedColumnName = "id"))
	private Collection<TurPrivilege> turPrivileges = new HashSet<>();

	public TurRole() {
		super();
	}
	public TurRole(String name) {
		this.name = name;
	}

	public void setTurPrivileges(Collection<TurPrivilege> turPrivileges) {
		this.turPrivileges.clear();
		if (turPrivileges != null) {
			this.turPrivileges.addAll(turPrivileges);
		}
	}
}