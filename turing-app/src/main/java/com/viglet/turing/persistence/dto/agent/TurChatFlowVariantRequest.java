/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

/**
 * T97 / §VII.11.g — request body for the variant-generator endpoint.
 *
 * @param instructions  required free-text directive describing the desired
 *                      variant (tone, length, audience, language). The LLM
 *                      uses it to rewrite only the user-facing copy fields
 *                      while preserving the flow's structure and slots.
 * @param targetName    optional name for the resulting variant flow. When
 *                      blank, the service falls back to {@code "{source.name}
 *                      (variant)"}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatFlowVariantRequest(String instructions, String targetName) {
}
