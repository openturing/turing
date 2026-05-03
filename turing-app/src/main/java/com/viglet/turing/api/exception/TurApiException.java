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

import org.springframework.http.HttpStatus;

/**
 * Abstract base for business exceptions thrown by the REST layer. Every subclass
 * carries the HTTP status and an RFC 7807 type URI so the global handler can
 * produce a {@code ProblemDetail} response without per-exception branching.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public abstract class TurApiException extends RuntimeException {

    private final HttpStatus status;
    private final String typeUri;

    protected TurApiException(HttpStatus status, String typeUri, String message) {
        super(message);
        this.status = status;
        this.typeUri = typeUri;
    }

    protected TurApiException(HttpStatus status, String typeUri, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.typeUri = typeUri;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTypeUri() {
        return typeUri;
    }
}
