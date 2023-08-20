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
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The persistent class for the turConverseChatResponse database table.
 * 
 */
@Entity
@Table(name = "turConverseChatResponse")
@NamedQuery(name = "TurConverseChatResponse.findAll", query = "SELECT ccr FROM TurConverseChatResponse ccr")
@JsonIgnoreProperties({ "chat" })
public class TurConverseChatResponse implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(name = "dateResponse")
	private Date date;

	private String text;

	private boolean isUser;

	private String intentId;

	private String actionName;

	private String parameterName;

	private String parameterValue;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "chat_id")
	private TurConverseChat chat;

	private boolean trainingToIntent = false;
	
	private boolean trainingToFallback = false;
	
	private boolean trainingRemove = false;
	
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

	public String getText() {
		return text;
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

	public String getIntentId() {
		return intentId;
	}

	public void setIntentId(String intentId) {
		this.intentId = intentId;
	}

	public String getActionName() {
		return actionName;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public String getParameterName() {
		return parameterName;
	}

	public void setParameterName(String parameterName) {
		this.parameterName = parameterName;
	}

	public TurConverseChat getChat() {
		return chat;
	}

	public void setChat(TurConverseChat chat) {
		this.chat = chat;
	}

	public String getParameterValue() {
		return parameterValue;
	}

	public void setParameterValue(String parameterValue) {
		this.parameterValue = parameterValue;
	}

	public boolean isTrainingToIntent() {
		return trainingToIntent;
	}

	public void setTrainingToIntent(boolean trainingToIntent) {
		this.trainingToIntent = trainingToIntent;
	}

	public boolean isTrainingToFallback() {
		return trainingToFallback;
	}

	public void setTrainingToFallback(boolean trainingToFallback) {
		this.trainingToFallback = trainingToFallback;
	}

	public boolean isTrainingRemove() {
		return trainingRemove;
	}

	public void setTrainingRemove(boolean trainingRemove) {
		this.trainingRemove = trainingRemove;
	}

}
