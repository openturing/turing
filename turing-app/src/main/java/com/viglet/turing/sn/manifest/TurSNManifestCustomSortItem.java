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

package com.viglet.turing.sn.manifest;

import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortMissingEnum;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortOrderEnum;

/**
 * One level of a manifest custom sort (T1104).
 *
 * @param field   the site field to sort by (required); declared in the same
 *                manifest or already on the site
 * @param order   {@code ASC} (default) or {@code DESC}
 * @param missing where documents without the field go, {@code FIRST} or
 *                {@code LAST}; {@code null} keeps the engine's placement
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurSNManifestCustomSortItem(String field, TurSNSiteCustomSortOrderEnum order,
        TurSNSiteCustomSortMissingEnum missing) {
}
