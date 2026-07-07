/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
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
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;
import com.viglet.turing.persistence.repository.chatanalytics.TurAnalyticsIntentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T28 / §III.5 — embedded Lucene {@code MoreLikeThis} classifier
 * strategy. Scores the conversation transcript against the per-agent
 * {@link TurAnalyticsIntent} catalog; picks the label whose samples best
 * resemble the transcript per BM25-weighted MLT scoring.
 *
 * <p>This is the dev/single-instance/no-LLM-available fallback called
 * out by §III.5 — same MLT machinery T26/T27 use for the chat-flow
 * router (mirrors the implementation choices in
 * {@code TurChatFlowEngineService}). The deferred SE-backed
 * {@code TurEsMltIntentClassifier} (T28 Phase B) will share this
 * strategy's contract through {@link TurIntentClassifierStrategy} and
 * cover multi-pod deployments via Elasticsearch's native MLT.
 *
 * <h2>Indexing model</h2>
 *
 * One {@link ByteBuffersDirectory} per agent, built lazily on the first
 * classify call and cached in {@link #indexByAgent}. Each catalog entry
 * becomes one document with:
 * <ul>
 *   <li>{@code label} ({@link StringField}, stored) — the human-readable
 *       intent label returned on match.</li>
 *   <li>{@code samples} ({@link TextField}, not stored) — the
 *       newline-separated samples concatenated; this is the field MLT
 *       scores against.</li>
 * </ul>
 *
 * <p>Eviction: piggybacks on the {@code turAnalyticsIntentMLTIndex}
 * Spring cache name — admin saves and
 * deletes trigger {@link #evictAll()} via {@link TurAnalyticsIntentEvictionListener}
 * (same JPA {@code @EntityListeners} pattern T27 uses for the chat-flow
 * router cache).
 *
 * <h2>Confidence + thresholding</h2>
 *
 * MLT raw scores are not bounded — calibrating to {@code [0, 1]} for
 * {@link TurChatSessionEnrichment#intentConfidence()} uses the top hit's
 * score divided by the sum of the top-K scores (a soft-max-like
 * normalization). Sessions where the top score is below
 * {@link #MIN_SCORE_FLOOR} return {@link TurChatSessionEnrichment#unclassified()}
 * so a too-short transcript or an unrelated topic doesn't get
 * mis-labelled.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurLuceneIntentClassifier implements TurIntentClassifierStrategy {

    public static final String TYPE = "lucene";

    /**
     * Minimum raw MLT score for the top hit to be reported as a
     * classification. Calibrated against the chat-flow router experience
     * (§II.2.2 / T26): below ~0.5 the matches tend to be incidental
     * term-overlaps from the transcript boilerplate rather than real
     * intent alignment.
     */
    private static final float MIN_SCORE_FLOOR = 0.5f;

    private static final String FIELD_LABEL = "label";
    private static final String FIELD_SAMPLES = "samples";

    /**
     * Single PT analyzer covers both PT and ES well enough at the
     * Snowball-stem level for this classifier — full per-locale routing
     * (matching T25's {@code analyzerFor}) is deferred until the catalog
     * carries an explicit per-row language tag. Today the catalog is
     * language-agnostic; using the Portuguese analyzer is the closest
     * thing to a no-regret default for the platform's primary audience
     * with no cost to EN matches (stopwords still filter cleanly).
     */
    private static final Analyzer ANALYZER_PT = new PortugueseAnalyzer();
    private static final Analyzer ANALYZER_EN = new EnglishAnalyzer();

    /**
     * Per-agent prebuilt indexes. Same pattern as
     * {@code TurChatFlowEngineService.routerIndexByAgent} (T27): lazily
     * built on first classification per agent, wiped via
     * {@link #evictAll()} when the catalog changes.
     */
    private final ConcurrentMap<String, AgentIntentIndex> indexByAgent = new ConcurrentHashMap<>();

    private final TurAnalyticsIntentRepository intentRepository;

    public TurLuceneIntentClassifier(TurAnalyticsIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isAvailable(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return false;
        }
        // Cheap check: does the agent have any enabled catalog row at all?
        // The Spring cache on the repository ensures this stays O(1) once
        // warm. No Lucene work yet — only on classify().
        List<TurAnalyticsIntent> catalog = intentRepository
                .findByTurAIAgent_IdAndEnabledOrderByLabelAsc(agentId, 1);
        return catalog != null && !catalog.isEmpty()
                && catalog.stream().anyMatch(i -> i.getSamples() != null && !i.getSamples().isBlank());
    }

    @Override
    public TurChatSessionEnrichment classify(String agentId, String firstUserMessage,
            List<Map<String, Object>> messages) {
        if (agentId == null || agentId.isBlank()) {
            return TurChatSessionEnrichment.unclassified();
        }
        String transcript = TurLlmIntentClassifier.buildTranscript(firstUserMessage, messages);
        if (transcript.isBlank()) {
            return TurChatSessionEnrichment.unclassified();
        }
        AgentIntentIndex index = indexByAgent.computeIfAbsent(agentId, this::buildIndex);
        if (index.isEmpty()) {
            return TurChatSessionEnrichment.unclassified();
        }
        return scoreTranscript(index, transcript);
    }

    /**
     * Drops every cached {@link AgentIntentIndex}, releasing the
     * underlying Lucene resources. Invoked by
     * {@link TurAnalyticsIntentEvictionListener} on
     * {@link TurAnalyticsIntent} persist/update/remove.
     */
    public void evictAll() {
        if (indexByAgent.isEmpty()) {
            return;
        }
        List<AgentIntentIndex> drained = new ArrayList<>(indexByAgent.values());
        indexByAgent.clear();
        for (AgentIntentIndex idx : drained) {
            idx.close();
        }
        log.debug("[LuceneIntentClassifier] evicted {} agent indexes", drained.size());
    }

    /** Test-visible cache size. */
    int cacheSize() {
        return indexByAgent.size();
    }

    private AgentIntentIndex buildIndex(String agentId) {
        List<TurAnalyticsIntent> catalog = intentRepository
                .findByTurAIAgent_IdAndEnabledOrderByLabelAsc(agentId, 1);
        if (catalog == null || catalog.isEmpty()) {
            return AgentIntentIndex.empty();
        }
        Analyzer analyzer = pickAnalyzer(catalog);
        Directory directory = new ByteBuffersDirectory();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer)
                .setSimilarity(new BM25Similarity());
        int docs = 0;
        try (IndexWriter writer = new IndexWriter(directory, cfg)) {
            for (TurAnalyticsIntent intent : catalog) {
                if (intent.getLabel() == null || intent.getLabel().isBlank()
                        || intent.getSamples() == null || intent.getSamples().isBlank()) {
                    continue;
                }
                Document doc = new Document();
                doc.add(new StringField(FIELD_LABEL, intent.getLabel(), Field.Store.YES));
                doc.add(new TextField(FIELD_SAMPLES,
                        normalizeSamples(intent.getSamples()), Field.Store.NO));
                writer.addDocument(doc);
                docs++;
            }
            writer.commit();
        } catch (IOException e) {
            log.warn("[LuceneIntentClassifier] failed to build index for agent '{}': {}",
                    agentId, e.getMessage());
            closeQuietly(directory);
            return AgentIntentIndex.empty();
        }
        if (docs == 0) {
            closeQuietly(directory);
            return AgentIntentIndex.empty();
        }
        try {
            return new AgentIntentIndex(directory, analyzer, docs);
        } catch (IOException e) {
            log.warn("[LuceneIntentClassifier] failed to open reader for agent '{}': {}",
                    agentId, e.getMessage());
            closeQuietly(directory);
            return AgentIntentIndex.empty();
        }
    }

    /**
     * Newline-separated samples → space-separated text MLT can tokenize.
     * Strips leading/trailing whitespace per line so admins can ship
     * one-per-line bullets without inflating term frequencies on the
     * incidental whitespace.
     */
    private static String normalizeSamples(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /**
     * Picks an analyzer per the catalog's dominant language hint —
     * cheap heuristic, same shape as T25's {@code detectLanguage}.
     * No-signal defaults to {@link #ANALYZER_PT} (platform's primary
     * audience).
     */
    private static Analyzer pickAnalyzer(List<TurAnalyticsIntent> catalog) {
        Set<String> enHints = Set.of("the", "and", "or", "for", "with", "what",
                "how", "when", "where", "your", "you");
        int en = 0;
        int pt = 0;
        for (TurAnalyticsIntent intent : catalog) {
            String samples = intent.getSamples();
            if (samples == null) continue;
            for (String word : samples.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
                if (enHints.contains(word)) {
                    en++;
                } else if ("o".equals(word) || "a".equals(word) || "de".equals(word)
                        || "para".equals(word) || "que".equals(word) || "com".equals(word)) {
                    pt++;
                }
            }
        }
        return en > pt ? ANALYZER_EN : ANALYZER_PT;
    }

    private static TurChatSessionEnrichment scoreTranscript(AgentIntentIndex index,
            String transcript) {
        try {
            MoreLikeThis mlt = new MoreLikeThis(index.reader());
            mlt.setAnalyzer(index.analyzer());
            mlt.setFieldNames(new String[] { FIELD_SAMPLES });
            mlt.setMinTermFreq(1);
            mlt.setMinDocFreq(1);
            mlt.setMinWordLen(3);
            mlt.setMaxQueryTerms(40);
            Query q = mlt.like(FIELD_SAMPLES, new StringReader(transcript));
            int topK = Math.min(5, index.docCount());
            IndexSearcher searcher = new IndexSearcher(index.reader());
            TopDocs hits = searcher.search(q, topK);
            if (hits.scoreDocs.length == 0) {
                return TurChatSessionEnrichment.unclassified();
            }
            ScoreDoc top = hits.scoreDocs[0];
            if (top.score < MIN_SCORE_FLOOR) {
                return TurChatSessionEnrichment.unclassified();
            }
            StoredFields stored = searcher.storedFields();
            String label = stored.document(top.doc).get(FIELD_LABEL);
            if (label == null || label.isBlank()) {
                return TurChatSessionEnrichment.unclassified();
            }
            double sumScores = 0.0;
            for (ScoreDoc sd : hits.scoreDocs) {
                sumScores += sd.score;
            }
            double confidence = sumScores > 0 ? top.score / sumScores : 0.0;
            List<String> keyTerms = extractKeyTerms(transcript, index.analyzer());
            return new TurChatSessionEnrichment(
                    TurChatIntentLabel.OTHER,   // free-form catalog labels don't map onto the enum
                    confidence,
                    label,                       // catalog label rendered as the session's goal/category
                    TurChatGoalAchieved.UNKNOWN, // outcome not derivable without LLM
                    TurChatSentiment.UNKNOWN,
                    keyTerms,
                    Instant.now());
        } catch (IOException e) {
            log.warn("[LuceneIntentClassifier] MLT search failed: {}", e.getMessage());
            return TurChatSessionEnrichment.unclassified();
        }
    }

    /**
     * Cheap stem dedupe through the same analyzer used to build the
     * index, capped at 5 entries — matches the LLM strategy's
     * {@code keyTerms} cap so downstream consumers see the same shape
     * regardless of which classifier ran.
     */
    private static List<String> extractKeyTerms(String transcript, Analyzer analyzer) {
        Set<String> stems = new HashSet<>();
        try (org.apache.lucene.analysis.TokenStream ts = analyzer.tokenStream(
                FIELD_SAMPLES, transcript)) {
            org.apache.lucene.analysis.tokenattributes.CharTermAttribute term =
                    ts.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken() && stems.size() < 5) {
                String value = term.toString();
                if (value.length() >= 3) {
                    stems.add(value);
                }
            }
            ts.end();
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return new ArrayList<>(stems);
    }

    private static void closeQuietly(Directory directory) {
        try {
            directory.close();
        } catch (IOException e) {
            log.debug("[LuceneIntentClassifier] directory close failed: {}", e.getMessage());
        }
    }

    /**
     * Pre-built per-agent Lucene index for the analytics intent catalog.
     */
    static final class AgentIntentIndex {

        private static final AgentIntentIndex EMPTY = new AgentIntentIndex();

        private final Directory directory;
        private final IndexReader reader;
        private final Analyzer analyzer;
        private final int docCount;

        private AgentIntentIndex() {
            this.directory = null;
            this.reader = null;
            this.analyzer = null;
            this.docCount = 0;
        }

        AgentIntentIndex(Directory directory, Analyzer analyzer, int docCount) throws IOException {
            this.directory = directory;
            this.reader = DirectoryReader.open(directory);
            this.analyzer = analyzer;
            this.docCount = docCount;
        }

        static AgentIntentIndex empty() {
            return EMPTY;
        }

        boolean isEmpty() {
            return docCount == 0;
        }

        IndexReader reader() {
            return reader;
        }

        Analyzer analyzer() {
            return analyzer;
        }

        int docCount() {
            return docCount;
        }

        void close() {
            if (this == EMPTY) return;
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                log.debug("[LuceneIntentClassifier] reader close failed: {}", e.getMessage());
            }
            try {
                if (directory != null) directory.close();
            } catch (IOException e) {
                log.debug("[LuceneIntentClassifier] directory close failed: {}", e.getMessage());
            }
        }
    }

    /** Test-visible accessor for the list of registered samples, post-normalization. */
    List<String> debugNormalizeSamples(String raw) {
        return Arrays.asList(normalizeSamples(raw).split(" "));
    }
}
