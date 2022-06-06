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
package com.viglet.turing.converse.exchange.intent;

import java.util.List;

public class TurConverseIntentExchange {

	private String id;

	private String parentId;

	private String rootParentId;

	private String name;

	private boolean auto;

	private List<String> contexts;

	private List<TurConverseIntentResponseExchange> responses;

	private int priority;

	private boolean webhookUsed;

	private boolean webhookForSlotFilling;

	private boolean fallbackIntent;

	private List<String> events;

	private List<String> conditionalResponses;

	private String condition;

	private List<String> conditionalFollowupEvents;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public String getRootParentId() {
		return rootParentId;
	}

	public void setRootParentId(String rootParentId) {
		this.rootParentId = rootParentId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isAuto() {
		return auto;
	}

	public void setAuto(boolean auto) {
		this.auto = auto;
	}

	public List<String> getContexts() {
		return contexts;
	}

	public void setContexts(List<String> contexts) {
		this.contexts = contexts;
	}

	public List<TurConverseIntentResponseExchange> getResponses() {
		return responses;
	}

	public void setResponses(List<TurConverseIntentResponseExchange> responses) {
		this.responses = responses;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public boolean isWebhookUsed() {
		return webhookUsed;
	}

	public void setWebhookUsed(boolean webhookUsed) {
		this.webhookUsed = webhookUsed;
	}

	public boolean isWebhookForSlotFilling() {
		return webhookForSlotFilling;
	}

	public void setWebhookForSlotFilling(boolean webhookForSlotFilling) {
		this.webhookForSlotFilling = webhookForSlotFilling;
	}

	public boolean isFallbackIntent() {
		return fallbackIntent;
	}

	public void setFallbackIntent(boolean fallbackIntent) {
		this.fallbackIntent = fallbackIntent;
	}

	public List<String> getEvents() {
		return events;
	}

	public void setEvents(List<String> events) {
		this.events = events;
	}

	public List<String> getConditionalResponses() {
		return conditionalResponses;
	}

	public void setConditionalResponses(List<String> conditionalResponses) {
		this.conditionalResponses = conditionalResponses;
	}

	public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public List<String> getConditionalFollowupEvents() {
		return conditionalFollowupEvents;
	}

	public void setConditionalFollowupEvents(List<String> conditionalFollowupEvents) {
		this.conditionalFollowupEvents = conditionalFollowupEvents;
	}

}
