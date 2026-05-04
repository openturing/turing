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
package com.viglet.turing.domain.system;

import java.util.Locale;

/**
 * Domain entity for a system locale row — the catalog of supported
 * languages with their human-readable English and Portuguese names. Free
 * of JPA / Jackson annotations and immutable.
 *
 * <p>Note: the JPA primary key is the {@link Locale} instance itself
 * (not a generated string ID), so {@code initials} doubles as the
 * record's identity.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurLocaleDomain(Locale initials, String en, String pt) {
}
