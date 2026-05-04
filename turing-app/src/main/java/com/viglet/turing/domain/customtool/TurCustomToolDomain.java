/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.domain.customtool;

/**
 * Domain entity for a user-defined Spring AI tool callable backed by a
 * Groovy script. The {@code parametersJson} string carries the
 * JSON-serialized parameter declarations the LLM is allowed to pass at
 * runtime; {@code returnType} is the JSON-Schema primitive of the value
 * the script returns.
 *
 * <p>Free of JPA / Jackson annotations and immutable. The Groovy script
 * source is part of the tool's definition (not a credential) and is
 * therefore included on the domain — runtime invocation paths still
 * decide when to actually compile and execute it.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurCustomToolDomain(
        String id,
        String title,
        String description,
        String descriptionMetaPrompt,
        String icon,
        String llmDescription,
        String llmDescriptionMetaPrompt,
        String groovyScript,
        String groovyMetaPrompt,
        String parametersJson,
        String returnType,
        int enabled) {

    /** True when the custom tool is enabled for runtime use (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }
}
