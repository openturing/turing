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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Input to {@link TurSNManifestDeriver#derive(TurSNManifestDeriveRequest)} — a
 * sample of a source's documents plus the site identity the draft manifest will
 * carry, for LLM-assisted manifest derivation (T387 / §XX.7).
 *
 * <p>The derived {@link TurSNSiteManifest} is a <em>draft for human review</em>;
 * {@code name}/{@code seInstanceId}/{@code locales} are echoed straight into it so
 * the reviewed manifest can be POSTed back to {@code /api/sn/manifest} unchanged
 * once corrected.</p>
 *
 * @param name        the SN site name the draft manifest should carry (required).
 * @param description optional human description for the draft.
 * @param seInstanceId search-engine instance id for the draft (so the reviewed
 *                    manifest can provision a new site without further edits).
 * @param locales     locale codes for the draft; {@code null}/empty defaults to
 *                    {@code en_US} when the manifest is later provisioned.
 * @param documents   the sample documents (parsed JSON objects) the schema is
 *                    inferred from. Required and non-empty.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TurSNManifestDeriveRequest(
        String name,
        String description,
        String seInstanceId,
        List<String> locales,
        List<Map<String, Object>> documents) {
}
