/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

/**
 * T1090 — a gateway request carried a parameter the resolved model cannot
 * honour (or carried it malformed). Surfaces as an OpenAI-shaped 400 whose
 * {@code param} names the field, so a client never gets a 200 that silently
 * ignored what it asked for.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurGatewayUnsupportedParameterException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String param;

    public TurGatewayUnsupportedParameterException(String param, String message) {
        super(message);
        this.param = param;
    }

    public String getParam() {
        return param;
    }
}
