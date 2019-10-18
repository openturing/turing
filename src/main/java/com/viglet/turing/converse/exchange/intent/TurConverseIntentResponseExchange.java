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

public class TurConverseIntentResponseExchange {

	private boolean resetContexts;
	
	private String action;
	
	private List<String> affectedContexts;
	
	private List<String> parameters;
	
	private List<TurConverseIntentMessageExchange> messages;
	
	private TurConverseIntentResponsePlataformsExchange defaultResponsePlataforms;
	
	private List<String> speech;

	public boolean isResetContexts() {
		return resetContexts;
	}

	public void setResetContexts(boolean resetContexts) {
		this.resetContexts = resetContexts;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public List<String> getAffectedContexts() {
		return affectedContexts;
	}

	public void setAffectedContexts(List<String> affectedContexts) {
		this.affectedContexts = affectedContexts;
	}

	public List<String> getParameters() {
		return parameters;
	}

	public void setParameters(List<String> parameters) {
		this.parameters = parameters;
	}

	public List<TurConverseIntentMessageExchange> getMessages() {
		return messages;
	}

	public void setMessages(List<TurConverseIntentMessageExchange> messages) {
		this.messages = messages;
	}

	public TurConverseIntentResponsePlataformsExchange getDefaultResponsePlataforms() {
		return defaultResponsePlataforms;
	}

	public void setDefaultResponsePlataforms(TurConverseIntentResponsePlataformsExchange defaultResponsePlataforms) {
		this.defaultResponsePlataforms = defaultResponsePlataforms;
	}

	public List<String> getSpeech() {
		return speech;
	}

	public void setSpeech(List<String> speech) {
		this.speech = speech;
	}
	
}
