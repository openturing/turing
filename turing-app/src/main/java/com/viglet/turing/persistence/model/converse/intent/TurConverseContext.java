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

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the turMLModel database table.
 * 
 */
@Getter
@Entity
@Table(name = "converse_context")
@JsonIgnoreProperties({ "intentInputs", "intentOutputs" })
public class TurConverseContext implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Setter
	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Setter
	private String text;

	@ManyToMany
	@JoinTable(name = "converse_context_intent_in")
	private Set<TurConverseIntent> intentInputs = new HashSet<>();

	@ManyToMany
	@JoinTable(name = "converse_context_intent_out")
	private Set<TurConverseIntent> intentOutputs = new HashSet<>();

	@Setter
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

	public void setIntentInputs(Set<TurConverseIntent> intentInputs) {
		this.intentInputs.clear();
		if (intentInputs != null) {
			this.intentInputs.addAll(intentInputs);
		}
	}

	public void setIntentOutputs(Set<TurConverseIntent> intentOutputs) {
		this.intentOutputs.clear();
		if (intentOutputs != null) {
			this.intentOutputs.addAll(intentOutputs);
		}
	}

	public void setAgentById(String agentId) {
		agent = new TurConverseAgent();
		agent.setId(agentId);

	}
}