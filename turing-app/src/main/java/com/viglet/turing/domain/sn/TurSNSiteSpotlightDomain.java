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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * Domain entity for a Semantic Navigation site spotlight — a curated set of
 * documents and trigger terms surfaced for matching queries on a site /
 * locale. Free of JPA / Jackson annotations and immutable.
 *
 * <p>The parent site is referenced by its ID ({@code snSiteId}); the
 * cascade-deleted children (terms and documents) are projected to immutable
 * sets of IDs ({@code termIds}, {@code documentIds}) following the same
 * pattern as {@code TurAIAgentDomain}'s many-to-many memberships. Future
 * slices may introduce their own domain layers if consumers need term or
 * document fields beyond identity.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteSpotlightDomain(
        String id,
        String name,
        String description,
        LocalDateTime modificationDate,
        int managed,
        String unmanagedId,
        String provider,
        Locale language,
        String snSiteId,
        Set<String> termIds,
        Set<String> documentIds) {

    /** True when the spotlight is managed by Turing (not imported from a partner system). */
    public boolean isManaged() {
        return managed == 1;
    }
}
