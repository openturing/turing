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
package com.viglet.turing.spring.security;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.core.security.VigletAuthenticationEntryPoint;

/**
 * Turing's JSON {@code 401} entry-point: the {@code viglet-core} primitive
 * ({@link VigletAuthenticationEntryPoint}) configured with Turing's only
 * Basic-realm rule — the {@code /git/} surface, where native Git-over-HTTP
 * clients need a {@code WWW-Authenticate} challenge to prompt for credentials.
 *
 * <p>The 401 logic itself now lives in {@code viglet-core-security} (Block Q /
 * T369). As a {@code @Component} it satisfies the core auto-configuration's
 * {@code @ConditionalOnMissingBean}, so the generic core bean never activates here.
 */
@Component
public class TurAuthenticationEntryPoint extends VigletAuthenticationEntryPoint {

	public TurAuthenticationEntryPoint() {
		super(List.of(new BasicRealmRule("/git/", "Turing Git")));
	}
}
