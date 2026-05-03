/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the configured storage backend (filesystem or MinIO) fails an IO
 * operation that the caller can't recover from. Maps to HTTP 502.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurStorageException extends TurApiException {

    public TurStorageException(String message) {
        super(HttpStatus.BAD_GATEWAY, TurErrorTypes.STORAGE_FAILURE, message);
    }

    public TurStorageException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, TurErrorTypes.STORAGE_FAILURE, message, cause);
    }
}
