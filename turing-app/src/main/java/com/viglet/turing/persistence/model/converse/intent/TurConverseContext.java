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

package com.viglet.turing.persistence.model.converse.intent;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;

/**
 * The persistent class for the turMLModel database table.
 * 
 */
@Entity
@Table(name = "turConverseCtx")
@NamedQuery(name = "TurConverseContext.findAll", query = "SELECT cc FROM TurConverseContext cc")
@JsonIgnoreProperties({ "intentInputs", "intentOutputs" })
public class TurConverseContext implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String text;

	@ManyToMany
	@JoinTable(name = "turConverseCtxIntentIn")
	private Set<TurConverseIntent> intentInputs = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "turConverseCtxIntentOut")
	private Set<TurConverseIntent> intentOutputs = new HashSet<>();

	@ManyToOne
	@JoinColumn(name = "agent_id")
	@JsonIdentityReference(alwaysAsId = true)
	private TurConverseAgent agent;

	public TurConverseContext() {
		super();
	}

	public TurConverseContext(String text) {
		super();
		this.setText(text);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public Set<TurConverseIntent> getIntentInputs() {
		return this.intentInputs;
	}

	public void setIntentInputs(Set<TurConverseIntent> intentInputs) {
		this.intentInputs.clear();
		if (intentInputs != null) {
			this.intentInputs.addAll(intentInputs);
		}
	}

	public Set<TurConverseIntent> getIntentOutputs() {
		return this.intentOutputs;
	}

	public void setIntentOutputs(Set<TurConverseIntent> intentOutputs) {
		this.intentOutputs.clear();
		if (intentOutputs != null) {
			this.intentOutputs.addAll(intentOutputs);
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