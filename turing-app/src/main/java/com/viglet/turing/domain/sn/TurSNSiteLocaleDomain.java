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
package com.viglet.turing.domain.sn;

import java.util.Locale;

/**
 * Domain entity for a Semantic Navigation site locale — an immutable record
 * pairing a search-engine core with a {@link Locale} for a given site.
 * Free of JPA / Jackson annotations.
 *
 * <p>The parent site is referenced by its ID only ({@code snSiteId}); resolve
 * through {@link TurSNSiteRepositoryPort} when the full site aggregate is
 * needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteLocaleDomain(
        String id,
        Locale language,
        String core,
        int position,
        String snSiteId) {
}
