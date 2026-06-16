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

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;

/**
 * Declarative description of an SN site and its field schema, consumed by
 * {@link TurSNSiteManifestService#provision(TurSNSiteManifest)} to converge a
 * site to this shape in a single idempotent call (T382).
 *
 * <p>The per-field shape reuses {@link TurSNJobAttributeSpec} — the same spec
 * already carried at index time — so "index a new content type" is declared
 * once, the same way whether it arrives with a job item or as a manifest.</p>
 *
 * @param name        the SN site name (required; matched case-insensitively to
 *                    decide create vs. converge).
 * @param description optional human description, applied on create.
 * @param seInstanceId id of the search-engine instance to bind on create. May
 *                     be {@code null} when the site already exists (the bound
 *                     instance is preserved).
 * @param locales     locale codes to provision (e.g. {@code "pt_BR"}, {@code "en_US"}).
 *                    Each maps to a search-engine core / analyzer configset. The
 *                    first one seeds the site on create; the rest are added
 *                    idempotently. {@code null}/empty defaults to {@code en_US}.
 * @param fields      ordered field specifications to ensure exist on the site.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNSiteManifest(
        String name,
        String description,
        String seInstanceId,
        List<String> locales,
        List<TurSNJobAttributeSpec> fields) {
}
