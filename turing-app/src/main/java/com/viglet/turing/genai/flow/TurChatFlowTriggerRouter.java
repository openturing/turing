/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queries.mlt.MoreLikeThis;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.flow.TurChatFlowEngineService.ProceduralOutcome;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.RouterCandidate;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Lucene BM25 / {@link MoreLikeThis} trigger router for the procedural
 * pre-route. Extracted from {@link TurChatFlowEngineService} (S6539 Monster
 * Class split) so the flow-orchestration engine no longer also owns an
 * in-memory Lucene search subsystem.
 *
 * <p>Owns the per-agent {@link SearcherManager} index cache, the PT/EN
 * analyzer selection (T25/T26) and the keyword scoring + dominance gate that
 * decides whether the procedural pass commits a flow or defers to the LLM
 * router. The engine delegates to this bean and keeps the higher-level flow
 * selection / A-B / LLM-router logic.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurChatFlowTriggerRouter {

    private static final double PROCEDURAL_DOMINANCE_RATIO = 1.5;
    private static final int PROCEDURAL_MIN_TOKEN_LENGTH = 3;
    private static final String MLT_FIELD_TRIGGER = "trigger";
    private static final String MLT_FIELD_ID = "flowId";

    /**
     * Per-language Lucene analyzers for the procedural pre-route. PT-BR is the
     * default for legacy flows; EN uses Porter stemming. Analyzers are
     * thread-safe — a single instance per language is reused across requests.
     */
    private static final Analyzer ANALYZER_PT = new PortugueseAnalyzer();
    private static final Analyzer ANALYZER_EN = new EnglishAnalyzer();

    /**
     * Lightweight EN stopword set for {@link #detectLanguage(String)} — the
     * AUTO-language heuristic counts these against {@link #PT_HINT_WORDS}.
     */
    private static final Set<String> EN_HINT_WORDS = Set.of(
            "the", "and", "or", "for", "with", "to", "of", "in", "on", "is", "are",
            "this", "that", "what", "how", "when", "where", "why", "you", "your",
            "i", "me", "my", "do", "does", "want", "need", "would", "should", "can");

    /**
     * PT hint stopwords mirror — same role, opposite language. Avoided overlap
     * with {@link #EN_HINT_WORDS} so neutral text (proper nouns only) returns
     * an even score → defaults to PT (legacy behavior, primary audience).
     */
    private static final Set<String> PT_HINT_WORDS = Set.of(
            "o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das",
            "para", "por", "com", "sem", "que", "como", "quando", "onde",
            "eu", "você", "voce", "meu", "minha", "seu", "sua",
            "quero", "preciso", "gostaria", "tenho", "estou", "está", "esta");

    /**
     * T27 / §II.2.3 — per-agent index of trigger descriptions, one
     * {@link AnalyzerBucket} per {@link Analyzer} (PT/EN). Local to this JVM by
     * design: {@code SearcherManager} + {@code ByteBuffersDirectory} are
     * non-serializable live resources.
     */
    private final ConcurrentMap<String, AgentRouterIndex> routerIndexByAgent =
            new ConcurrentHashMap<>();

    private final TurChatFlowRepository chatFlowRepository;

    public TurChatFlowTriggerRouter(TurChatFlowRepository chatFlowRepository) {
        this.chatFlowRepository = chatFlowRepository;
    }

    /**
     * Procedural pre-route — Lucene {@link MoreLikeThis} score between the
     * user message and each candidate's {@code triggerDescription}. Returns
     * the picked flow id when one candidate dominates clearly
     * (≥{@link #PROCEDURAL_DOMINANCE_RATIO}× the next best); otherwise
     * {@link Optional#empty()} so the caller can fall back to the LLM router.
     */
    static Optional<String> tryProceduralRoute(List<RouterCandidate> candidates,
            String userMessage) {
        if (userMessage == null || userMessage.isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }
        // Procedural routing is a disambiguation fast-path: with a single
        // candidate there is nothing to disambiguate. Defer to the LLM router.
        if (candidates.size() < 2) {
            return Optional.empty();
        }
        // Query-side quality guard: require at least one substantive token.
        if (!hasSubstantiveToken(userMessage)) {
            return Optional.empty();
        }
        Map<Analyzer, List<RouterCandidate>> byAnalyzer = groupCandidatesByAnalyzer(candidates);
        if (byAnalyzer.isEmpty()) {
            return Optional.empty();
        }
        ScoredRoute aggregate = aggregateBucketScores(byAnalyzer, userMessage);
        if (aggregate.bestId() == null || aggregate.bestScore() <= 0f) {
            return Optional.empty();
        }
        if (aggregate.secondScore() > 0f
                && aggregate.bestScore() < aggregate.secondScore() * PROCEDURAL_DOMINANCE_RATIO) {
            log.debug("[FlowEngine] Procedural router undecided "
                    + "(best={}, second={}, ratio<{}) — falling back to LLM",
                    aggregate.bestScore(), aggregate.secondScore(), PROCEDURAL_DOMINANCE_RATIO);
            return Optional.empty();
        }
        return Optional.ofNullable(aggregate.bestId());
    }

    private static Map<Analyzer, List<RouterCandidate>> groupCandidatesByAnalyzer(
            List<RouterCandidate> candidates) {
        Map<Analyzer, List<RouterCandidate>> byAnalyzer = HashMap.newHashMap(2);
        for (RouterCandidate c : candidates) {
            String description = c.flow().getTriggerDescription();
            if (description != null && !description.isBlank()) {
                Analyzer analyzer = analyzerFor(c.flow().getTriggerLanguage(), description);
                byAnalyzer.computeIfAbsent(analyzer, ignored -> new ArrayList<>()).add(c);
            }
        }
        return byAnalyzer;
    }

    private static ScoredRoute aggregateBucketScores(Map<Analyzer, List<RouterCandidate>> byAnalyzer,
            String userMessage) {
        float bestScore = 0f;
        float secondScore = 0f;
        String bestId = null;
        for (Map.Entry<Analyzer, List<RouterCandidate>> bucket : byAnalyzer.entrySet()) {
            ScoredRoute scoredRoute = moreLikeThisRoute(bucket.getKey(), bucket.getValue(),
                    userMessage);
            if (scoredRoute == null) {
                continue;
            }
            if (scoredRoute.bestScore() > bestScore) {
                if (bestScore > secondScore) {
                    secondScore = bestScore;
                }
                bestScore = scoredRoute.bestScore();
                bestId = scoredRoute.bestId();
            } else if (scoredRoute.bestScore() > secondScore) {
                secondScore = scoredRoute.bestScore();
            }
            if (scoredRoute.secondScore() > secondScore) {
                secondScore = scoredRoute.secondScore();
            }
        }
        return new ScoredRoute(bestId, bestScore, secondScore);
    }

    private record ScoredRoute(String bestId, float bestScore, float secondScore) {
    }

    /**
     * Returns {@code true} when {@code userMessage} contains at least one
     * whitespace-separated token of length ≥ {@link #PROCEDURAL_MIN_TOKEN_LENGTH}
     * after stripping leading/trailing non-letter characters.
     */
    static boolean hasSubstantiveToken(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        for (String raw : userMessage.split("\\s+")) {
            String trimmed = raw.replaceAll("^[^\\p{L}]+", "").replaceAll("[^\\p{L}]+$", "");
            if (trimmed.length() >= PROCEDURAL_MIN_TOKEN_LENGTH) {
                return true;
            }
        }
        return false;
    }

    /**
     * T27 — cached variant of {@link #tryProceduralRoute(List, String)} that
     * reuses a pre-built {@link SearcherManager}-per-analyzer index keyed by
     * {@code agentId}.
     */
    Optional<String> tryProceduralRoute(String agentId, List<RouterCandidate> candidates,
            String userMessage) {
        return proceduralRouteExplained(agentId, candidates, userMessage)
                .filter(ProceduralOutcome::decided)
                .map(ProceduralOutcome::pickedId);
    }

    /**
     * T89 — score-surfacing variant of {@link #tryProceduralRoute(String, List, String)}.
     * Returns the full per-candidate score map and best/second/ratio even when
     * the pass is too ambiguous to commit ({@link ProceduralOutcome#decided()}
     * is {@code false}); {@link Optional#empty()} only when the pass did not run.
     */
    Optional<ProceduralOutcome> proceduralRouteExplained(String agentId,
            List<RouterCandidate> candidates, String userMessage) {
        if (userMessage == null || userMessage.isBlank() || candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() < 2) {
            return Optional.empty();
        }
        if (!hasSubstantiveToken(userMessage)) {
            return Optional.empty();
        }
        if (agentId == null || agentId.isBlank()) {
            // No agent id → on-demand static path, which only surfaces the
            // pick (no per-candidate scores). Wrap it as a decided outcome
            // with an empty score map so callers keep the same shape.
            return tryProceduralRoute(candidates, userMessage)
                    .map(id -> new ProceduralOutcome(true, id, 0f, 0f,
                            Float.POSITIVE_INFINITY, Map.of()));
        }
        AgentRouterIndex index = routerIndexByAgent.computeIfAbsent(agentId,
                this::buildRouterIndex);
        if (index.isEmpty()) {
            return Optional.empty();
        }
        return scoreCachedRouteExplained(index, candidates, userMessage);
    }

    /**
     * Drops every cached {@link AgentRouterIndex}, releasing the underlying
     * Lucene {@link Directory}/{@link SearcherManager} resources. Invoked by
     * {@link TurChatFlowRouterEvictionListener} on {@link TurChatFlow}
     * persist/update/remove and at the one bulk-DML delete site.
     */
    public void evictRouterIndexes() {
        if (routerIndexByAgent.isEmpty()) {
            return;
        }
        List<AgentRouterIndex> drained = new ArrayList<>(routerIndexByAgent.values());
        routerIndexByAgent.clear();
        for (AgentRouterIndex index : drained) {
            index.close();
        }
        log.debug("[FlowEngine] Router index cache evicted ({} agent entries)",
                drained.size());
    }

    /** Test-visible accessor for the per-agent index cache size. */
    int routerIndexCacheSize() {
        return routerIndexByAgent.size();
    }

    private AgentRouterIndex buildRouterIndex(String agentId) {
        List<TurChatFlow> flows = chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
        Map<Analyzer, List<TurChatFlow>> grouped = HashMap.newHashMap(2);
        for (TurChatFlow flow : flows) {
            String description = flow.getTriggerDescription();
            if (flow.getEnabled() != 1 || description == null || description.isBlank()) {
                continue;
            }
            Analyzer analyzer = analyzerFor(flow.getTriggerLanguage(), description);
            grouped.computeIfAbsent(analyzer, ignored -> new ArrayList<>()).add(flow);
        }
        if (grouped.isEmpty()) {
            return AgentRouterIndex.empty();
        }
        Map<Analyzer, AnalyzerBucket> buckets = HashMap.newHashMap(grouped.size());
        for (Map.Entry<Analyzer, List<TurChatFlow>> entry : grouped.entrySet()) {
            AnalyzerBucket bucket = buildBucket(entry.getKey(), entry.getValue());
            if (bucket != null) {
                buckets.put(entry.getKey(), bucket);
            }
        }
        if (buckets.isEmpty()) {
            return AgentRouterIndex.empty();
        }
        return new AgentRouterIndex(Map.copyOf(buckets));
    }

    private static AnalyzerBucket buildBucket(Analyzer analyzer, List<TurChatFlow> flows) {
        Directory directory = new ByteBuffersDirectory();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer)
                .setSimilarity(new BM25Similarity());
        int docs = 0;
        try (IndexWriter writer = new IndexWriter(directory, cfg)) {
            for (TurChatFlow flow : flows) {
                Document doc = new Document();
                doc.add(new StringField(MLT_FIELD_ID, flow.getId(), Field.Store.YES));
                doc.add(new TextField(MLT_FIELD_TRIGGER, flow.getTriggerDescription(),
                        Field.Store.NO));
                writer.addDocument(doc);
                docs++;
            }
            writer.commit();
        } catch (IOException e) {
            log.warn("[FlowEngine] Failed to build router index bucket: {}", e.getMessage());
            try {
                directory.close();
            } catch (IOException closeException) {
                log.debug("[FlowEngine] Failed to close directory after build error: {}",
                        closeException.getMessage());
            }
            return null;
        }
        try {
            SearcherManager searcherManager = new SearcherManager(directory, null);
            return new AnalyzerBucket(directory, searcherManager, analyzer, docs);
        } catch (IOException e) {
            log.warn("[FlowEngine] Failed to open SearcherManager: {}", e.getMessage());
            try {
                directory.close();
            } catch (IOException closeException) {
                log.debug("[FlowEngine] Failed to close directory after SM error: {}",
                        closeException.getMessage());
            }
            return null;
        }
    }

    private static Optional<ProceduralOutcome> scoreCachedRouteExplained(AgentRouterIndex index,
            List<RouterCandidate> candidates, String userMessage) {
        Set<String> eligibleIds = candidates.stream()
                .map(c -> c.flow().getId())
                .collect(Collectors.toSet());
        Map<String, Float> scores = new HashMap<>();
        for (AnalyzerBucket bucket : index.buckets().values()) {
            collectBucketScores(bucket, eligibleIds, userMessage, scores);
        }
        return decide(scores);
    }

    /**
     * T89 — applies the dominance gate to a flowId→score map and packages the
     * outcome. {@link Optional#empty()} only when nothing scored; otherwise
     * {@code decided=true} iff the best dominates the runner-up by
     * {@link #PROCEDURAL_DOMINANCE_RATIO}× (or there is no runner-up).
     */
    private static Optional<ProceduralOutcome> decide(Map<String, Float> scores) {
        String bestId = null;
        float bestScore = 0f;
        float secondScore = 0f;
        for (Map.Entry<String, Float> e : scores.entrySet()) {
            float s = e.getValue();
            if (s > bestScore) {
                if (bestScore > secondScore) {
                    secondScore = bestScore;
                }
                bestScore = s;
                bestId = e.getKey();
            } else if (s > secondScore) {
                secondScore = s;
            }
        }
        if (bestId == null || bestScore <= 0f) {
            return Optional.empty();
        }
        float ratio = secondScore > 0f ? bestScore / secondScore : Float.POSITIVE_INFINITY;
        boolean decided = !(secondScore > 0f
                && bestScore < secondScore * PROCEDURAL_DOMINANCE_RATIO);
        if (!decided) {
            log.debug("[FlowEngine] Cached router undecided "
                    + "(best={}, second={}, ratio<{}) — falling back to LLM",
                    bestScore, secondScore, PROCEDURAL_DOMINANCE_RATIO);
        }
        return Optional.of(new ProceduralOutcome(decided, bestId, bestScore, secondScore,
                ratio, Map.copyOf(scores)));
    }

    /**
     * Searches one analyzer bucket and merges every eligible flow's score into
     * {@code sink}. Keeps the full distribution so {@link ProceduralOutcome}
     * can report each candidate's score (T89).
     */
    private static void collectBucketScores(AnalyzerBucket bucket, Set<String> eligibleIds,
            String userMessage, Map<String, Float> sink) {
        SearcherManager searcherManager = bucket.searcherManager();
        try {
            IndexSearcher searcher = searcherManager.acquire();
            // Release the searcher back to the manager (never close()) via an
            // AutoCloseable lease so the body reads as try-with-resources.
            try (Closeable ignored = () -> searcherManager.release(searcher)) {
                MoreLikeThis moreLikeThis = new MoreLikeThis(searcher.getIndexReader());
                moreLikeThis.setAnalyzer(bucket.analyzer());
                moreLikeThis.setFieldNames(new String[] { MLT_FIELD_TRIGGER });
                moreLikeThis.setMinTermFreq(1);
                moreLikeThis.setMinDocFreq(1);
                moreLikeThis.setMinWordLen(2);
                moreLikeThis.setMaxQueryTerms(25);
                Query query = moreLikeThis.like(MLT_FIELD_TRIGGER, new StringReader(userMessage));
                int topK = Math.max(2, bucket.docCount());
                TopDocs hits = searcher.search(query, topK);
                if (hits.scoreDocs.length == 0) {
                    return;
                }
                StoredFields storedFields = searcher.storedFields();
                for (ScoreDoc sd : hits.scoreDocs) {
                    String flowId = storedFields.document(sd.doc).get(MLT_FIELD_ID);
                    if (flowId == null || !eligibleIds.contains(flowId)) {
                        continue;
                    }
                    sink.merge(flowId, sd.score, Math::max);
                }
            }
        } catch (IOException e) {
            log.warn("[FlowEngine] Cached MoreLikeThis search failed: {}", e.getMessage());
        }
    }

    /**
     * Pre-built per-analyzer Lucene index for one agent. Lives in the
     * {@code routerIndexByAgent} cache until {@link #evictRouterIndexes()}
     * fires; closing it releases the underlying {@link Directory} and
     * {@link SearcherManager}.
     */
    record AgentRouterIndex(Map<Analyzer, AnalyzerBucket> buckets) {

        private static final AgentRouterIndex EMPTY = new AgentRouterIndex(Map.of());

        static AgentRouterIndex empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return buckets.isEmpty();
        }

        void close() {
            for (AnalyzerBucket bucket : buckets.values()) {
                bucket.close();
            }
        }
    }

    /**
     * One bucket of the {@link AgentRouterIndex}: a Lucene directory + open
     * {@link SearcherManager} for a single {@link Analyzer} (PT or EN).
     */
    record AnalyzerBucket(Directory directory, SearcherManager searcherManager,
            Analyzer analyzer, int docCount) {

        void close() {
            try {
                searcherManager.close();
            } catch (IOException e) {
                log.debug("[FlowEngine] SearcherManager close failed: {}", e.getMessage());
            }
            try {
                directory.close();
            } catch (IOException e) {
                log.debug("[FlowEngine] Directory close failed: {}", e.getMessage());
            }
        }
    }

    private static ScoredRoute moreLikeThisRoute(Analyzer analyzer,
            List<RouterCandidate> candidates, String userMessage) {
        try (Directory dir = new ByteBuffersDirectory();
                IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
            for (RouterCandidate candidate : candidates) {
                Document doc = new Document();
                doc.add(new StringField(MLT_FIELD_ID, candidate.flow().getId(), Field.Store.YES));
                doc.add(new TextField(MLT_FIELD_TRIGGER,
                        candidate.flow().getTriggerDescription(), Field.Store.NO));
                writer.addDocument(doc);
            }
            writer.commit();
            try (IndexReader reader = DirectoryReader.open(dir)) {
                MoreLikeThis moreLikeThis = new MoreLikeThis(reader);
                moreLikeThis.setAnalyzer(analyzer);
                moreLikeThis.setFieldNames(new String[] { MLT_FIELD_TRIGGER });
                moreLikeThis.setMinTermFreq(1);
                moreLikeThis.setMinDocFreq(1);
                moreLikeThis.setMinWordLen(2);
                moreLikeThis.setMaxQueryTerms(25);
                Query query = moreLikeThis.like(MLT_FIELD_TRIGGER, new StringReader(userMessage));
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs hits = searcher.search(query, 2);
                if (hits.scoreDocs.length == 0) {
                    return null;
                }
                StoredFields storedFields = searcher.storedFields();
                String bestId = storedFields.document(hits.scoreDocs[0].doc).get(MLT_FIELD_ID);
                float bestScore = hits.scoreDocs[0].score;
                float secondScore = hits.scoreDocs.length > 1 ? hits.scoreDocs[1].score : 0f;
                return new ScoredRoute(bestId, bestScore, secondScore);
            }
        } catch (IOException e) {
            log.warn("[FlowEngine] MoreLikeThis procedural route failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * T25 — picks the analyzer for a flow's {@code triggerDescription} based on
     * its declared {@link TurChatFlowTriggerLanguage}. {@code AUTO} (default)
     * routes through {@link #detectLanguage(String)}; PT/EN are honored
     * verbatim. Null lang treated as AUTO.
     */
    static Analyzer analyzerFor(TurChatFlowTriggerLanguage lang, String description) {
        TurChatFlowTriggerLanguage effective = lang == null ? TurChatFlowTriggerLanguage.AUTO : lang;
        return switch (effective) {
            case PT -> ANALYZER_PT;
            case EN -> ANALYZER_EN;
            case AUTO -> detectLanguage(description) == TurChatFlowTriggerLanguage.EN
                    ? ANALYZER_EN : ANALYZER_PT;
        };
    }

    /**
     * T25 — lightweight EN vs PT heuristic for AUTO-tagged flows. Returns
     * {@link TurChatFlowTriggerLanguage#EN} when EN hint count strictly exceeds
     * PT hint count, {@link TurChatFlowTriggerLanguage#PT} otherwise (PT is the
     * platform's primary language so ties + zero-signal default to it).
     */
    static TurChatFlowTriggerLanguage detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return TurChatFlowTriggerLanguage.PT;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String[] words = lower.split("[^\\p{L}]+");
        int en = 0;
        int pt = 0;
        for (String w : words) {
            if (EN_HINT_WORDS.contains(w)) en++;
            else if (PT_HINT_WORDS.contains(w)) pt++;
        }
        return en > pt ? TurChatFlowTriggerLanguage.EN : TurChatFlowTriggerLanguage.PT;
    }

    /**
     * Tokenize {@code text} through the supplied Lucene {@link Analyzer}.
     * Applies that analyzer's pipeline (lowercase, stopword removal, stemming)
     * and returns the deduplicated set of stems. Empty for blank/null input.
     */
    static Set<String> tokenize(String text, Analyzer analyzer) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        try (TokenStream stream = analyzer.tokenStream("text", text)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String token = term.toString();
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
            stream.end();
        } catch (IOException e) {
            log.warn("[FlowEngine] Lucene tokenization failed: {}", e.getMessage());
            return Set.of();
        }
        return tokens;
    }
}
