/*
 * Copyright (C) 2019 the original author or authors. 
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

package com.viglet.turing.converse.exchange.intent;

import java.util.List;

public class TurConverseIntentExchange {

	private String id;
	
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
