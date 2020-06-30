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

public class TurConverseAgentWebhookExchange {
	
	private String url;
	
	private String username;
	
	private TurConverseAgentWebhookHeadersExchange headers;
	
	private boolean available;
	
	private boolean useForDomains;
	
	private boolean cloudFunctionsEnabled;
	
	private boolean cloudFunctionsInitialized;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public TurConverseAgentWebhookHeadersExchange getHeaders() {
		return headers;
	}

	public void setHeaders(TurConverseAgentWebhookHeadersExchange headers) {
		this.headers = headers;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public boolean isUseForDomains() {
		return useForDomains;
	}

	public void setUseForDomains(boolean useForDomains) {
		this.useForDomains = useForDomains;
	}

	public boolean isCloudFunctionsEnabled() {
		return cloudFunctionsEnabled;
	}

	public void setCloudFunctionsEnabled(boolean cloudFunctionsEnabled) {
		this.cloudFunctionsEnabled = cloudFunctionsEnabled;
	}

	public boolean isCloudFunctionsInitialized() {
		return cloudFunctionsInitialized;
	}

	public void setCloudFunctionsInitialized(boolean cloudFunctionsInitialized) {
		this.cloudFunctionsInitialized = cloudFunctionsInitialized;
	}
	
	
}
