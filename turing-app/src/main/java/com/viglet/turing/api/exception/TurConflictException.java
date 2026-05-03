/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation conflicts with current resource state — duplicate
 * key, optimistic-lock failure, attempt to delete a resource that is still in
 * use. Maps to HTTP 409.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurConflictException extends TurApiException {

    public TurConflictException(String message) {
        super(HttpStatus.CONFLICT, TurErrorTypes.CONFLICT, message);
    }
}
