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
package com.viglet.turing.converse.exchange.agent;

import lombok.Getter;

import java.util.List;
@Getter
public class TurConverseAgentGAExchange {

	private boolean googleAssistantCompatible;
	
	private String project;
	
	private boolean welcomeIntentSignInRequired;
	
	private List<String> startIntents;
	
	private List<String> systemIntents;
	
	private List<String> endIntentIds;
	
	private TurConverseAgentGAOAuthExchange oAuthLinking;
	
	private String voiceType;
	
	private List<String> capabilities;
	
	private String env;
	
	private String protocolVersion;
	
	private String autoPreviewEnabled;
	
	private boolean isDeviceAgent;

	public void setGoogleAssistantCompatible(boolean googleAssistantCompatible) {
		this.googleAssistantCompatible = googleAssistantCompatible;
	}

	public void setProject(String project) {
		this.project = project;
	}

	public void setWelcomeIntentSignInRequired(boolean welcomeIntentSignInRequired) {
		this.welcomeIntentSignInRequired = welcomeIntentSignInRequired;
	}

	public void setStartIntents(List<String> startIntents) {
		this.startIntents = startIntents;
	}

	public void setSystemIntents(List<String> systemIntents) {
		this.systemIntents = systemIntents;
	}

	public void setEndIntentIds(List<String> endIntentIds) {
		this.endIntentIds = endIntentIds;
	}

	public void setoAuthLinking(TurConverseAgentGAOAuthExchange oAuthLinking) {
		this.oAuthLinking = oAuthLinking;
	}

	public void setVoiceType(String voiceType) {
		this.voiceType = voiceType;
	}

	public void setCapabilities(List<String> capabilities) {
		this.capabilities = capabilities;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public void setProtocolVersion(String protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	public void setAutoPreviewEnabled(String autoPreviewEnabled) {
		this.autoPreviewEnabled = autoPreviewEnabled;
	}

	public void setIsDeviceAgent(boolean isDeviceAgent) {
		this.isDeviceAgent = isDeviceAgent;
	}
	
	

	
}
