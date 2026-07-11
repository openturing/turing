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
 * Request for a shadow / dual-run relevance comparison (T661 / §XXXVIII.5).
 *
 * <p>Runs the same {@code queries} against the source engine and the migrated
 * Turing SN site and diffs the top-{@code rows} result sets, so a team can measure
 * relevance parity before flipping traffic. Which connection fields apply depends
 * on {@code engine}: Elasticsearch uses {@code sourceUrl} + {@code username}/
 * {@code password} or {@code apiKey}; Algolia uses {@code appId} + {@code apiKey}
 * (+ optional {@code host}). No writes happen — this is read-only on both sides.</p>
 *
 * @param engine     the source engine ({@code "elasticsearch"} or {@code "algolia"}).
 * @param sourceUrl  ES cluster URL.
 * @param username   ES basic-auth user.
 * @param password   ES basic-auth password.
 * @param apiKey     ES API key, or the Algolia API key.
 * @param appId      Algolia application id.
 * @param host       optional Algolia REST host override.
 * @param index      the source index name.
 * @param targetSite the Turing SN site to compare against.
 * @param locale     the locale to query Turing under (defaults to {@code en_US}).
 * @param rows       top-N result depth to compare (defaults to 10).
 * @param queries    the query set to run on both sides.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurMigrationCompareRequest(
        String engine,
        String sourceUrl,
        String username,
        String password,
        String apiKey,
        String appId,
        String host,
        String index,
        String targetSite,
        String locale,
        Integer rows,
        List<String> queries) {
}
