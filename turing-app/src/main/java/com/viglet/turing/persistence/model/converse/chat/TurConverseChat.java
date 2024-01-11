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

import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The persistent class for the turConverseChat database table.
 * 
 */
@Getter
@Entity
@Table(name = "converse_chat")
public class TurConverseChat implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String summary;

	@Column(name = "date_chat")
	private Date date;

	@Column(name = "session_chat")
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

	public void setId(String id) {
		this.id = id;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public void setSession(String session) {
		this.session = session;
	}

	public void setAgent(TurConverseAgent agent) {
		this.agent = agent;
	}

	public void setResponses(List<TurConverseChatResponse> responses) {
		this.responses.clear();
		if (responses != null) {
			this.responses.addAll(responses);
		}
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public void setRequests(int requests) {
		this.requests = requests;
	}

	public void setNoMatch(int noMatch) {
		this.noMatch = noMatch;
	}

	public void setUpdated(boolean updated) {
		this.updated = updated;
	}

	public void setTrainingApproved(boolean trainingApproved) {
		this.trainingApproved = trainingApproved;
	}

}
