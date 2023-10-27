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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * The persistent class for the turConverseChatResponse database table.
 * 
 */
@Entity
@Table(name = "turConverseChatResponse")
@NamedQuery(name = "TurConverseChatResponse.findAll", query = "SELECT ccr FROM TurConverseChatResponse ccr")
@JsonIgnoreProperties({ "chat" })
public class TurConverseChatResponse implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Getter
	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Getter
	@Column(name = "dateResponse")
	private Date date;

	@Getter
	private String text;

	private boolean isUser;

	@Getter
	private String intentId;

	@Getter
	private String actionName;

	@Getter
	private String parameterName;

	@Getter
	private String parameterValue;

	@Getter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_id")
	private TurConverseChat chat;

	@Getter
	private boolean trainingToIntent = false;
	
	@Getter
	private boolean trainingToFallback = false;
	
	@Getter
	private boolean trainingRemove = false;

	public void setId(String id) {
		this.id = id;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public void setText(String text) {
		this.text = text;
	}

	public boolean isUser() {
		return isUser;
	}

	public void setUser(boolean isUser) {
		this.isUser = isUser;
	}

	public void setIntentId(String intentId) {
		this.intentId = intentId;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public void setParameterName(String parameterName) {
		this.parameterName = parameterName;
	}

	public void setChat(TurConverseChat chat) {
		this.chat = chat;
	}

	public void setParameterValue(String parameterValue) {
		this.parameterValue = parameterValue;
	}

	public void setTrainingToIntent(boolean trainingToIntent) {
		this.trainingToIntent = trainingToIntent;
	}

	public void setTrainingToFallback(boolean trainingToFallback) {
		this.trainingToFallback = trainingToFallback;
	}

	public void setTrainingRemove(boolean trainingRemove) {
		this.trainingRemove = trainingRemove;
	}

}
