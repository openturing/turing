/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

/**
 * T1090 — a chat request carried a parameter the resolved model cannot honour
 * (or carried it malformed). An {@link IllegalArgumentException}, so it answers
 * 400; the gateway also puts {@link #getParam()} in its OpenAI-shaped error, so
 * a client never gets a 200 that silently ignored what it asked for. T1102 moved
 * it here from the gateway package when the native chat endpoint began raising
 * it too.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurUnsupportedParameterException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String param;

    public TurUnsupportedParameterException(String param, String message) {
        super(message);
        this.param = param;
    }

    public String getParam() {
        return param;
    }
}
