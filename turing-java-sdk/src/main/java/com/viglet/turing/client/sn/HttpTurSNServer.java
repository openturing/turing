/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn;

import java.net.URL;

import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;

/**
 * HTTP of TurnSNServer.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
public class HttpTurSNServer extends TurSNServer {

	@Deprecated
	public HttpTurSNServer(String turSNServer) {
		super(turSNServer);

	}

	public HttpTurSNServer(URL serverURL, String siteName) {
		super(serverURL, siteName);
	}

	public HttpTurSNServer(URL serverURL, String siteName, String locale) {
		super(serverURL, siteName, locale);

	}

	public HttpTurSNServer(URL serverURL, String siteName, String locale, TurUsernamePasswordCredentials credentials) {
		super(serverURL, siteName, locale, credentials);

	}

	public HttpTurSNServer(URL serverURL, String siteName, String locale, TurUsernamePasswordCredentials credentials,
			String userId) {
		super(serverURL, siteName, locale, credentials, userId);

	}

}
