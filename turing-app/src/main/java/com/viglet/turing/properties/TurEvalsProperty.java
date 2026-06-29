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
package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * F.9 / §X.10.b — configuration for the OpenAI Evals integration
 * ({@code turing.evals.*}).
 *
 * <p>Off by default: T168 pushes the agent's rubric fixtures to the OpenAI
 * Evals API and runs server-side graders only when {@link #enabled} is true and
 * the eval LLM is OpenAI-backed; otherwise the call is a no-op and the existing
 * local LLM-judge path ({@code TurAgentEvalRunnerService.runAgent}) is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurEvalsProperty {

    /** Master switch for the OpenAI Evals push. When false, the push is a no-op. */
    private boolean enabled = false;

    /**
     * Model the server-side {@code label_model} grader uses to judge each rubric
     * fixture. Blank → the eval LLM instance's own model is used.
     */
    private String graderModel = "";
}
