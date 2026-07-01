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
package com.viglet.turing.api.sn.bean;

/**
 * Request payload to create or rename a facet ordering profile.
 * <p>
 * Only carries client-supplied fields (no primitives) so a partial JSON body
 * such as {@code {"name":"..."}} deserializes cleanly under Jackson 3, whose
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} defaults to {@code true}.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
public record TurSNSiteFacetProfileRequest(String name, String description) {
}
