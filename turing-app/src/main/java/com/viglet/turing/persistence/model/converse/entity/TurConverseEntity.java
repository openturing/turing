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
package com.viglet.turing.persistence.model.converse.entity;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the turConverseEntity database table.
 * 
 */
@Entity
@Getter
@Table(name = "tur_converse_entity")
public class TurConverseEntity implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = false, length = 50)
	private String name;

	private boolean isSynonyms;

	private boolean useRegexp;

	private boolean allowAutomatedExpansion;

	private boolean fuzzyMatching;


	@OneToMany(mappedBy = "entity", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseEntityTerm> terms = new HashSet<>();

	@ManyToOne
	@JoinColumn(name = "agent_id")
	@JsonIdentityReference(alwaysAsId = true)
	private TurConverseAgent agent;

	public void setId(String id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isSynonyms() {
		return isSynonyms;
	}

	public void setSynonyms(boolean isSynonyms) {
		this.isSynonyms = isSynonyms;
	}

	public void setUseRegexp(boolean useRegexp) {
		this.useRegexp = useRegexp;
	}

	public void setAllowAutomatedExpansion(boolean allowAutomatedExpansion) {
		this.allowAutomatedExpansion = allowAutomatedExpansion;
	}

	public void setFuzzyMatching(boolean fuzzyMatching) {
		this.fuzzyMatching = fuzzyMatching;
	}

	public void setTerms(Set<TurConverseEntityTerm> terms) {
		this.terms.clear();
		if (terms != null) {
			this.terms.addAll(terms);
		}
	}

	public void setAgent(TurConverseAgent agent) {
		this.agent = agent;
	}

	public void setAgent(String agentId) {
		agent = new TurConverseAgent();
		agent.setId(agentId);

	}
}