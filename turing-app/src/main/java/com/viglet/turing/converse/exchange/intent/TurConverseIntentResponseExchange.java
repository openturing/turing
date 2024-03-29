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
public class TurConverseIntentResponseExchange {

	private boolean resetContexts;
	
	private String action;
	
	private List<TurConverseIntentResponseAffectedContextExchange> affectedContexts;
	
	private List<TurConverseIntentResponseParameterExchange> parameters;
	
	private List<TurConverseIntentMessageExchange> messages;
	
	private TurConverseIntentResponsePlatformsExchange defaultResponsePlatforms;
	
	private List<String> speech;

	public void setResetContexts(boolean resetContexts) {
		this.resetContexts = resetContexts;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public void setAffectedContexts(List<TurConverseIntentResponseAffectedContextExchange> affectedContexts) {
		this.affectedContexts = affectedContexts;
	}

	public void setParameters(List<TurConverseIntentResponseParameterExchange> parameters) {
		this.parameters = parameters;
	}

	public void setMessages(List<TurConverseIntentMessageExchange> messages) {
		this.messages = messages;
	}

	public void setDefaultResponsePlatforms(TurConverseIntentResponsePlatformsExchange defaultResponsePlatforms) {
		this.defaultResponsePlatforms = defaultResponsePlatforms;
	}

	public void setSpeech(List<String> speech) {
		this.speech = speech;
	}
	
}
