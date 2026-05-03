/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request payload or parameter fails business-level validation.
 * Maps to HTTP 400. Use this for explicit checks (e.g., "name must be unique
 * within site"); framework-level Bean Validation failures are handled
 * separately by the global handler.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurValidationException extends TurApiException {

    public TurValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, TurErrorTypes.VALIDATION, message);
    }
}
