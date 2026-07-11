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
package com.viglet.turing.persistence.dto.sn.synonym;

import java.util.Locale;

/**
 * T666 / §XXXIX (Block AP) — request to import an Algolia index's synonyms into
 * the site's synonym store (the one-click "Import from Algolia").
 *
 * @param appId  the Algolia application id
 * @param apiKey an Algolia API key with synonym read access
 * @param host   optional host override (blank → {@code {appId}-dsn.algolia.net})
 * @param index  the source index name
 * @param locale the locale the imported rules apply to
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNSynonymAlgoliaImportRequest(
        String appId,
        String apiKey,
        String host,
        String index,
        Locale locale) {
}
