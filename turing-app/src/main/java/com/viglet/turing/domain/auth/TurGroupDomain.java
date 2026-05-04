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
package com.viglet.turing.domain.auth;

import java.util.Set;

/**
 * Domain entity for an auth group — bundles users together and grants them
 * a set of roles. Free of JPA / Jackson annotations and immutable.
 *
 * <p>Memberships and role assignments are projected as immutable sets of
 * IDs ({@code roleIds} are role primary keys, {@code userIds} are
 * usernames since {@link TurUserDomain} is keyed by username).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurGroupDomain(
        String id,
        String name,
        String description,
        Set<String> roleIds,
        Set<String> userIds) {
}
