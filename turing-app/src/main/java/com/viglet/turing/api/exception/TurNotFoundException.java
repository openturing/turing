/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (entity, file, configuration) does not
 * exist. Maps to HTTP 404.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public class TurNotFoundException extends TurApiException {

    public TurNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, TurErrorTypes.NOT_FOUND, message);
    }

    /**
     * Convenience for the common pattern {@code "Foo not found: id=...".}
     */
    public static TurNotFoundException of(String resource, Object id) {
        return new TurNotFoundException(resource + " not found: " + id);
    }
}
