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

@Getter
public class TurConverseAgentGAOAuthExchange {

	private boolean required;

	private String providerId;

	private String authorizationUrl;

	private String tokenUrl;

	private String scopes;

	private String privacyPolicyUrl;

	private String grantType;

	public void setRequired(boolean required) {
		this.required = required;
	}

	public void setProviderId(String providerId) {
		this.providerId = providerId;
	}

	public void setAuthorizationUrl(String authorizationUrl) {
		this.authorizationUrl = authorizationUrl;
	}

	public void setTokenUrl(String tokenUrl) {
		this.tokenUrl = tokenUrl;
	}

	public void setScopes(String scopes) {
		this.scopes = scopes;
	}

	public void setPrivacyPolicyUrl(String privacyPolicyUrl) {
		this.privacyPolicyUrl = privacyPolicyUrl;
	}

	public void setGrantType(String grantType) {
		this.grantType = grantType;
	}

}
