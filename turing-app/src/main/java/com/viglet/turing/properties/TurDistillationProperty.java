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
 * F.9 / §X.10.c — configuration for the distillation pipeline
 * ({@code turing.distillation.*}).
 *
 * <p>Off by default: T169 exports an agent's Stored Completions (T167),
 * submits a fine-tuning job, and — after the eval gate (T286) passes against
 * the fine-tuned candidate — swaps the agent's LLM instance model. When
 * {@link #enabled} is false the "Distill" action is a no-op and nothing is sent
 * to OpenAI.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurDistillationProperty {

    /** Master switch for the distillation pipeline. When false, distill is a no-op. */
    private boolean enabled = false;

    /** Base model the fine-tuning job trains from. */
    private String baseModel = "gpt-4o-mini";

    /** Default look-back window (days) when the caller doesn't specify one. */
    private int defaultDays = 30;

    /** Minimum training examples required to submit a fine-tuning job (OpenAI floor is 10). */
    private int minExamples = 10;

    /** Max stored completions pulled per distillation export. */
    private int maxExamples = 1000;

    /** Cron for the poller that drives fine-tuning jobs to completion (default every 15 min). */
    private String pollCron = "0 */15 * * * *";

    /**
     * T191 / §X.15.e — cron for the overnight self-improvement job that kicks off
     * propose-only distillation for opted-in agents (default 03:20 daily). Still
     * requires {@link #enabled} and each agent's {@code overnightImprovementEnabled}.
     */
    private String overnightCron = "0 20 3 * * *";
}
