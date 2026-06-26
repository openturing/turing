/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.voice;

/**
 * T147 / §X.6.a — raised when a real-time voice session cannot be minted
 * (missing credentials, unsupported model, vendor/transport failure). Unlike
 * the rerank fail-open, a voice request is explicit and user-initiated, so the
 * failure surfaces rather than degrading silently.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurRealtimeVoiceException extends RuntimeException {

    public TurRealtimeVoiceException(String message) {
        super(message);
    }

    public TurRealtimeVoiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
