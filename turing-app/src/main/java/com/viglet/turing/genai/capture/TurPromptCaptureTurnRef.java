/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.capture;

/**
 * T618 / §XXXIV.6 — a lightweight reference to one captured turn, used to
 * populate the Live Preview's turn picker without loading each capture's body.
 *
 * @param turnIndex  1-based turn number within the conversation.
 * @param capturedAt epoch millis when the turn was captured.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPromptCaptureTurnRef(int turnIndex, long capturedAt) {
}
