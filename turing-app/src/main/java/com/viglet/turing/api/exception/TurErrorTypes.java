/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.exception;

/**
 * Stable type URIs for RFC 7807 ProblemDetail responses. Clients can match on
 * these strings to discriminate error categories without parsing free-text
 * messages.
 *
 * <p>Format follows the IETF tag URI scheme — opaque identifiers that don't
 * need to resolve to live HTTP documents but remain unique across versions.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public final class TurErrorTypes {

    private static final String BASE = "tag:viglet.com,2026:errors/";

    public static final String NOT_FOUND = BASE + "not-found";
    public static final String VALIDATION = BASE + "validation";
    public static final String CONFLICT = BASE + "conflict";
    public static final String FORBIDDEN = BASE + "access-denied";
    public static final String PROVIDER_FAILURE = BASE + "provider-failure";
    public static final String STORAGE_FAILURE = BASE + "storage-failure";
    public static final String INVALID_ARGUMENT = BASE + "invalid-argument";
    public static final String MALFORMED_REQUEST = BASE + "malformed-request";
    public static final String UNSUPPORTED_MEDIA = BASE + "unsupported-media";
    public static final String METHOD_NOT_ALLOWED = BASE + "method-not-allowed";
    public static final String FILE_TOO_LARGE = BASE + "file-too-large";
    public static final String UPSTREAM_TIMEOUT = BASE + "upstream-timeout";
    /**
     * The server tried to reach a downstream HTTP service (LLM, embedding,
     * vector store, MCP) and the connection was refused, the host was unknown,
     * or the network was otherwise unreachable. Distinct from
     * {@link #UPSTREAM_TIMEOUT} (the host answered but too slowly).
     *
     * @since 2026.2.17
     */
    public static final String UPSTREAM_UNREACHABLE = BASE + "upstream-unreachable";
    public static final String INTERNAL = BASE + "internal";

    private TurErrorTypes() {}
}
