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

import java.util.List;
import java.util.Map;

/**
 * A custom facet declared in an SN manifest (T1084): named buckets over one
 * field, such as posting dates of the last 24 hours, 7 days and 30 days.
 *
 * <p>Custom facets are Turing's, not part of the product-neutral viglet-core
 * field manifest, so they ride beside it in {@link TurSNManifestRequest}. A
 * facet is matched to the live one by {@code field} and {@code name}; one the
 * manifest does not declare is left as the console made it.
 *
 * @param field        the site field the buckets filter (required); declared in
 *                     the same manifest or already on the site
 * @param name         the facet name, unique per field (required)
 * @param defaultLabel the label when no locale label matches
 * @param label        per-locale facet labels
 * @param position     the facet's place among the site's custom facets;
 *                     {@code null} appends a new facet and keeps an existing one
 * @param items        the buckets, in order
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestCustomFacet(String field, String name, String defaultLabel,
        Map<String, String> label, Integer position, List<TurSNManifestCustomFacetItem> items) {
}
