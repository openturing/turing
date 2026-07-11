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

import java.util.Map;

/**
 * Pure, deterministic transform of an Elasticsearch {@code _source} document into
 * an SN job-item attribute map (T657 / §XXXVIII.1).
 *
 * <p>The reshape (dotted flattening, list-of-nested collapse, grounding drop of
 * absent/blank/empty values) is engine-agnostic and lives in
 * {@link TurMigrationDocumentMapper}; this type is the ES-flavoured entry point.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurEsDocumentMapper {

    private TurEsDocumentMapper() {
        // static-only
    }

    /**
     * Reshapes one document into an indexable attribute map.
     *
     * @param id       the document id (Elasticsearch {@code _id}).
     * @param source   the document {@code _source}.
     * @param type     the value stored under {@code type} (typically the index name).
     * @param provider the value stored under {@code source_apps} (the importer name).
     */
    public static Map<String, Object> toAttributes(String id, Map<String, Object> source, String type,
            String provider) {
        return TurMigrationDocumentMapper.toAttributes(id, source, type, provider);
    }
}
