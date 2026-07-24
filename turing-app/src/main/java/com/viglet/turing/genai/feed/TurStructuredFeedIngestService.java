/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.feed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.sn.job.TurSNImportAPI;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.genai.urlfetch.TurUrlFetchService;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.properties.TurStructuredFeedProperty;
import com.viglet.turing.properties.TurStructuredFeedProperty.Source;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T791 / §LIV.2 (Block BF) — the scheduled <strong>structured-feed pull
 * ingester</strong>: the "pull" side of ingestion for a Vectorless
 * (Structured-Data) RAG knowledge base.
 *
 * <p>For each configured {@link Source} it:
 * <ol>
 *   <li>GETs the remote feed's <em>raw</em> body via
 *       {@link TurUrlFetchService#fetchRaw(String)} (the T739 SSRF/allowlist guard
 *       + timeout, but without the Tika text-extraction that would mangle JSON),
 *       accepting a top-level JSON array, a JSON object envelope (records under a
 *       configured field, flattening a map-of-arrays such as the model catalog's
 *       {@code vendors}), or newline-delimited JSON ({@code catalog.ndjson});</li>
 *   <li>optionally converges the SN-site field schema from a field manifest
 *       (idempotent, additive — never destructive) via {@link TurSNFieldProvisioner};</li>
 *   <li>maps each record's scalar/array fields to {@link TurSNJobItem} attributes
 *       (honouring the T381 grounding contract — an empty value is omitted, never
 *       indexed as empty) and imports them through the existing
 *       {@link TurSNImportAPI#send(TurSNJobItems)} pipeline;</li>
 *   <li>de-indexes ids that vanished from the feed since the previous run.</li>
 * </ol>
 *
 * <p><b>De-index tracking is in-memory and per-node</b> ({@link #lastSeenIds}): a
 * restart re-syncs from scratch, so a deletion that happened while the node was
 * down is only reconciled once that id is observed missing on a subsequent run —
 * an acceptable best-effort for a periodic mirror. The whole path is fail-open:
 * a missing site, an unreachable feed, or an unparseable body logs and returns a
 * failed {@link IngestResult} rather than throwing.
 *
 * <p>Opt-in and off by default (see {@link TurStructuredFeedProperty}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurStructuredFeedIngestService {

    /** Common envelope field names probed when no explicit {@code recordsField} is set. */
    private static final List<String> ENVELOPE_FIELDS = List.of("data", "items", "records", "models", "vendors");

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @Value("${turing.genai.structured-feed.enabled:false}")
    private boolean scheduleEnabled;

    private final TurStructuredFeedProperty property;
    private final TurUrlFetchService urlFetchService;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNFieldProvisioner fieldProvisioner;
    private final TurSNImportAPI turSNImportAPI;

    /** Per-source set of ids seen on the previous successful run (de-index diff). */
    private final Map<String, Set<String>> lastSeenIds = new ConcurrentHashMap<>();

    public TurStructuredFeedIngestService(TurStructuredFeedProperty property,
            TurUrlFetchService urlFetchService,
            TurSNSiteRepository turSNSiteRepository,
            TurSNFieldProvisioner fieldProvisioner,
            TurSNImportAPI turSNImportAPI) {
        this.property = property;
        this.urlFetchService = urlFetchService;
        this.turSNSiteRepository = turSNSiteRepository;
        this.fieldProvisioner = fieldProvisioner;
        this.turSNImportAPI = turSNImportAPI;
    }

    /**
     * Cluster-once scheduled sweep of every enabled source. Off the boot path;
     * gated by {@code turing.genai.structured-feed.enabled}. Best-effort per
     * source — one failing feed never aborts the others.
     */
    @Scheduled(
            initialDelayString = "${turing.genai.structured-feed.initial-delay-ms:30000}",
            fixedDelayString = "${turing.genai.structured-feed.interval-ms:3600000}")
    @SchedulerLock(name = "structuredFeedIngest", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1S")
    public void ingestAll() {
        if (!scheduleEnabled) {
            return;
        }
        List<Source> sources = property.getSources();
        if (sources == null || sources.isEmpty()) {
            return;
        }
        for (Source source : sources) {
            if (source == null || !source.isEnabled()) {
                continue;
            }
            try {
                IngestResult result = ingestSource(source);
                log.info("[StructuredFeed] source '{}' → {}", source.getId(), result);
            } catch (RuntimeException e) {
                log.warn("[StructuredFeed] source '{}' failed: {}",
                        source == null ? "?" : source.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Ingest one source now. Never throws — returns a failed {@link IngestResult}
     * on any recoverable problem (missing site, unreachable feed, unparseable
     * body). Public so an admin trigger / test can drive a single source.
     */
    public IngestResult ingestSource(Source source) {
        if (source == null || StringUtils.isBlank(source.getSiteName())
                || StringUtils.isBlank(source.getFeedUrl())) {
            return IngestResult.failed("source is missing siteName or feedUrl");
        }
        String sourceId = StringUtils.defaultIfBlank(source.getId(), source.getSiteName());
        TurSNSite site = turSNSiteRepository.findByNameIgnoreCase(source.getSiteName()).orElse(null);
        if (site == null) {
            return IngestResult.failed("SN site not found: " + source.getSiteName());
        }

        // 1. Converge the declared field schema from the manifest (optional).
        int fieldsDeclared = 0;
        if (StringUtils.isNotBlank(source.getManifestUrl())) {
            fieldsDeclared = provisionSchema(site, source.getManifestUrl());
        }

        // 2. Fetch + parse the feed into records.
        String body = urlFetchService.fetchRaw(source.getFeedUrl());
        if (StringUtils.isBlank(body)) {
            return IngestResult.failed("empty or unreachable feed: " + source.getFeedUrl());
        }
        List<JsonNode> records = parseRecords(body, source.getRecordsField());
        if (records.isEmpty()) {
            return IngestResult.failed("no records parsed from feed: " + source.getFeedUrl());
        }

        // 3. Map records → job items, tracking the ids we saw this run.
        Locale locale = safeLocale(source.getLocale());
        String idField = StringUtils.defaultIfBlank(source.getIdField(), TurSNFieldName.ID);
        int cap = property.getMaxRecordsPerSource();
        List<TurSNJobItem> items = new ArrayList<>();
        Set<String> currentIds = new LinkedHashSet<>();
        int mapped = 0;
        int skipped = 0;
        for (JsonNode record : records) {
            if (cap > 0 && mapped >= cap) {
                log.warn("[StructuredFeed] source '{}' hit maxRecordsPerSource={} — {} records dropped",
                        sourceId, cap, records.size() - mapped);
                break;
            }
            String id = idOf(record, idField);
            if (id == null || currentIds.contains(id)) {
                skipped++;
                continue;
            }
            currentIds.add(id);
            items.add(new TurSNJobItem(TurSNJobAction.CREATE, List.of(source.getSiteName()),
                    locale, toAttributes(record, id, source.isFlattenNested())));
            mapped++;
        }

        // 4. De-index ids that vanished since the previous run (diff over the full
        //    run — chunking below never changes which ids are reconciled).
        List<TurSNJobItem> deletions = buildDeletions(sourceId, source.getSiteName(), locale, currentIds);
        items.addAll(deletions);

        // 5. T806 / §LV.4 — import in bounded chunks, each its own bulk import + a
        //    terminal COMMIT, with per-chunk progress logging and per-chunk
        //    fail-open (one bad chunk never aborts the rest).
        dispatchInChunks(source.getSiteName(), locale, items, sourceId);
        lastSeenIds.put(sourceId, currentIds);
        return new IngestResult(true, mapped, deletions.size(), skipped, fieldsDeclared, null);
    }

    /**
     * T806 / §LV.4 — splits {@code items} into {@code batchSize}-sized chunks and
     * sends each as its own bulk import, appending a terminal {@code COMMIT} so the
     * chunk's writes are flushed before the next chunk. Per-chunk fail-open: a
     * failing chunk is logged and skipped, never aborting the remaining chunks.
     */
    private void dispatchInChunks(String siteName, Locale locale, List<TurSNJobItem> items,
            String sourceId) {
        if (items.isEmpty()) {
            return;
        }
        List<List<TurSNJobItem>> chunks = partition(items, property.getBatchSize());
        int total = chunks.size();
        for (int i = 0; i < total; i++) {
            List<TurSNJobItem> chunk = chunks.get(i);
            try {
                TurSNJobItems jobItems = new TurSNJobItems();
                chunk.forEach(jobItems::add);
                jobItems.add(new TurSNJobItem(TurSNJobAction.COMMIT, List.of(siteName), locale));
                turSNImportAPI.send(jobItems);
                log.info("[StructuredFeed] source '{}' chunk {}/{} sent ({} items)",
                        sourceId, i + 1, total, chunk.size());
            } catch (RuntimeException e) {
                log.warn("[StructuredFeed] source '{}' chunk {}/{} failed ({} items): {}",
                        sourceId, i + 1, total, chunk.size(), e.getMessage());
            }
        }
    }

    /**
     * Partitions {@code list} into consecutive sublists of at most {@code size}
     * elements. A non-positive {@code size} yields a single chunk (legacy
     * unbounded behaviour). The returned sublists are views over {@code list}.
     */
    static <T> List<List<T>> partition(List<T> list, int size) {
        if (list.isEmpty()) {
            return List.of();
        }
        if (size <= 0) {
            return List.of(list);
        }
        List<List<T>> parts = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            parts.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return parts;
    }

    // ─────────────────────────── Schema convergence ───────────────────────────

    private int provisionSchema(TurSNSite site, String manifestUrl) {
        String body = urlFetchService.fetchRaw(manifestUrl);
        if (StringUtils.isBlank(body)) {
            log.warn("[StructuredFeed] manifest unreachable, skipping schema convergence: {}", manifestUrl);
            return 0;
        }
        JsonNode manifest;
        try {
            manifest = OBJECT_MAPPER.readTree(body);
        } catch (JacksonException e) {
            log.warn("[StructuredFeed] manifest not valid JSON, skipping: {} ({})", manifestUrl, e.getMessage());
            return 0;
        }
        JsonNode fields = manifest.get("fields");
        if (fields == null || !fields.isArray()) {
            log.warn("[StructuredFeed] manifest has no 'fields' array, skipping: {}", manifestUrl);
            return 0;
        }
        int declared = 0;
        for (JsonNode field : fields) {
            String name = text(field.get("name"));
            if (StringUtils.isBlank(name)) {
                continue;
            }
            TurSNJobAttributeSpec spec = TurSNJobAttributeSpec.builder()
                    .name(name)
                    .type(parseFieldType(text(field.get("type"))))
                    .facet(field.path("facet").asBoolean(false))
                    .multiValued(field.path("multiValued").asBoolean(false))
                    .mandatory(field.path("mandatory").asBoolean(false))
                    .description(text(field.get("description")))
                    .build();
            try {
                if (fieldProvisioner.ensureField(site, spec)) {
                    declared++;
                }
            } catch (RuntimeException e) {
                log.warn("[StructuredFeed] failed to declare field '{}' on site '{}': {}",
                        name, site.getName(), e.getMessage());
            }
        }
        return declared;
    }

    private static TurSEFieldType parseFieldType(String raw) {
        if (StringUtils.isBlank(raw)) {
            return TurSEFieldType.TEXT;
        }
        try {
            return TurSEFieldType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TurSEFieldType.TEXT;
        }
    }

    // ─────────────────────────── Feed parsing ───────────────────────────

    /**
     * Parse a feed body into a flat list of record nodes. Tries a single JSON tree
     * first (array, or object envelope) and falls back to newline-delimited JSON
     * when the whole body is not one JSON value.
     */
    List<JsonNode> parseRecords(String body, String recordsField) {
        String trimmed = body.strip();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(trimmed);
            return recordsFromTree(root, recordsField);
        } catch (JacksonException e) {
            return parseNdjson(trimmed);
        }
    }

    private List<JsonNode> recordsFromTree(JsonNode root, String recordsField) {
        List<JsonNode> out = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return out;
        }
        if (root.isArray()) {
            addObjects(root, out);
            return out;
        }
        if (root.isObject()) {
            JsonNode container = resolveContainer(root, recordsField);
            if (container != null) {
                if (container.isArray()) {
                    addObjects(container, out);
                } else if (container.isObject()) {
                    // map-of-arrays (e.g. the model catalog's vendors map) → flatten
                    for (Map.Entry<String, JsonNode> entry : container.properties()) {
                        if (entry.getValue().isArray()) {
                            addObjects(entry.getValue(), out);
                        }
                    }
                }
            }
        }
        return out;
    }

    private JsonNode resolveContainer(JsonNode root, String recordsField) {
        if (StringUtils.isNotBlank(recordsField) && root.has(recordsField)) {
            return root.get(recordsField);
        }
        for (String candidate : ENVELOPE_FIELDS) {
            JsonNode node = root.get(candidate);
            if (node != null && (node.isArray() || node.isObject())) {
                return node;
            }
        }
        return null;
    }

    private List<JsonNode> parseNdjson(String body) {
        List<JsonNode> out = new ArrayList<>();
        for (String line : body.split("\\r?\\n")) {
            String l = line.strip();
            if (l.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = OBJECT_MAPPER.readTree(l);
                if (node != null && node.isObject()) {
                    out.add(node);
                }
            } catch (JacksonException e) {
                log.debug("[StructuredFeed] skipping non-JSON NDJSON line: {}", e.getMessage());
            }
        }
        return out;
    }

    private static void addObjects(JsonNode array, List<JsonNode> out) {
        for (JsonNode node : array) {
            if (node != null && node.isObject()) {
                out.add(node);
            }
        }
    }

    // ─────────────────────────── Record → attributes ───────────────────────────

    private String idOf(JsonNode record, String idField) {
        String id = text(record.get(idField));
        return StringUtils.isBlank(id) ? null : id;
    }

    /**
     * Project a record's scalar / array fields into an attribute map. Honours the
     * T381 grounding contract: an absent, null, blank, or empty value is omitted,
     * never materialized as an empty attribute. The id is always written under
     * {@link TurSNFieldName#ID}.
     *
     * <p>T797 / §LIV.7 — when {@code flatten} is {@code true}, nested objects are
     * flattened into {@code parent_child} keys (recursively) so their fields become
     * queryable; otherwise a nested object is dropped (legacy behaviour). Arrays of
     * scalars are preserved as multi-valued either way.
     */
    Map<String, Object> toAttributes(JsonNode record, String id, boolean flatten) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.ID, id);
        for (Map.Entry<String, JsonNode> entry : record.properties()) {
            String key = entry.getKey();
            if (TurSNFieldName.ID.equals(key)) {
                continue;
            }
            addField(key, entry.getValue(), attributes, flatten);
        }
        return attributes;
    }

    /**
     * Add one field to the attribute map. In flatten mode a nested object recurses
     * with a {@code parent_child} key prefix; otherwise objects fall through to
     * {@link #scalarOf} (which drops them). Empty / blank values are omitted.
     */
    private void addField(String key, JsonNode value, Map<String, Object> attributes, boolean flatten) {
        if (flatten && value != null && value.isObject()) {
            for (Map.Entry<String, JsonNode> child : value.properties()) {
                addField(key + "_" + child.getKey(), child.getValue(), attributes, true);
            }
            return;
        }
        Object scalar = scalarOf(value);
        if (scalar != null) {
            attributes.put(key, scalar);
        }
    }

    /**
     * Convert a JSON value to an indexable attribute, or {@code null} to omit it.
     * Textual/blank → omitted; numbers/booleans kept as typed values; arrays kept
     * as a {@link List} of their scalar elements (empty → omitted); objects omitted.
     */
    private Object scalarOf(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode element : node) {
                Object element0 = scalarOf(element);
                if (element0 != null) {
                    values.add(element0);
                }
            }
            return values.isEmpty() ? null : values;
        }
        if (node.isObject()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        String textValue = node.asString();
        return StringUtils.isBlank(textValue) ? null : textValue;
    }

    // ─────────────────────────── De-index ───────────────────────────

    private List<TurSNJobItem> buildDeletions(String sourceId, String siteName,
            Locale locale, Set<String> currentIds) {
        Set<String> previous = lastSeenIds.get(sourceId);
        if (previous == null || previous.isEmpty()) {
            return List.of();
        }
        List<TurSNJobItem> deletions = new ArrayList<>();
        for (String previousId : previous) {
            if (!currentIds.contains(previousId)) {
                Map<String, Object> attributes = new HashMap<>();
                attributes.put(TurSNFieldName.ID, previousId);
                deletions.add(new TurSNJobItem(TurSNJobAction.DELETE, List.of(siteName),
                        locale, attributes));
            }
        }
        return deletions;
    }

    private static String text(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asString();
    }

    private static Locale safeLocale(String locale) {
        try {
            return StringUtils.isBlank(locale) ? Locale.ENGLISH : LocaleUtils.toLocale(locale);
        } catch (IllegalArgumentException e) {
            return Locale.ENGLISH;
        }
    }

    /**
     * Outcome of one source ingest: how many records were mapped/imported,
     * de-indexed (vanished ids), skipped (no id / duplicate), and fields declared
     * from the manifest. {@code error} is null on success.
     */
    public record IngestResult(boolean success, int imported, int deIndexed, int skipped,
            int fieldsDeclared, String error) {

        static IngestResult failed(String error) {
            return new IngestResult(false, 0, 0, 0, 0, error);
        }
    }
}
