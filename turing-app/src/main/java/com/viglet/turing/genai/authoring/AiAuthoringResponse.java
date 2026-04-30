/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.authoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Generic chat response for the AI Authoring pattern. The LLM produces
 * BOTH a conversational reply ({@code message}) AND the full updated
 * entity snapshot ({@code state}), validated against the type-specific
 * JSON schema by Spring AI's {@code BeanOutputConverter}.
 *
 * @param <T> entity-specific shape
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiAuthoringResponse<T>(String message, T state) {
}
