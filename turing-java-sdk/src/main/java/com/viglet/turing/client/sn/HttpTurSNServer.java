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

import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;

import java.net.URL;
import java.util.Locale;


/**
 * HTTP of TurnSNServer.
 *
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
public class HttpTurSNServer extends TurSNServer {

    public HttpTurSNServer(URL serverURL, String siteName) {
        super(serverURL, siteName);
    }

    public HttpTurSNServer(URL serverURL, String siteName, Locale locale) {
        super(serverURL, siteName, locale);

    }

    public HttpTurSNServer(URL serverURL, String siteName, Locale locale, TurApiKeyCredentials apiKeyCredentials) {
        super(serverURL, siteName, locale, apiKeyCredentials);

    }

    public HttpTurSNServer(URL serverURL, String siteName, TurApiKeyCredentials apiKeyCredentials) {
        super(serverURL, siteName, apiKeyCredentials);

    }

    public HttpTurSNServer(URL serverURL, String siteName, Locale locale, TurApiKeyCredentials apiKeyCredentials, String userId) {
        super(serverURL, siteName, locale, apiKeyCredentials, userId);

    }
}
