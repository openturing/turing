/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.List;

/**
 * T743 / §XLIX — raised when {@code x-turing-guardrails: strict} moderation
 * flags the request input or the model output. The controller maps it to an
 * OpenAI-shaped HTTP 400 with error type {@code content_filter}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurGatewayGuardrailException extends RuntimeException {

    private final transient List<String> categories;

    public TurGatewayGuardrailException(String message, List<String> categories) {
        super(message);
        this.categories = categories == null ? List.of() : List.copyOf(categories);
    }

    public List<String> getCategories() {
        return categories;
    }
}
