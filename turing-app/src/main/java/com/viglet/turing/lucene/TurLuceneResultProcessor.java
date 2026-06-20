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
package com.viglet.turing.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Converts Lucene {@link TopDocs} and facet data into {@link TurSEResults}.
 * Mirrors {@code TurSolrResultProcessor} for the Lucene engine.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class TurLuceneResultProcessor {

    private static final int MAX_FACET_VALUES = 100;
    private static final Set<TurSEFieldType> HL_FIELD_TYPES = Collections
            .unmodifiableSet(EnumSet.of(TurSEFieldType.TEXT, TurSEFieldType.STRING));

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Executes a search against the Lucene index and returns a fully
     * populated {@link TurSEResults}, including facet counts and highlighting.
     *
     * <p>
     * Uses {@link FacetsCollector} to collect matched documents across all
     * index segments; facet values are then counted directly from per-field
     * {@link SortedSetDocValues} without relying on FacetsConfig internals.
     */
    public TurSEResults getResults(TurLuceneSearch search, List<TurSNSiteFieldExt> facetFields,
            List<TurSNSiteFieldExt> hlFields, org.apache.lucene.search.Sort sort,
            long startTime) {
        TurLuceneInstance instance = search.instance();
        TurSNSite turSNSite = search.turSNSite();
        Query query = search.query();
        TurSEParameters params = search.params();
        TurSEResults results = TurSEResults.builder().build();
        populateResultsParameters(params, results);

        try {
            IndexSearcher searcher = instance.getSearcher();
            // Resolve the effective page size first (mirrors TurSolrQueryBuilder#setRows):
            // when the request omits "rows" it arrives negative/null, so fall back to the
            // site's rowsPerPage. The offset MUST be computed from this same value —
            // otherwise (page-1)*rawRows collapses to 0 and every page shows page 1.
            int rows = effectiveRows(params, turSNSite);
            int start = firstRow(params, rows);
            results.setLimit(rows);
            // Collect enough hits to support pagination — must be >= 1
            int hitsNeeded = Math.max(1, start + rows);

            TopDocs topDocs;
            FacetsCollector fc = null;

            if (!facetFields.isEmpty()) {
                // Two passes: hits first, then facets across all matching docs.
                topDocs = sort != null
                        ? searcher.search(query, hitsNeeded, sort)
                        : searcher.search(query, hitsNeeded);
                fc = searcher.search(query, new FacetsCollectorManager());
            } else {
                topDocs = sort != null
                        ? searcher.search(query, hitsNeeded, sort)
                        : searcher.search(query, hitsNeeded);
            }

            long numFound = topDocs.totalHits.value();
            results.setNumFound(numFound);
            results.setStart(start);
            results.setPageCount((int) Math.ceil(numFound / (double) rows));

            // Prepare highlighting
            Map<String, Map<String, String>> hlByDoc = buildHighlighting(
                    searcher, query, topDocs, turSNSite, hlFields);

            // Map hits to TurSEResult (apply pagination offset)
            List<TurSEResult> resultList = new ArrayList<>();
            for (int i = start; i < Math.min(topDocs.scoreDocs.length, hitsNeeded); i++) {
                ScoreDoc sd = topDocs.scoreDocs[i];
                Document doc = searcher.storedFields().document(sd.doc);
                String docId = doc.get(TurLuceneConstants.ID);
                Map<String, String> docHL = docId != null ? hlByDoc.getOrDefault(docId, Map.of()) : Map.of();
                resultList.add(createTurSEResult(doc, sd.score, docHL, hlFields));
            }
            results.setResults(resultList);

            // Compute facets directly from per-field SortedSetDocValues
            if (fc != null && !facetFields.isEmpty()) {
                results.setFacetResults(computeFacets(fc, facetFields));
            }

        } catch (IOException e) {
            log.error("Error executing Lucene search for site '{}'", turSNSite.getName(), e);
        }

        results.setElapsedTime(System.currentTimeMillis() - startTime);
        return results;
    }

    // -------------------------------------------------------------------------
    // Highlighting
    // -------------------------------------------------------------------------

    private Map<String, Map<String, String>> buildHighlighting(IndexSearcher searcher,
            Query query, TopDocs topDocs, TurSNSite turSNSite,
            List<TurSNSiteFieldExt> hlFields) {
        if (!isHL(turSNSite, hlFields) || topDocs.scoreDocs.length == 0) {
            return Map.of();
        }

        Map<String, Map<String, String>> hlByDoc = new LinkedHashMap<>();
        String preTags = turSNSite.getHlPre() != null ? turSNSite.getHlPre() : "<em>";
        String postTags = turSNSite.getHlPost() != null ? turSNSite.getHlPost() : "</em>";

        HlTagPair tags = new HlTagPair(preTags, postTags);
        try {
            UnifiedHighlighter highlighter = new UnifiedHighlighter.Builder(searcher, new StandardAnalyzer())
                    .build();

            for (TurSNSiteFieldExt hlField : hlFields) {
                if (hlField.getType() == null || !HL_FIELD_TYPES.contains(hlField.getType())) {
                    continue;
                }
                highlightField(highlighter, hlField.getName(), query, topDocs, searcher, hlByDoc, tags);
            }
        } catch (Exception e) {
            log.warn("Error building highlighting: {}", e.getMessage());
        }

        return hlByDoc;
    }

    /** Configured pre/post highlight tags. */
    private record HlTagPair(String preTags, String postTags) {
    }

    /**
     * Highlights one field across the hit docs, writing the rewritten snippet
     * (default {@code <b>} tags swapped for the configured pair) under each
     * doc id in {@code hlByDoc}. Per-field failures are logged and skipped.
     */
    private void highlightField(UnifiedHighlighter highlighter, String fieldName, Query query,
            TopDocs topDocs, IndexSearcher searcher, Map<String, Map<String, String>> hlByDoc,
            HlTagPair tags) {
        try {
            String[] snippets = highlighter.highlight(fieldName, query, topDocs, 1);
            if (snippets == null) return;
            for (int i = 0; i < snippets.length && i < topDocs.scoreDocs.length; i++) {
                if (snippets[i] == null) continue;
                // Replace default <b>/</b> tags with configured HL tags
                String highlighted = snippets[i]
                        .replace("<b>", tags.preTags())
                        .replace("</b>", tags.postTags());
                Document doc = searcher.storedFields().document(topDocs.scoreDocs[i].doc);
                String docId = doc.get(TurLuceneConstants.ID);
                if (docId != null) {
                    hlByDoc.computeIfAbsent(docId, k -> new LinkedHashMap<>())
                            .put(fieldName, highlighted);
                }
            }
        } catch (Exception e) {
            log.debug("Could not highlight field '{}': {}", fieldName, e.getMessage());
        }
    }

    static boolean isHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> hlFields) {
        return TurSNUtils.isTrue(turSNSite.getHl()) && hlFields != null && !hlFields.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Document → TurSEResult
    // -------------------------------------------------------------------------

    TurSEResult createTurSEResult(Document doc, float score,
            Map<String, String> docHL, List<TurSNSiteFieldExt> hlFields) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, TurSEFieldType> hlFieldTypes = buildHlFieldTypes(hlFields);

        for (IndexableField f : doc.getFields()) {
            String name = f.name();
            // Skip Lucene-internal facet storage fields
            if (name.startsWith("$"))
                continue;

            Object newVal = extractStoredValue(f);

            // Apply highlighting if available for this field
            if (docHL.containsKey(name) && isHLAttribute(hlFieldTypes, name)) {
                newVal = docHL.get(name);
            }

            mergeFieldValue(fields, name, newVal);
        }
        fields.put(TurLuceneConstants.SCORE, score);
        return TurSEResult.builder().fields(fields).build();
    }

    private static Map<String, TurSEFieldType> buildHlFieldTypes(List<TurSNSiteFieldExt> hlFields) {
        Map<String, TurSEFieldType> hlFieldTypes = new LinkedHashMap<>();
        if (hlFields != null) {
            for (TurSNSiteFieldExt f : hlFields) {
                if (f.getType() != null) {
                    hlFieldTypes.put(f.getName(), f.getType());
                }
            }
        }
        return hlFieldTypes;
    }

    /**
     * Merges {@code newVal} into {@code fields} under {@code name}, promoting
     * to a mutable list when the field already holds one or more values.
     */
    private static void mergeFieldValue(Map<String, Object> fields, String name, Object newVal) {
        Object existing = fields.get(name);
        if (existing == null) {
            fields.put(name, newVal);
        } else if (existing instanceof List<?> list) {
            List<Object> mutable = new ArrayList<>(list);
            mutable.add(newVal);
            fields.put(name, mutable);
        } else {
            fields.put(name, new ArrayList<>(List.of(existing, newVal)));
        }
    }

    private static boolean isHLAttribute(Map<String, TurSEFieldType> hlFieldTypes, String attribute) {
        TurSEFieldType type = hlFieldTypes.get(attribute);
        return type != null && HL_FIELD_TYPES.contains(type);
    }

    private Object extractStoredValue(IndexableField f) {
        Number num = f.numericValue();
        if (num != null)
            return num;
        String str = f.stringValue();
        return str != null ? str : "";
    }

    // -------------------------------------------------------------------------
    // Facet computation
    // -------------------------------------------------------------------------

    /**
     * Counts facet values directly from per-field {@link SortedSetDocValues} across
     * all segments that had matching documents. This avoids the
     * {@code FacetsConfig}
     * / {@code $facets} internal field entirely, making it robust across index
     * updates and Lucene version differences.
     */
    private List<TurSEFacetResult> computeFacets(FacetsCollector fc,
            List<TurSNSiteFieldExt> facetFields) {
        List<TurSEFacetResult> out = new ArrayList<>();
        List<FacetsCollector.MatchingDocs> matchingDocsList = fc.getMatchingDocs();
        log.debug("[Lucene] computeFacets: {} facet fields, {} matching segments",
                facetFields.size(), matchingDocsList.size());

        for (TurSNSiteFieldExt fieldExt : facetFields) {
            TurSEFacetResult turFacet = processFacetField(fieldExt, matchingDocsList);
            if (turFacet != null) {
                out.add(turFacet);
            }
        }

        return out;
    }

    /**
     * Processes a single facet field by counting values across all matching
     * segments.
     *
     * @param fieldExt         the facet field definition
     * @param matchingDocsList list of matching docs from all segments
     * @return a {@link TurSEFacetResult} or null if no facet values found
     */
    private TurSEFacetResult processFacetField(TurSNSiteFieldExt fieldExt,
            List<FacetsCollector.MatchingDocs> matchingDocsList) {
        String fieldName = fieldExt.getName();
        // Numeric types (INT, LONG, FLOAT, DOUBLE, DATE) store SortedSetDocValues under a
        // _sf-suffixed field to avoid conflict with their NumericDocValuesField.
        TurSEFieldType type = fieldExt.getType();
        String docValuesFieldName = usesNumericDocValues(type)
                ? fieldName + TurLuceneConstants.SORTED_SET_FACET_SUFFIX
                : fieldName;
        Map<String, Integer> counts = countFacetValues(fieldName, docValuesFieldName, matchingDocsList);

        log.debug("[Lucene] facet '{}': {} distinct values", fieldName, counts.size());
        if (counts.isEmpty()) {
            return null;
        }

        TurSEFacetResult turFacet = new TurSEFacetResult();
        turFacet.setFacet(fieldName);
        turFacet.setFacetPosition(
                fieldExt.getFacetPosition() != null ? fieldExt.getFacetPosition() : 0);

        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_FACET_VALUES)
                .forEach(e -> turFacet.add(e.getKey(),
                        new TurSEFacetResultAttr(e.getKey(), e.getValue())));

        return turFacet;
    }

    /**
     * Counts facet values for a given field across all matching document segments.
     *
     * @param fieldName          the facet field name
     * @param docValuesFieldName the actual field name for SortedSetDocValues
     * @param matchingDocsList   list of matching docs from all segments
     * @return a map of facet values to their counts
     */
    private Map<String, Integer> countFacetValues(String fieldName, String docValuesFieldName,
            List<FacetsCollector.MatchingDocs> matchingDocsList) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (FacetsCollector.MatchingDocs matchingDocs : matchingDocsList) {
            processSegmentFacets(fieldName, docValuesFieldName, matchingDocs, counts);
        }

        return counts;
    }

    /**
     * Processes facet values for a single segment, handling null checks and errors.
     *
     * @param fieldName          the facet field name
     * @param docValuesFieldName the actual field name for SortedSetDocValues
     * @param matchingDocs       the matching docs for a segment
     * @param counts             the map accumulating counts
     */
    private void processSegmentFacets(String fieldName, String docValuesFieldName,
            FacetsCollector.MatchingDocs matchingDocs, Map<String, Integer> counts) {
        try {
            SortedSetDocValues docValues = matchingDocs.context().reader()
                    .getSortedSetDocValues(docValuesFieldName);
            if (docValues == null) {
                log.debug("[Lucene] field '{}' has no SortedSetDocValues in segment", fieldName);
                return;
            }

            DocIdSetIterator disi = matchingDocs.bits().iterator();
            if (disi == null) {
                return;
            }

            countValuesInSegment(docValues, disi, counts);
        } catch (IOException e) {
            log.warn("Error reading facets for field '{}' in segment: {}", fieldName,
                    e.getMessage());
        }
    }

    /**
     * Counts facet values for a single segment.
     *
     * @param docValues the SortedSetDocValues for the field
     * @param disi      the document ID set iterator for matching docs
     * @param counts    the map accumulating counts
     * @throws IOException if an error occurs reading doc values
     */
    private void countValuesInSegment(SortedSetDocValues docValues, DocIdSetIterator disi,
            Map<String, Integer> counts) throws IOException {
        int docId;
        while ((docId = disi.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (docValues.advanceExact(docId)) {
                int count = docValues.docValueCount();
                for (int i = 0; i < count; i++) {
                    String label = docValues.lookupOrd(docValues.nextOrd()).utf8ToString();
                    counts.merge(label, 1, (existing, one) -> existing + one);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pagination helpers
    // -------------------------------------------------------------------------

    private static boolean usesNumericDocValues(TurSEFieldType type) {
        return type == TurSEFieldType.INT || type == TurSEFieldType.LONG
                || type == TurSEFieldType.FLOAT || type == TurSEFieldType.DOUBLE
                || type == TurSEFieldType.CURRENCY || type == TurSEFieldType.DATE;
    }

    private void populateResultsParameters(TurSEParameters params, TurSEResults results) {
        // limit is set in getResults() from the effective rows (see effectiveRows)
        results.setCurrentPage(params.getCurrentPage());
        results.setSort(params.getSort());
        results.setQueryString(params.getQuery());
    }

    /**
     * Resolves the effective page size. When the request omits {@code rows} it
     * arrives null/negative, so fall back to the site's {@code rowsPerPage}
     * (mirrors {@code TurSolrQueryBuilder#setRows}).
     */
    private int effectiveRows(TurSEParameters params, TurSNSite turSNSite) {
        Integer rows = params.getRows();
        if (rows != null && rows > 0) {
            return rows;
        }
        Integer rowsPerPage = turSNSite.getRowsPerPage();
        return (rowsPerPage != null && rowsPerPage > 0) ? rowsPerPage : 10;
    }

    private int firstRow(TurSEParameters params, int rows) {
        Integer page = params.getCurrentPage();
        int currentPage = (page != null && page > 0) ? page : 1;
        return Math.max(0, (currentPage - 1) * rows);
    }
}
