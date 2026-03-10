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

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetCounts;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.*;

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

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Executes a search against the Lucene index and returns a fully
     * populated {@link TurSEResults}, including facet counts.
     *
     * <p>Uses {@link FacetsCollector#search} to collect both top hits and
     * facet data in a single index traversal when facets are enabled.
     */
    public TurSEResults getResults(TurLuceneInstance instance, TurSNSite turSNSite,
            Query query, TurSEParameters params, List<TurSNSiteFieldExt> facetFields,
            long startTime) {
        TurSEResults results = TurSEResults.builder().build();
        populateResultsParameters(params, results);

        try {
            IndexSearcher searcher = instance.getSearcher();
            int start = firstRow(params);
            int rows = params.getRows() > 0 ? params.getRows() : 10;
            // Collect enough hits to support pagination — must be >= 1
            int hitsNeeded = Math.max(1, start + rows);

            TopDocs topDocs;
            FacetsCollector fc = null;

            if (!facetFields.isEmpty()) {
                // Two passes: hits first, then facets. Keeps compatibility across Lucene versions.
                topDocs = searcher.search(query, hitsNeeded);
                fc = new FacetsCollector();
                searcher.search(query, fc);
            } else {
                topDocs = searcher.search(query, hitsNeeded);
            }

            long numFound = topDocs.totalHits.value();
            results.setNumFound(numFound);
            results.setStart((long) start);
            results.setPageCount((int) Math.ceil(numFound / (double) rows));

            // Map hits to TurSEResult (apply pagination offset)
            List<TurSEResult> resultList = new ArrayList<>();
            for (int i = start; i < Math.min(topDocs.scoreDocs.length, hitsNeeded); i++) {
                ScoreDoc sd = topDocs.scoreDocs[i];
                Document doc = searcher.storedFields().document(sd.doc);
                resultList.add(createTurSEResult(doc, sd.score));
            }
            results.setResults(resultList);

            // Compute facets
            if (fc != null && !facetFields.isEmpty()) {
                results.setFacetResults(computeFacets(instance, fc, facetFields));
            }

        } catch (IOException e) {
            log.error("Error executing Lucene search for site '{}'", turSNSite.getName(), e);
        }

        results.setElapsedTime(System.currentTimeMillis() - startTime);
        return results;
    }

    // -------------------------------------------------------------------------
    // Document → TurSEResult
    // -------------------------------------------------------------------------

    TurSEResult createTurSEResult(Document doc, float score) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (IndexableField f : doc.getFields()) {
            String name = f.name();
            // Skip Lucene-internal facet storage fields
            if (name.startsWith("$")) continue;
            Object newVal = extractStoredValue(f);
            Object existing = fields.get(name);
            if (existing == null) {
                fields.put(name, newVal);
            } else if (existing instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Object> mutable = new ArrayList<>((List<Object>) list);
                mutable.add(newVal);
                fields.put(name, mutable);
            } else {
                fields.put(name, new ArrayList<>(List.of(existing, newVal)));
            }
        }
        fields.put(TurLuceneConstants.SCORE, score);
        return TurSEResult.builder().fields(fields).build();
    }

    private Object extractStoredValue(IndexableField f) {
        Number num = f.numericValue();
        if (num != null) return num;
        String str = f.stringValue();
        return str != null ? str : "";
    }

    // -------------------------------------------------------------------------
    // Facet computation
    // -------------------------------------------------------------------------

    private List<TurSEFacetResult> computeFacets(TurLuceneInstance instance,
            FacetsCollector fc, List<TurSNSiteFieldExt> facetFields) {
        List<TurSEFacetResult> out = new ArrayList<>();
        try {
            DefaultSortedSetDocValuesReaderState state =
                    new DefaultSortedSetDocValuesReaderState(instance.getReader(),
                            instance.getFacetsConfig());
            SortedSetDocValuesFacetCounts counts = new SortedSetDocValuesFacetCounts(state, fc);

            for (TurSNSiteFieldExt fieldExt : facetFields) {
                String fieldName = fieldExt.getName();
                try {
                    FacetResult facetResult = counts.getTopChildren(MAX_FACET_VALUES, fieldName);
                    if (facetResult == null) continue;

                    TurSEFacetResult turFacet = new TurSEFacetResult();
                    turFacet.setFacet(fieldName);
                    turFacet.setFacetPosition(
                            fieldExt.getFacetPosition() != null ? fieldExt.getFacetPosition() : 0);

                    for (LabelAndValue lv : facetResult.labelValues) {
                        turFacet.add(lv.label, new TurSEFacetResultAttr(lv.label, lv.value.intValue()));
                    }
                    out.add(turFacet);
                } catch (IllegalArgumentException e) {
                    log.debug("No facet data for field '{}': {}", fieldName, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Error computing Lucene facets", e);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Index has no facets indexed yet, or $facets field type mismatch (re-index required)
            log.debug("SortedSetDocValues not available (re-index may be required): {}", e.getMessage());
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Pagination helpers
    // -------------------------------------------------------------------------

    private void populateResultsParameters(TurSEParameters params, TurSEResults results) {
        results.setLimit(params.getRows());
        results.setCurrentPage(params.getCurrentPage());
        results.setSort(params.getSort());
        results.setQueryString(params.getQuery());
    }

    private int firstRow(TurSEParameters params) {
        return Math.max(0, (params.getCurrentPage() - 1) * params.getRows());
    }
}
