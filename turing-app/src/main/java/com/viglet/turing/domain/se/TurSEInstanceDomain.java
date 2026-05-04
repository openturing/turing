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
package com.viglet.turing.domain.se;

/**
 * Domain entity for a Search Engine instance — the configuration the
 * application needs to identify and connect to a backend (Solr, etc.).
 * Free of JPA / Jackson annotations and immutable.
 *
 * <p>The vendor reference is projected to its ID; resolve through a
 * dedicated vendor port when the full vendor aggregate is needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSEInstanceDomain(
        String id,
        String title,
        String description,
        String icon,
        int enabled,
        String endpointUrl,
        String vendorId) {

    /** True when the instance is enabled for runtime use (admin toggle). */
    public boolean isEnabled() {
        return enabled == 1;
    }
}
