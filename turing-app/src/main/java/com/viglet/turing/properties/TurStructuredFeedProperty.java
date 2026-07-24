/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * T791 / §LIV.2 (Block BF) — configuration for the scheduled
 * <strong>structured-feed pull ingester</strong>
 * ({@link com.viglet.turing.genai.feed.TurStructuredFeedIngestService}): the
 * "pull" side of ingestion for a Vectorless (Structured-Data) RAG knowledge base.
 *
 * <p>Opt-in and off by default: {@link #enabled} gates the scheduler, and with an
 * empty {@link #sources} list the ingester is a no-op even when enabled. Each
 * {@link Source} points at a remote JSON / NDJSON feed and (optionally) a field
 * manifest that declares the SN-site schema the records are indexed against.
 *
 * <pre>
 * turing:
 *   genai:
 *     structured-feed:
 *       enabled: true
 *       interval-ms: 3600000
 *       sources:
 *         - id: model-catalog
 *           site-name: model-catalog
 *           feed-url: https://openviglet.github.io/model-catalog/catalog.ndjson
 *           manifest-url: https://openviglet.github.io/model-catalog/query-manifest.json
 *           id-field: id
 *           locale: en_US
 * </pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.genai.structured-feed")
public class TurStructuredFeedProperty {

    /** Master switch for the scheduled ingester. Default {@code false} (off). */
    private boolean enabled = false;

    /** Delay before the first scheduled run, in milliseconds. */
    private long initialDelayMs = 30_000L;

    /** Fixed delay between scheduled runs, in milliseconds (default 1 hour). */
    private long intervalMs = 3_600_000L;

    /**
     * Safety cap on records mapped per source per run ({@code 0} = unlimited).
     * A silent truncation is logged, never hidden.
     */
    private int maxRecordsPerSource = 0;

    /**
     * T806 / §LV.4 (Block BG) — chunk size for the bounded bulk import: the
     * ingester splits a source's mapped records into groups of this many job
     * items and sends each group as its own bulk import (followed by a terminal
     * {@code COMMIT}), instead of one huge JMS message consumed in one long
     * transaction. Keeps message size and consumer-transaction size bounded for
     * catalog-scale feeds and gives per-chunk progress. {@code 0} (or negative) =
     * send everything in a single chunk (legacy behaviour). Default {@code 200}.
     */
    private int batchSize = 200;

    /** The feeds to pull. Empty (default) → the ingester does nothing. */
    private List<Source> sources = new ArrayList<>();

    /** One remote structured feed bound to one SN site. */
    @Getter
    @Setter
    public static class Source {

        /** Logical id used in logs and for per-source de-index tracking. Required. */
        private String id;

        /** Per-source enable flag (default {@code true}); lets one source be paused. */
        private boolean enabled = true;

        /** Target SN site name — must already exist. Required. */
        private String siteName;

        /** The JSON / NDJSON feed URL to GET. Required. */
        private String feedUrl;

        /**
         * Optional field-manifest URL: a JSON document declaring the fields the
         * records should be indexed as ({@code {schemaVersion, fields:[{name,type,
         * facet,multiValued,description}]}}). When set, the schema is converged
         * (idempotent, additive) before the records are imported.
         */
        private String manifestUrl;

        /** Locale for the imported documents. Default {@code en_US}. */
        private String locale = "en_US";

        /** Record field used as the document id. Default {@code id}. */
        private String idField = "id";

        /**
         * When the feed is a JSON <em>object</em> envelope rather than a top-level
         * array / NDJSON, the field holding the records. The value may be an array
         * of records, or an object whose values are arrays (e.g. the model
         * catalog's {@code vendors} map) — which is flattened. Optional.
         */
        private String recordsField;

        /**
         * T797 / §LIV.7 (Block BF) — flatten nested objects in each record into
         * queryable flat fields ({@code {pricing:{inputPer1M:5}}} →
         * {@code pricing_inputPer1M}), recursively; arrays of scalars stay
         * multi-valued. Default {@code false} keeps the legacy behaviour (nested
         * objects are dropped). Turn on for a feed whose decision fields live under
         * nested objects so they become searchable / citable.
         */
        private boolean flattenNested = false;

        /**
         * T796 / §LIV.8 (Block BF) — auto-provision the SN site at startup when it
         * does not yet exist: create the site (needs {@link #seInstanceId}) +
         * converge the schema + set the site to Vectorless mode + run the first
         * ingest, so a pure-env-var deployment yields a working vectorless KB with
         * no manual admin step. Default {@code false} (the ingester otherwise
         * requires the site to already exist).
         */
        private boolean provision = false;

        /**
         * T796 / §LIV.8 (Block BF) — the {@code TurSEInstance} id backing the SN
         * site when {@link #provision} creates it. Required to create a new site;
         * ignored once the site exists.
         */
        private String seInstanceId;

        /** T796 — optional description stamped on an auto-provisioned SN site. */
        private String description;
    }
}
