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

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;

/**
 * The persistent class for the turConverseEntity database table.
 * 
 */
@Entity
@Table(name = "turConverseEntity")
@NamedQuery(name = "TurConverseEntity.findAll", query = "SELECT ce FROM TurConverseEntity ce")
public class TurConverseEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
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

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
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

	public boolean isUseRegexp() {
		return useRegexp;
	}

	public void setUseRegexp(boolean useRegexp) {
		this.useRegexp = useRegexp;
	}

	public boolean isAllowAutomatedExpansion() {
		return allowAutomatedExpansion;
	}

	public void setAllowAutomatedExpansion(boolean allowAutomatedExpansion) {
		this.allowAutomatedExpansion = allowAutomatedExpansion;
	}

	public boolean isFuzzyMatching() {
		return fuzzyMatching;
	}

	public void setFuzzyMatching(boolean fuzzyMatching) {
		this.fuzzyMatching = fuzzyMatching;
	}

	public Set<TurConverseEntityTerm> getTerms() {
		return this.terms;
	}

	public void setTerms(Set<TurConverseEntityTerm> terms) {
		this.terms.clear();
		if (terms != null) {
			this.terms.addAll(terms);
		}
	}

	public TurConverseAgent getAgent() {
		return agent;
	}

	public void setAgent(TurConverseAgent agent) {
		this.agent = agent;
	}

	public void setAgent(String agentId) {
		agent = new TurConverseAgent();
		agent.setId(agentId);

	}
}