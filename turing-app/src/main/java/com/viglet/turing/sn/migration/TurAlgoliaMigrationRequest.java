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

package com.viglet.turing.sn.migration;

import java.util.List;

/**
 * Request to migrate one Algolia index into an SN site (T658 / §XXXVIII.2).
 *
 * <p>Algolia has no strict field-type mapping, so the schema is inferred from a
 * sample of records (the T387 heuristics, or an LLM when {@code useLlm} is set and
 * one is configured) and refined with the index settings. {@code seInstanceId} is
 * only needed when the target SN site does not yet exist.</p>
 *
 * @param appId        the Algolia application id.
 * @param apiKey       a read-capable Algolia API key.
 * @param host         optional REST host override (defaults to {@code {appId}-dsn.algolia.net}).
 * @param index        the source index name.
 * @param targetSite   the SN site name to create or converge.
 * @param seInstanceId the search-engine instance id (required only to create a new site).
 * @param locale       the locale to index documents under (defaults to {@code en_US}).
 * @param batchSize    documents per import batch (defaults to the configured value).
 * @param maxDocuments cap on documents imported ({@code 0}/null = no cap).
 * @param useLlm       when true and a default LLM is configured, refine the derived schema with it.
 * @param dryRun       when true, only derive + return the manifest.
 * @param overrides    declarative field-mapping overrides applied during import (T660).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAlgoliaMigrationRequest(
        String appId,
        String apiKey,
        String host,
        String index,
        String targetSite,
        String seInstanceId,
        String locale,
        Integer batchSize,
        Integer maxDocuments,
        boolean useLlm,
        boolean dryRun,
        List<TurMigrationOverride> overrides) {
}
