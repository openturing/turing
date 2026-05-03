/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a caller is authenticated but lacks permission for the operation
 * — e.g. attempting to mutate a read-only SE catalog. Maps to HTTP 403.
 *
 * <p>For unauthenticated requests the existing {@code TurAuthenticationEntryPoint}
 * remains in charge and returns 401 with its own response body.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurForbiddenException extends TurApiException {

    public TurForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, TurErrorTypes.FORBIDDEN, message);
    }
}
