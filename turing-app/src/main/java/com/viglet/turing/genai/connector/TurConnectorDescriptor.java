/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.connector;

import java.util.List;
import java.util.Map;

/**
 * T190 / §X.15.d — one entry in the built-in self-install connector catalogue.
 * Describes a third-party system a customer can connect ("connect my Notion"):
 * the human steps to mint an API token, and the machine recipe to probe that the
 * token works (a lightweight authenticated GET).
 *
 * @param key             stable lowercase identifier (e.g. {@code "notion"})
 * @param displayName     human label (e.g. {@code "Notion"})
 * @param kind            {@code "MCP"} (vendor tool collection) or {@code "INTEGRATION"} (BYO backend)
 * @param authType        {@code "BEARER"} / {@code "API_KEY"} / {@code "OAUTH"} — informational for the UI
 * @param defaultEndpoint base API URL used when the caller doesn't override it (may be {@code null}
 *                        when the customer must supply their own host, e.g. self-hosted Confluence)
 * @param testPath        path appended to the endpoint for the connection probe (may be {@code ""})
 * @param authHeaderName  the HTTP header the token rides in (usually {@code "Authorization"})
 * @param authHeaderPrefix prefix prepended to the token in that header (e.g. {@code "Bearer "} or {@code ""})
 * @param extraHeaders    fixed extra headers the vendor requires on the probe (e.g. Notion's version header)
 * @param setupSteps      ordered, human-readable instructions to generate the token
 * @param docsUrl         link to the vendor's token/credential documentation
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurConnectorDescriptor(
        String key,
        String displayName,
        String kind,
        String authType,
        String defaultEndpoint,
        String testPath,
        String authHeaderName,
        String authHeaderPrefix,
        Map<String, String> extraHeaders,
        List<String> setupSteps,
        String docsUrl) {
}
