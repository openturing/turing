/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.ranking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.rag.TurRagRrf;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T383 / §XX.3 — RRF hybrid ranking for the <strong>public</strong> Semantic
 * Navigation search.
 *
 * <p>Public SN documents are not embedded by the lexical search engine, so this
 * service maintains a parallel, per-site embedded vector collection
 * ({@code sn_<siteId>}) built on the same default embedding model + store the
 * RAG path uses (via {@link TurRagContextBuilder#buildFromGlobalSettings}). It
 * mirrors the SN indexing pipeline: every {@code indexDocument} / {@code deIndex}
 * on a {@link TurSNRankingMode#HYBRID_RRF} site also writes/removes the matching
 * vector.
 *
 * <p>At query time, {@link #fuse(TurSNSite, Locale, String, List)} takes the
 * lexical (BM25) result page, runs a vector pass over the site collection, and
 * fuses the two rankings with {@link TurRagRrf} (k=60 — one fusion
 * implementation shared with RAG). The fusion is restricted to the documents
 * already on the page, so facet counts and pagination stay BM25-derived — the
 * conservative, objective-ranking-preserving choice for a public catalog.
 *
 * <p>Everything here is <strong>fail-open</strong>: any failure (no default
 * embedding model configured, embedding error, vector store unavailable) logs a
 * WARN and degrades to the legacy lexical behaviour. A site left on
 * {@link TurSNRankingMode#LEGACY} never touches this service.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSNHybridRankingService {

    /** Vector candidate depth for the semantic pass before fusion. */
    private static final int VECTOR_TOP_K = 50;
    /** Bound the embedded text so a pathological document can't blow up an embed call. */
    private static final int MAX_EMBED_CHARS = 8_000;
    private static final String META_LOCALE = "snLocale";
    private static final String META_TYPE = "snType";
    /** Collection prefix isolating per-site SN vectors from the RAG "turing" collection. */
    private static final String COLLECTION_PREFIX = "sn_";

    private final TurRagContextBuilder ragContextBuilder;
    private final TurGlobalSettingsService globalSettingsService;

    /** Cached infra per (site, embeddingModel, store) so search latency doesn't pay a rebuild per query. */
    private final Map<String, RagInfrastructure> infraCache = new ConcurrentHashMap<>();

    public TurSNHybridRankingService(TurRagContextBuilder ragContextBuilder,
            TurGlobalSettingsService globalSettingsService) {
        this.ragContextBuilder = ragContextBuilder;
        this.globalSettingsService = globalSettingsService;
    }

    /**
     * Whether the site opted into hybrid ranking AND the platform has a default
     * embedding model + store configured (without those there is nothing to
     * embed against, so we stay on the legacy path).
     */
    public boolean isEnabled(TurSNSite turSNSite) {
        if (turSNSite == null) {
            return false;
        }
        TurSNSiteGenAi genAi = turSNSite.getTurSNSiteGenAi();
        if (genAi == null || genAi.getSnRankingMode() != TurSNRankingMode.HYBRID_RRF) {
            return false;
        }
        return StringUtils.hasText(globalSettingsService.getDefaultEmbeddingModelId())
                && StringUtils.hasText(globalSettingsService.getDefaultEmbeddingStoreId());
    }

    /**
     * Upserts the vector for one SN document. Called from the indexing pipeline
     * after the lexical index write succeeds, only for HYBRID_RRF sites.
     * Fail-open: errors are logged and swallowed so public indexing never breaks.
     */
    public void indexDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        if (!isEnabled(turSNSite) || attributes == null) {
            return;
        }
        String id = asString(attributes.get(TurSNFieldName.ID));
        if (!StringUtils.hasText(id)) {
            return;
        }
        String content = buildContent(attributes);
        if (!StringUtils.hasText(content)) {
            return;
        }
        try {
            RagInfrastructure infra = infraFor(turSNSite);
            if (infra == null) {
                return;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (locale != null) {
                metadata.put(META_LOCALE, locale.toString());
            }
            Optional.ofNullable(asString(attributes.get(TurSNFieldName.TYPE)))
                    .filter(StringUtils::hasText)
                    .ifPresent(type -> metadata.put(META_TYPE, type));
            Document doc = Document.builder().id(id).text(content).metadata(metadata).build();
            // Upsert: the vector store does not dedupe by id, so drop any prior copy first.
            safeDelete(infra, List.of(id));
            infra.vectorStore().add(List.of(doc));
        } catch (RuntimeException e) {
            log.warn("[SN-HYBRID] index of doc '{}' on site '{}' failed: {} — lexical index unaffected",
                    id, turSNSite.getName(), e.getMessage());
        }
    }

    /** Removes one document's vector by id. Fail-open. */
    public void deIndex(TurSNSite turSNSite, String id) {
        if (!isEnabled(turSNSite) || !StringUtils.hasText(id)) {
            return;
        }
        RagInfrastructure infra = infraFor(turSNSite);
        if (infra != null) {
            safeDelete(infra, List.of(id));
        }
    }

    /** Removes every vector of a given SN {@code type} (mirrors deIndexByType). Fail-open. */
    public void deIndexByType(TurSNSite turSNSite, String type) {
        if (!isEnabled(turSNSite) || !StringUtils.hasText(type)) {
            return;
        }
        try {
            RagInfrastructure infra = infraFor(turSNSite);
            if (infra != null) {
                infra.storeProvider().deleteByMetadata(infra.storeInstance(), infra.storeCredential(),
                        infra.collectionName(), META_TYPE, type);
            }
        } catch (RuntimeException e) {
            log.warn("[SN-HYBRID] deIndexByType '{}' on site '{}' failed: {}",
                    type, turSNSite.getName(), e.getMessage());
        }
    }

    /**
     * Reorders a lexical result page by fusing the BM25 ranking with a vector
     * ranking of the same documents via RRF. Returns the input unchanged on any
     * failure or when there is nothing meaningful to fuse.
     *
     * @param turSNSite the (snapshot) site — must be HYBRID_RRF
     * @param locale    request locale (unused for filtering today; the collection is per-site)
     * @param query     the user query text
     * @param page      the BM25-ordered result page
     * @return a new list with the page reordered by fused rank, or {@code page} as-is
     */
    public List<TurSEResult> fuse(TurSNSite turSNSite, Locale locale, String query, List<TurSEResult> page) {
        if (!isEnabled(turSNSite) || page == null || page.size() < 2 || !StringUtils.hasText(query)) {
            return page;
        }
        try {
            RagInfrastructure infra = infraFor(turSNSite);
            if (infra == null) {
                return page;
            }
            // Index page results by id so the fused id ordering can be mapped back.
            Map<String, TurSEResult> byId = new LinkedHashMap<>();
            List<Document> bm25Hits = new ArrayList<>(page.size());
            for (TurSEResult result : page) {
                String id = asString(result.getFields().get(TurSNFieldName.ID));
                if (StringUtils.hasText(id) && !byId.containsKey(id)) {
                    byId.put(id, result);
                    bm25Hits.add(Document.builder().id(id).text("").build());
                }
            }
            if (byId.size() < 2) {
                return page;
            }
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(VECTOR_TOP_K)
                    .similarityThreshold(0.0)
                    .build();
            List<Document> vectorAll = infra.vectorStore().similaritySearch(request);
            // Keep only vector hits that are actually on this page; fusion can only
            // reorder what BM25 already returned (facets/pagination stay BM25-derived).
            List<Document> vectorHits = new ArrayList<>();
            if (vectorAll != null) {
                for (Document d : vectorAll) {
                    if (d.getId() != null && byId.containsKey(d.getId())) {
                        vectorHits.add(d);
                    }
                }
            }
            if (vectorHits.isEmpty()) {
                return page;
            }
            List<Document> fused = TurRagRrf.fuse(vectorHits, bm25Hits, byId.size());
            List<TurSEResult> reordered = new ArrayList<>(page.size());
            for (Document d : fused) {
                TurSEResult result = byId.remove(d.getId());
                if (result != null) {
                    reordered.add(result);
                }
            }
            // Any page result the fusion didn't cover (shouldn't happen) keeps BM25 order at the tail.
            reordered.addAll(byId.values());
            return reordered;
        } catch (RuntimeException e) {
            log.warn("[SN-HYBRID] fusion on site '{}' failed: {} — returning lexical order",
                    turSNSite.getName(), e.getMessage());
            return page;
        }
    }

    /** Drops the cached infrastructure for a site (e.g. after deleting it). */
    public void evict(String siteId) {
        infraCache.keySet().removeIf(k -> k.startsWith(siteId + "|"));
    }

    private RagInfrastructure infraFor(TurSNSite turSNSite) {
        String embeddingModelId = globalSettingsService.getDefaultEmbeddingModelId();
        String storeId = globalSettingsService.getDefaultEmbeddingStoreId();
        if (!StringUtils.hasText(embeddingModelId) || !StringUtils.hasText(storeId)) {
            return null;
        }
        // Key on the defaults too: changing the platform embedding model/store
        // produces a new key and a fresh (correctly-configured) infra.
        String key = "%s|%s|%s".formatted(turSNSite.getId(), embeddingModelId, storeId);
        return infraCache.computeIfAbsent(key, k -> ragContextBuilder
                .build(embeddingModelId, storeId, COLLECTION_PREFIX + turSNSite.getId())
                .orElse(null));
    }

    private void safeDelete(RagInfrastructure infra, List<String> ids) {
        try {
            infra.vectorStore().delete(ids);
        } catch (RuntimeException e) {
            log.debug("[SN-HYBRID] delete {} skipped: {}", ids, e.getMessage());
        }
    }

    /**
     * Builds the text fed to the embedding model. Prefers the human-meaningful
     * fields (title, abstract, text) and falls back to concatenating every
     * String-valued attribute, capped at {@link #MAX_EMBED_CHARS}.
     */
    static String buildContent(Map<String, Object> attributes) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, attributes.get(TurSNFieldName.TITLE));
        appendField(sb, attributes.get(TurSNFieldName.ABSTRACT));
        appendField(sb, attributes.get(TurSNFieldName.TEXT));
        if (sb.isEmpty()) {
            attributes.forEach((k, v) -> {
                if (!TurSNFieldName.ID.equals(k) && !TurSNFieldName.URL.equals(k)) {
                    appendField(sb, v);
                }
            });
        }
        String content = sb.toString().trim();
        return content.length() > MAX_EMBED_CHARS ? content.substring(0, MAX_EMBED_CHARS) : content;
    }

    private static void appendField(StringBuilder sb, Object value) {
        String text = asString(value);
        if (StringUtils.hasText(text)) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(text);
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString)
                    .reduce((a, b) -> a + " " + b).orElse(null);
        }
        return value.toString();
    }
}
