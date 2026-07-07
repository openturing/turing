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
package com.viglet.turing.genai.nativeapi.anthropic.editor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T142 / §X.4.d — default {@link TurAgentBashExecutor} that is never available.
 *
 * <p>Lets the {@code anthropic-bash} capability be selectable in the matrix
 * without bundling a sandbox dependency. When Claude calls the bash tool, the
 * editor loop sends back an errors-as-text {@code tool_result} explaining no
 * executor is configured, exactly like {@code TurNoOpComputerUseDriver}.
 * Registered only when no other {@link TurAgentBashExecutor} bean exists, so a
 * real sandbox-backed executor transparently takes over.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration
public class TurNoOpAgentBashExecutor {

    @Bean
    @ConditionalOnMissingBean(TurAgentBashExecutor.class)
    public TurAgentBashExecutor noOpAgentBashExecutor() {
        return new TurAgentBashExecutor() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public BashResult run(String agentId, String conversationId, String command) {
                return BashResult.failed(
                        "No bash executor (e.g. a sandbox backend) is configured on this "
                                + "deployment, so shell commands cannot be run.", -1);
            }
        };
    }
}
