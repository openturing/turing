/*
 * Copyright (C) 2016-2022 the original author or authors. 
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
package com.viglet.turing.client.sn.utils;

import com.viglet.turing.client.auth.credentials.TurUsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Client Utils
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurSNClientUtils {

	private TurSNClientUtils() {
		throw new IllegalStateException("Utility class");
	}

	public static void authentication(HttpPost httpPost, TurUsernamePasswordCredentials credentials, String apiKey) {
		if (apiKey != null) {
			httpPost.setHeader("Key", apiKey);
		}
		else  if (credentials != null && credentials.getUsername() != null) {
			String auth = String.format("%s:%s", credentials.getUsername(), credentials.getPassword());
			String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
			String authHeader = "Basic " + encodedAuth;
			httpPost.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
		}
	}
}
