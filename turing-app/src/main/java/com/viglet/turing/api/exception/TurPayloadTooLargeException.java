/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request payload exceeds a business-level size cap (distinct from
 * the servlet/multipart {@code MaxUploadSizeExceededException}, which the global
 * handler maps separately). Maps to HTTP 413.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurPayloadTooLargeException extends TurApiException {

    public TurPayloadTooLargeException(String message) {
        super(HttpStatus.CONTENT_TOO_LARGE, TurErrorTypes.FILE_TOO_LARGE, message);
    }
}
