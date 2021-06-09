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

package com.viglet.turing.converse.exchange.agent;

import java.util.List;

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

	public boolean isGoogleAssistantCompatible() {
		return googleAssistantCompatible;
	}

	public void setGoogleAssistantCompatible(boolean googleAssistantCompatible) {
		this.googleAssistantCompatible = googleAssistantCompatible;
	}

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}

	public boolean isWelcomeIntentSignInRequired() {
		return welcomeIntentSignInRequired;
	}

	public void setWelcomeIntentSignInRequired(boolean welcomeIntentSignInRequired) {
		this.welcomeIntentSignInRequired = welcomeIntentSignInRequired;
	}

	public List<String> getStartIntents() {
		return startIntents;
	}

	public void setStartIntents(List<String> startIntents) {
		this.startIntents = startIntents;
	}

	public List<String> getSystemIntents() {
		return systemIntents;
	}

	public void setSystemIntents(List<String> systemIntents) {
		this.systemIntents = systemIntents;
	}

	public List<String> getEndIntentIds() {
		return endIntentIds;
	}

	public void setEndIntentIds(List<String> endIntentIds) {
		this.endIntentIds = endIntentIds;
	}

	public TurConverseAgentGAOAuthExchange getoAuthLinking() {
		return oAuthLinking;
	}

	public void setoAuthLinking(TurConverseAgentGAOAuthExchange oAuthLinking) {
		this.oAuthLinking = oAuthLinking;
	}

	public String getVoiceType() {
		return voiceType;
	}

	public void setVoiceType(String voiceType) {
		this.voiceType = voiceType;
	}

	public List<String> getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(List<String> capabilities) {
		this.capabilities = capabilities;
	}

	public String getEnv() {
		return env;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public String getProtocolVersion() {
		return protocolVersion;
	}

	public void setProtocolVersion(String protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	public String getAutoPreviewEnabled() {
		return autoPreviewEnabled;
	}

	public void setAutoPreviewEnabled(String autoPreviewEnabled) {
		this.autoPreviewEnabled = autoPreviewEnabled;
	}

	public boolean getIsDeviceAgent() {
		return isDeviceAgent;
	}

	public void setIsDeviceAgent(boolean isDeviceAgent) {
		this.isDeviceAgent = isDeviceAgent;
	}
	
	

	
}
