/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * T692 / §XLII.6 — thrown when the async transcription job pool has all workers
 * busy <b>and</b> its bounded queue is full. This is the back-pressure signal:
 * rather than growing memory without limit, the platform rejects the submission
 * so the caller can retry later. Maps to HTTP 429 (Too Many Requests).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TurTranscriptionBusyException extends RuntimeException {

    public TurTranscriptionBusyException(String message) {
        super(message);
    }
}
