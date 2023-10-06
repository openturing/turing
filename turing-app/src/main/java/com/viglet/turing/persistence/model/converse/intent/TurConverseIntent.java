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

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;

/**
 * The persistent class for the turConverseIntent database table.
 * 
 */
@Entity
@Table(name = "turConverseIntent")
@NamedQuery(name = "TurConverseIntent.findAll", query = "SELECT ci FROM TurConverseIntent ci")
public class TurConverseIntent implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private boolean fallback = false;

	private String name;

	private String actionName;

	@ManyToMany(mappedBy = "intentInputs")
	private Set<TurConverseContext> contextInputs = new HashSet<>();

	@ManyToMany(mappedBy = "intentOutputs")
	private Set<TurConverseContext> contextOutputs = new HashSet<>();

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseEvent> events = new HashSet<>();

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConversePhrase> phrases = new HashSet<>();

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseParameter> parameters = new HashSet<>();

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseResponse> responses = new HashSet<>();

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

	public String getActionName() {
		return actionName;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public Set<TurConverseContext> getContextInputs() {
		return this.contextInputs;
	}

	public void setContextInputs(Set<TurConverseContext> contextInputs) {
		this.contextInputs.clear();
		if (contextInputs != null) {
			this.contextInputs.addAll(contextInputs);
		}
	}

	public Set<TurConverseContext> getContextOutputs() {
		return this.contextOutputs;
	}

	public void setContextOutputs(Set<TurConverseContext> contextOutputs) {
		this.contextOutputs.clear();
		if (contextOutputs != null) {
			this.contextOutputs.addAll(contextOutputs);
		}
	}

	public Set<TurConverseEvent> getEvents() {
		return this.events;
	}

	public void setEvents(Set<TurConverseEvent> events) {
		this.events.clear();
		if (events != null) {
			this.events.addAll(events);
		}
	}

	public Set<TurConversePhrase> getPhrases() {
		return this.phrases;
	}

	public void setPhrases(Set<TurConversePhrase> phrases) {
		this.phrases.clear();
		if (phrases != null) {
			this.phrases.addAll(phrases);
		}
	}

	public Set<TurConverseParameter> getParameters() {
		return this.parameters;
	}

	public void setParameters(Set<TurConverseParameter> parameters) {
		this.parameters.clear();
		if (parameters != null) {
			this.parameters.addAll(parameters);
		}
	}

	public Set<TurConverseResponse> getResponses() {
		return this.responses;
	}

	public void setResponses(Set<TurConverseResponse> responses) {
		this.responses.clear();
		if (responses != null) {
			this.responses.addAll(responses);
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

	public boolean isFallback() {
		return fallback;
	}

	public void setFallback(boolean fallback) {
		this.fallback = fallback;
	}

}