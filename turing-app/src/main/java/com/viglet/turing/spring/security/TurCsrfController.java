/*
 * Copyright (C) 2016-2023 the original author or authors.
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

import org.springframework.web.bind.annotation.RestController;

import com.viglet.core.security.VigletCsrfController;

/**
 * Turing's CSRF-token endpoint. The handler now lives in
 * {@code viglet-core-security} ({@link VigletCsrfController}, Block Q / T369),
 * mapped at {@code /api/csrf} by default. Declaring it as Turing's own
 * {@code @RestController} bean satisfies the core auto-configuration's
 * {@code @ConditionalOnMissingBean(VigletCsrfController.class)}, so the path is
 * registered exactly once.
 */
@RestController
public class TurCsrfController extends VigletCsrfController {
}
