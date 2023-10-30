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

import lombok.Getter;

import java.util.List;

@Getter
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

	public void setId(String id) {
		this.id = id;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public void setRootParentId(String rootParentId) {
		this.rootParentId = rootParentId;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setAuto(boolean auto) {
		this.auto = auto;
	}

	public void setContexts(List<String> contexts) {
		this.contexts = contexts;
	}

	public void setResponses(List<TurConverseIntentResponseExchange> responses) {
		this.responses = responses;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public void setWebhookUsed(boolean webhookUsed) {
		this.webhookUsed = webhookUsed;
	}

	public void setWebhookForSlotFilling(boolean webhookForSlotFilling) {
		this.webhookForSlotFilling = webhookForSlotFilling;
	}

	public void setFallbackIntent(boolean fallbackIntent) {
		this.fallbackIntent = fallbackIntent;
	}

	public void setEvents(List<String> events) {
		this.events = events;
	}

	public void setConditionalResponses(List<String> conditionalResponses) {
		this.conditionalResponses = conditionalResponses;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public void setConditionalFollowupEvents(List<String> conditionalFollowupEvents) {
		this.conditionalFollowupEvents = conditionalFollowupEvents;
	}

}
