/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.persistence.model.converse.chat;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

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
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
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
