/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.spring;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * T1004 — registers {@link TurSseNoBufferingInterceptor}.
 *
 * <p>Kept apart from {@link TurStaticResourceConfiguration}, which is about
 * serving the built frontend: those two share the {@code WebMvcConfigurer}
 * interface and nothing else, and Spring merges every configurer it finds.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration
public class TurSseStreamingConfiguration implements WebMvcConfigurer {

    /**
     * Registered for all paths, and constructed here rather than injected: the
     * interceptor has no collaborators, so making it a bean would only add a
     * wiring step for something nothing else needs to reference.
     *
     * <p>No path patterns either — the interceptor acts only on a handler that
     * declares {@code produces = text/event-stream}, and narrowing here would
     * duplicate that decision somewhere it could disagree with itself.
     */
    @Override
    public void addInterceptors(@NotNull InterceptorRegistry registry) {
        registry.addInterceptor(new TurSseNoBufferingInterceptor());
    }
}
