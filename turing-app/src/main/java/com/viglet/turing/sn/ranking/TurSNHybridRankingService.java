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

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.TurRagContextBuilder.RagInfrastructure;
import com.viglet.turing.genai.rag.TurRagReranker;
import com.viglet.turing.genai.rag.TurRagRrf;
import com.viglet.turing.genai.rag.rerank.TurRagRerankStrategyType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.system.TurDefaultChatModelResolver;
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
 * <p><strong>T389 / §XX.9</strong> generalizes this into a configurable
 * {@code retrieve → fuse → rerank} pipeline. {@link TurSNRankingMode#HYBRID_RRF}
 * stops after fusion; {@link TurSNRankingMode#HYBRID_RRF_RERANK} runs the fused
 * page once more through the Block N {@link TurRagReranker} strategy seam
 * (LLM | CROSS_ENCODER | COHERE). Both modes also attach a per-result
 * <em>ranking explanation</em> (lexical rank, semantic rank, fused rank, RRF
 * score, final rank, reranker delta) so the objective-ranking claim is auditable
 * — no commercial signal ever enters the pipeline.
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
    private final TurRagReranker reranker;
    private final TurDefaultChatModelResolver defaultChatModelResolver;

    /** Cached infra per (site, embeddingModel, store) so search latency doesn't pay a rebuild per query. */
    private final Map<String, RagInfrastructure> infraCache = new ConcurrentHashMap<>();

    public TurSNHybridRankingService(TurRagContextBuilder ragContextBuilder,
            TurGlobalSettingsService globalSettingsService,
            TurRagReranker reranker,
            TurDefaultChatModelResolver defaultChatModelResolver) {
        this.ragContextBuilder = ragContextBuilder;
        this.globalSettingsService = globalSettingsService;
        this.reranker = reranker;
        this.defaultChatModelResolver = defaultChatModelResolver;
    }

    /**
     * Whether the site opted into a hybrid ranking mode ({@code HYBRID_RRF} or
     * {@code HYBRID_RRF_RERANK}) AND the platform has a default embedding model +
     * store configured (without those there is nothing to embed against, so we
     * stay on the legacy path).
     */
    public boolean isEnabled(TurSNSite turSNSite) {
        if (turSNSite == null) {
            return false;
        }
        // T790 — a site explicitly in VECTORLESS_STRUCTURED has no vector collection
        // by design, so the public hybrid ranking (BM25 ⊕ vector via RRF) can never
        // apply. Short-circuit to the pure lexical path here: this is the single gate
        // every embedding-dependent entry point (fuse / indexDocument / deIndex /
        // findScoredNeighbors) shares, so a vectorless catalog stays on the simplest
        // possible search path — no embedding work, and one less place the vector
        // machinery can fail on a site that opted out.
        TurSNSiteGenAi genAi = turSNSite.getTurSNSiteGenAi();
        if (genAi != null && genAi.getKnowledgeBaseMode() != null
                && !genAi.getKnowledgeBaseMode().needsVectorSetup()) {
            return false;
        }
        TurSNRankingMode mode = modeOf(turSNSite);
        if (!mode.isHybrid()) {
            return false;
        }
        return StringUtils.hasText(resolveEmbeddingModelId(turSNSite))
                && StringUtils.hasText(globalSettingsService.getDefaultEmbeddingStoreId());
    }

    /**
     * T513 / §XXVIII.9 — the embedding model this site indexes and queries
     * through: its per-site {@link TurSNSiteGenAi#getEmbeddingModelId() domain
     * override} when set, otherwise the Global Settings platform default.
     */
    private String resolveEmbeddingModelId(TurSNSite turSNSite) {
        TurSNSiteGenAi genAi = turSNSite.getTurSNSiteGenAi();
        if (genAi != null && StringUtils.hasText(genAi.getEmbeddingModelId())) {
            return genAi.getEmbeddingModelId();
        }
        return globalSettingsService.getDefaultEmbeddingModelId();
    }

    private static TurSNRankingMode modeOf(TurSNSite turSNSite) {
        TurSNSiteGenAi genAi = turSNSite.getTurSNSiteGenAi();
        return genAi != null && genAi.getSnRankingMode() != null
                ? genAi.getSnRankingMode()
                : TurSNRankingMode.LEGACY;
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

    /** Stage 1: index the BM25 page by id into {@code byId}, capturing 1-based lexical rank + hit docs. */
    private void indexBm25Page(List<TurSEResult> page, Map<String, TurSEResult> byId,
            Map<String, Integer> lexicalRank, List<Document> bm25Hits) {
        for (TurSEResult result : page) {
            String id = asString(result.getFields().get(TurSNFieldName.ID));
            if (StringUtils.hasText(id) && byId.putIfAbsent(id, result) == null) {
                lexicalRank.put(id, lexicalRank.size() + 1);
                bm25Hits.add(Document.builder().id(id).text("").build());
            }
        }
    }

    /** Stage 2: keep only vector hits already on the page, capturing 1-based semantic rank. */
    private List<Document> collectVectorHits(List<Document> vectorAll, Map<String, TurSEResult> byId,
            Map<String, Integer> semanticRank) {
        List<Document> vectorHits = new ArrayList<>();
        if (vectorAll != null) {
            for (Document d : vectorAll) {
                if (byId.containsKey(d.getId())
                        && !semanticRank.containsKey(d.getId())) {
                    semanticRank.put(d.getId(), vectorHits.size() + 1);
                    vectorHits.add(d);
                }
            }
        }
        return vectorHits;
    }

    /** Stage 2.5: capture 1-based fused rank + RRF score per id from the fused list. */
    private void rankFused(List<Document> fused, Map<String, Integer> fusedRank,
            Map<String, Double> rrfScore) {
        for (Document d : fused) {
            if (!fusedRank.containsKey(d.getId())) {
                fusedRank.put(d.getId(), fusedRank.size() + 1);
                rrfScore.put(d.getId(), d.getScore());
            }
        }
    }

    public List<TurSEResult> fuse(TurSNSite turSNSite, Locale locale, String query, List<TurSEResult> page) {
        if (!isEnabled(turSNSite) || page == null || page.size() < 2 || !StringUtils.hasText(query)) {
            return page;
        }
        try {
            RagInfrastructure infra = infraFor(turSNSite);
            if (infra == null) {
                return page;
            }
            // Stage 1 (retrieve): index the BM25 page by id, capturing 1-based lexical rank.
            Map<String, TurSEResult> byId = new LinkedHashMap<>();
            Map<String, Integer> lexicalRank = new LinkedHashMap<>();
            List<Document> bm25Hits = new ArrayList<>(page.size());
            indexBm25Page(page, byId, lexicalRank, bm25Hits);
            if (byId.size() < 2) {
                return page;
            }
            // Stage 2 (fuse): vector pass over the per-site collection, restricted to
            // documents already on the page; fusion can only reorder what BM25 returned
            // (facets/pagination stay BM25-derived). Capture 1-based semantic rank.
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(VECTOR_TOP_K)
                    .similarityThreshold(0.0)
                    .build();
            List<Document> vectorAll = infra.vectorStore().similaritySearch(request);
            Map<String, Integer> semanticRank = new LinkedHashMap<>();
            List<Document> vectorHits = collectVectorHits(vectorAll, byId, semanticRank);
            if (vectorHits.isEmpty()) {
                return page;
            }
            List<Document> fused = TurRagRrf.fuse(vectorHits, bm25Hits, byId.size());
            Map<String, Integer> fusedRank = new LinkedHashMap<>();
            Map<String, Double> rrfScore = new LinkedHashMap<>();
            rankFused(fused, fusedRank, rrfScore);
            // Stage 3 (rerank, optional): re-order the fused page through the seam.
            TurSNRankingMode mode = modeOf(turSNSite);
            List<String> finalOrder = new ArrayList<>(fusedRank.keySet());
            boolean rerankApplied = false;
            TurRagRerankStrategyType rerankStrategy = null;
            if (mode.isRerank()) {
                rerankStrategy = globalSettingsService.getRagSnRerankStrategy();
                List<String> rerankedOrder = rerank(rerankStrategy, query, fused, byId, finalOrder);
                if (rerankedOrder != null) {
                    finalOrder = rerankedOrder;
                    rerankApplied = true;
                }
            }
            // Build the reordered page and attach the per-result ranking explanation.
            List<TurSEResult> reordered = new ArrayList<>(page.size());
            for (String id : finalOrder) {
                TurSEResult result = byId.remove(id);
                if (result == null) {
                    continue;
                }
                attachExplanation(result, mode,
                        new RankBreakdown(lexicalRank.get(id), semanticRank.get(id),
                                fusedRank.get(id), rrfScore.get(id), reordered.size() + 1),
                        rerankApplied, rerankStrategy);
                reordered.add(result);
            }
            // Any page result the pipeline didn't cover (shouldn't happen) keeps order at the tail.
            reordered.addAll(byId.values());
            return reordered;
        } catch (RuntimeException e) {
            log.warn("[SN-HYBRID] pipeline on site '{}' failed: {} — returning lexical order",
                    turSNSite.getName(), e.getMessage());
            return page;
        }
    }

    /**
     * Re-orders the fused id list via the {@link TurRagReranker} seam. Builds
     * candidate documents whose text is rebuilt from the page result fields (so
     * the strategy has real content to judge), resolves a default chat model only
     * for the LLM strategy, and delegates to {@link TurRagReranker#reorder}.
     *
     * @return the reranked id order when it is a full permutation of the page, or
     *         {@code null} when reranking added no signal (caller keeps fused order)
     */
    private List<String> rerank(TurRagRerankStrategyType strategy, String query,
            List<Document> fused, Map<String, TurSEResult> byId, List<String> fusedOrder) {
        // T517 — the LLM reranker rides the REASONING model lane (falls back to
        // the default LLM when the lane is unbound).
        ChatModel chatModel = strategy == TurRagRerankStrategyType.LLM
                ? defaultChatModelResolver.resolve(
                        com.viglet.turing.system.lane.TurModelStage.RERANK).orElse(null)
                : null;
        List<Document> candidates = new ArrayList<>(fused.size());
        for (Document d : fused) {
            TurSEResult result = byId.get(d.getId());
            String text = result != null ? buildContent(result.getFields()) : d.getText();
            candidates.add(Document.builder().id(d.getId()).text(text == null ? "" : text).build());
        }
        List<Document> reranked = reranker.reorder(chatModel, query, candidates);
        List<String> order = new ArrayList<>(reranked.size());
        for (Document d : reranked) {
            if (d.getId() != null && !order.contains(d.getId())) {
                order.add(d.getId());
            }
        }
        // Only adopt a complete permutation; otherwise keep the fused order.
        return order.size() == fusedOrder.size() ? order : null;
    }

    /**
     * The per-result rank signals that feed the "why this result ranked here"
     * breakdown: the lexical / semantic / fused input ranks, the RRF score and
     * the final position. Bundled so {@link #attachExplanation} stays below the
     * parameter threshold.
     */
    private record RankBreakdown(Integer lexicalRank, Integer semanticRank, Integer fusedRank,
            Double rrfScore, int finalRank) {
    }

    /**
     * Attaches the "why this result ranked here" breakdown — objective signals
     * only (ranks + the RRF score), never any commercial weight.
     */
    private static void attachExplanation(TurSEResult result, TurSNRankingMode mode,
            RankBreakdown ranks, boolean rerankApplied, TurRagRerankStrategyType rerankStrategy) {
        Integer lexicalRank = ranks.lexicalRank();
        Integer semanticRank = ranks.semanticRank();
        Integer fusedRank = ranks.fusedRank();
        Double rrfScore = ranks.rrfScore();
        int finalRank = ranks.finalRank();
        Map<String, Object> explain = new LinkedHashMap<>();
        explain.put("pipeline", mode.name());
        if (lexicalRank != null) {
            explain.put("lexicalRank", lexicalRank);
        }
        if (semanticRank != null) {
            explain.put("semanticRank", semanticRank);
        }
        if (fusedRank != null) {
            explain.put("fusedRank", fusedRank);
        }
        if (rrfScore != null) {
            explain.put("rrfScore", Math.round(rrfScore * 1_000_000d) / 1_000_000d);
        }
        explain.put("finalRank", finalRank);
        if (rerankApplied) {
            if (rerankStrategy != null) {
                explain.put("rerankStrategy", rerankStrategy.name());
            }
            if (fusedRank != null) {
                explain.put("rerankerDelta", fusedRank - finalRank);
            }
        }
        result.setRankingExplanation(explain);
    }

    /**
     * T384 / §XX.4 — vector-neighbor retrieval: returns the ids of the documents
     * most semantically similar to a seed document, ordered by similarity
     * descending, excluding the seed itself. The seed's content is rebuilt from
     * {@code seedAttributes} (same {@link #buildContent} the indexing path uses)
     * and embedded against the per-site {@code sn_<siteId>} collection.
     *
     * <p>This is the semantic half of the public similar-documents endpoint and
     * the primitive {@code T390} (multi-source duplicate clustering) builds on.
     * Fail-open: any failure (no embedding model, store unavailable, embedding
     * error) returns an empty list so the caller can degrade to lexical MLT.
     *
     * @param turSNSite      the HYBRID_RRF site
     * @param seedAttributes the seed document's field map (must include its id)
     * @param topK           maximum number of neighbor ids to return
     * @return up to {@code topK} neighbor ids, seed excluded; empty on any failure
     */
    public List<String> findNeighbors(TurSNSite turSNSite, Map<String, Object> seedAttributes, int topK) {
        return findScoredNeighbors(turSNSite, seedAttributes, topK, 0.0).stream()
                .map(TurSNScoredNeighbor::id)
                .toList();
    }

    /**
     * T390 / §XX.10 — scored vector-neighbor retrieval: the same machinery as
     * {@link #findNeighbors} but it keeps the per-neighbor similarity score and
     * applies a minimum-similarity cut-off, so a caller can tell "near-identical"
     * (duplicate clustering) apart from merely "related" (similar documents).
     *
     * <p>Neighbors are returned ordered by similarity descending, seed excluded.
     * The {@code minSimilarity} cut-off is applied both as the store's
     * {@link SearchRequest#getSimilarityThreshold()} (so capable stores prune
     * server-side) and defensively in this method against each
     * {@link Document#getScore()}. A neighbor whose score is unknown ({@code null})
     * is kept only when {@code minSimilarity <= 0} — so any positive threshold
     * conservatively excludes scoreless hits rather than wrongly merging them.
     *
     * <p>Fail-open: any failure (no embedding model, store unavailable, embedding
     * error) returns an empty list.
     *
     * @param turSNSite      the HYBRID_RRF site
     * @param seedAttributes the seed document's field map (must include its id)
     * @param topK           maximum number of neighbors to return
     * @param minSimilarity  the minimum similarity score a neighbor must clear
     * @return up to {@code topK} scored neighbors, seed excluded; empty on any failure
     */
    public List<TurSNScoredNeighbor> findScoredNeighbors(TurSNSite turSNSite,
            Map<String, Object> seedAttributes, int topK, double minSimilarity) {
        if (!isEnabled(turSNSite) || seedAttributes == null || topK < 1) {
            return List.of();
        }
        String seedId = asString(seedAttributes.get(TurSNFieldName.ID));
        String content = buildContent(seedAttributes);
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        try {
            RagInfrastructure infra = infraFor(turSNSite);
            if (infra == null) {
                return List.of();
            }
            SearchRequest request = SearchRequest.builder()
                    .query(content)
                    .topK(topK + 1)
                    .similarityThreshold(Math.max(0.0, minSimilarity))
                    .build();
            List<Document> hits = infra.vectorStore().similaritySearch(request);
            if (hits == null || hits.isEmpty()) {
                return List.of();
            }
            return collectNeighbors(hits, seedId, topK, minSimilarity);
        } catch (RuntimeException e) {
            log.warn("[SN-HYBRID] neighbors of doc '{}' on site '{}' failed: {} — degrading to lexical",
                    seedId, turSNSite.getName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Reduces raw vector hits to scored neighbors: skips the seed itself,
     * duplicates, and sub-threshold scores, capped at {@code topK}.
     */
    private List<TurSNScoredNeighbor> collectNeighbors(List<Document> hits, String seedId,
            int topK, double minSimilarity) {
        List<TurSNScoredNeighbor> neighbors = new ArrayList<>(Math.min(topK, hits.size()));
        List<String> seen = new ArrayList<>(neighbors.size());
        for (Document d : hits) {
            String id = d.getId();
            Double score = d.getScore();
            boolean eligible = StringUtils.hasText(id) && !id.equals(seedId) && !seen.contains(id)
                    && !(minSimilarity > 0.0 && (score == null || score < minSimilarity));
            if (eligible) {
                seen.add(id);
                neighbors.add(new TurSNScoredNeighbor(id, score));
                if (neighbors.size() >= topK) {
                    break;
                }
            }
        }
        return neighbors;
    }

    /** Drops the cached infrastructure for a site (e.g. after deleting it). */
    public void evict(String siteId) {
        infraCache.keySet().removeIf(k -> k.startsWith(siteId + "|"));
    }

    private RagInfrastructure infraFor(TurSNSite turSNSite) {
        String embeddingModelId = resolveEmbeddingModelId(turSNSite);
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
