/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.api.profile.bean;

/**
 * Request body for creating/updating a generic profile.
 * <p>
 * Kept free of primitive components on purpose: Jackson 3 (Spring Boot 4) defaults
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES=true}, so a record with primitives would 400 when
 * the client omits them.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
public record TurProfileRequest(String name, String description) {
}
