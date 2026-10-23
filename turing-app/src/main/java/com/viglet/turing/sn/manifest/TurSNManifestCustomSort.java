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

/**
 * A custom sort declared in an SN manifest (T1104), such as "closing soonest":
 * a DATE field ascending, documents without it last.
 *
 * <p>Like custom facets it is Turing's, so it rides beside the neutral field
 * manifest in {@link TurSNManifestRequest}. A sort is matched to the live one by
 * {@code name}, which is also what a search's {@code sort} parameter names; one
 * the manifest does not declare is left as the console made it.
 *
 * @param name        the sort name, unique per site (required)
 * @param description shown beside the sort in the console
 * @param items       the sort levels, in order (at least one)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurSNManifestCustomSort(String name, String description,
        List<TurSNManifestCustomSortItem> items) {
}
