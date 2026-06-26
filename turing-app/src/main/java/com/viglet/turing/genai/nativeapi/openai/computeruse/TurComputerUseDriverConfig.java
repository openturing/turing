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
package com.viglet.turing.genai.nativeapi.openai.computeruse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T136 / §X.3.d — supplies the default {@link TurComputerUseDriver} when none
 * other is on the classpath.
 *
 * <p>The seam is deliberately open: drop in a real driver bean (a browser
 * backend, a remote VM, …) and {@code @ConditionalOnMissingBean} keeps the
 * {@link TurNoOpComputerUseDriver} out of the context so the real one wins with
 * no further wiring — the same factory pattern {@code TurStorageConfig} uses.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration(proxyBeanMethods = false)
public class TurComputerUseDriverConfig {

    @Bean
    @ConditionalOnMissingBean(TurComputerUseDriver.class)
    public TurComputerUseDriver turNoOpComputerUseDriver() {
        return new TurNoOpComputerUseDriver();
    }
}
