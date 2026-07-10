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

package com.viglet.turing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the source-engine migration importers (Block AO / §XXXVIII).
 *
 * <p>Tunes how the Elasticsearch (T657) and Algolia (T658) importers read a
 * source index and push the reshaped records through the existing import queue:
 * the batch size, the source-engine HTTP timeout, the optional document cap, the
 * Elasticsearch scroll keep-alive window, and how many documents are sampled to
 * detect multi-valued fields when a source schema does not declare cardinality.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.migration")
public class TurMigrationProperty {

    /**
     * Documents reshaped and pushed per import batch. Each batch is one send to
     * the indexing queue; a trailing COMMIT flushes the search engine at the end.
     */
    private int batchSize = 500;

    /** Connect/read timeout (seconds) for every REST call to the source engine. */
    private int timeoutSeconds = 30;

    /**
     * Hard cap on documents imported in a single migration run; {@code 0} means no
     * cap (import the whole index).
     */
    private int maxDocuments = 0;

    /**
     * The Elasticsearch scroll context keep-alive window (e.g. {@code "1m"}) kept
     * open on the source cluster between pages.
     */
    private String esScrollKeepAlive = "1m";

    /**
     * Number of documents sampled from the first page to detect multi-valued
     * fields, since neither an Elasticsearch {@code _mapping} nor Algolia settings
     * declare whether a field holds a collection.
     */
    private int sampleSize = 20;
}
