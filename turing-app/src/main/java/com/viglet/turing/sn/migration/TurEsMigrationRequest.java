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
 * Request to migrate one Elasticsearch index into an SN site (T657 / §XXXVIII.1).
 *
 * <p>The whole migration is driven by just a URL, credentials, and the source
 * index name — no vendor SDK. {@code seInstanceId} is only needed when the target
 * SN site does not yet exist (it is created against that search-engine instance).
 * A {@code dryRun} returns the derived manifest without provisioning or importing.
 * Optional {@code overrides} reshape the schema on the way in (T660).</p>
 *
 * @param sourceUrl    the base URL of the source cluster (e.g. {@code https://host:9200}).
 * @param username     basic-auth user (optional).
 * @param password     basic-auth password (optional).
 * @param apiKey       Elasticsearch API key (optional; takes precedence over basic auth).
 * @param index        the source index name to read.
 * @param targetSite   the SN site name to create or converge.
 * @param seInstanceId the search-engine instance id (required only to create a new site).
 * @param locale       the locale to index documents under (defaults to {@code en_US}).
 * @param batchSize    documents per import batch (defaults to the configured value).
 * @param maxDocuments cap on documents imported ({@code 0}/null = no cap).
 * @param dryRun       when true, only derive + return the manifest.
 * @param overrides    declarative field-mapping overrides applied during import (T660).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEsMigrationRequest(
        String sourceUrl,
        String username,
        String password,
        String apiKey,
        String index,
        String targetSite,
        String seInstanceId,
        String locale,
        Integer batchSize,
        Integer maxDocuments,
        boolean dryRun,
        List<TurMigrationOverride> overrides) {
}
