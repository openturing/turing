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

package com.viglet.turing.persistence.model.converse.chat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.turing.persistence.model.converse.TurConverseAgent;

/**
 * The persistent class for the turConverseChat database table.
 * 
 */
@Entity
@Table(name = "turConverseChat")
@NamedQuery(name = "TurConverseChat.findAll", query = "SELECT cc FROM TurConverseChat cc")
public class TurConverseChat implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String summary;

	@Column(name = "dateChat")
	private Date date;

	@Column(name = "sessionChat")
	private String session;

	@ManyToOne
	@JoinColumn(name = "agent_id")
	private TurConverseAgent agent;

	private int requests;

	private int noMatch;

	private boolean updated;

	@OneToMany(mappedBy = "chat", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private List<TurConverseChatResponse> responses = new ArrayList<>();

	private boolean trainingApproved = false;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getSession() {
		return session;
	}

	public void setSession(String session) {
		this.session = session;
	}

	public TurConverseAgent getAgent() {
		return agent;
	}

	public void setAgent(TurConverseAgent agent) {
		this.agent = agent;
	}

	public List<TurConverseChatResponse> getResponses() {
		return this.responses;
	}

	public void setResponses(List<TurConverseChatResponse> responses) {
		this.responses.clear();
		if (responses != null) {
			this.responses.addAll(responses);
		}
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public int getRequests() {
		return requests;
	}

	public void setRequests(int requests) {
		this.requests = requests;
	}

	public int getNoMatch() {
		return noMatch;
	}

	public void setNoMatch(int noMatch) {
		this.noMatch = noMatch;
	}

	public boolean isUpdated() {
		return updated;
	}

	public void setUpdated(boolean updated) {
		this.updated = updated;
	}

	public boolean isTrainingApproved() {
		return trainingApproved;
	}

	public void setTrainingApproved(boolean trainingApproved) {
		this.trainingApproved = trainingApproved;
	}

}
