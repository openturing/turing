/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurGenAiProperty.TurGenAiToolPreFilterProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * §IV.2 / T29 — tool catalog pre-filter.
 *
 * <p>When an agent has dozens of MCP / custom / native tools, the LLM
 * pays the input-token cost of every tool's description on every turn,
 * and its tool-selection quality degrades with cardinality. This service
 * runs a fast in-memory Lucene/BM25 match between the user's latest
 * message and each tool's description (the one already loaded by
 * {@link TurToolDescriptionService} from {@code prompts/tools/**.md}),
 * keeping only the {@link TurGenAiToolPreFilterProperty#topK top-K}
 * tools by score.
 *
 * <h2>Always-kept set</h2>
 *
 * Two categories of tools are <em>never</em> filtered out, regardless of
 * BM25 score:
 *
 * <ul>
 *   <li>The agent's {@code nativeTools} (the operator explicitly opted
 *       these into the agent's toolbox — belt-and-suspenders);</li>
 *   <li>The active flow node's {@code requiredTools} (the flow author
 *       explicitly declared the node depends on them — even when the
 *       user message doesn't lexically mention them, e.g. a "compor
 *       proposta" node that triggers the proposal-composer tool after
 *       a slot is collected).</li>
 * </ul>
 *
 * <h2>Bypass conditions</h2>
 *
 * The filter is a transparent no-op (returns the input array unchanged)
 * when:
 *
 * <ol>
 *   <li>{@code turing.genai.tool-prefilter.enabled = false};</li>
 *   <li>the candidate pool is {@code ≤ minToolsThreshold} (no point
 *       paying the filter cost on small catalogs);</li>
 *   <li>the user message is blank (no signal to score against);</li>
 *   <li>{@code topK} already covers the pool;</li>
 *   <li>the always-kept set already covers everything.</li>
 * </ol>
 *
 * <h2>Indexing model</h2>
 *
 * The index is built per-call into a {@link ByteBuffersDirectory} — for
 * ~50 tools this is sub-millisecond and avoids the cache-invalidation
 * headache of tracking when an agent's MCP / custom tool set changes
 * mid-conversation. The two fields per document are:
 *
 * <ul>
 *   <li>{@code name} ({@link StringField}, stored) — looked up to map
 *       back from a hit to the original {@link ToolCallback};</li>
 *   <li>{@code description} ({@link TextField}, not stored) — the
 *       {@code prompts/tools/**.md} content; this is the field MLT
 *       scores against. When a tool has no override file we fall back
 *       to the callback's existing description so the indexed pool is
 *       complete.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurToolPreFilterService {

    private static final String FIELD_NAME = "name";
    private static final String FIELD_DESCRIPTION = "description";

    /**
     * Locale-agnostic analyzer — tool descriptions in
     * {@code prompts/tools/**.md} are predominantly English technical
     * prose (JSON DSL snippets, parameter names, API verbs). Stem
     * accuracy across PT/EN would require per-tool language tagging the
     * pool doesn't carry today; the standard analyzer (lowercase +
     * english stop-words) is the no-regret default for the term-overlap
     * shape this filter is doing.
     */
    private static final Analyzer ANALYZER = new StandardAnalyzer();

    private final TurToolDescriptionService toolDescriptionService;
    private final TurConfigProperties configProperties;
    private final TurChatPipelineObservation pipelineObservation;

    public TurToolPreFilterService(TurToolDescriptionService toolDescriptionService,
            TurConfigProperties configProperties,
            TurChatPipelineObservation pipelineObservation) {
        this.toolDescriptionService = toolDescriptionService;
        this.configProperties = configProperties;
        this.pipelineObservation = pipelineObservation;
    }

    /**
     * Returns the pre-filtered subset of {@code callbacks} to forward to
     * the LLM. The returned array preserves the relative order of the
     * input (always-kept tools first in their original order, then the
     * BM25-ranked survivors in descending score order). Tool identity is
     * matched on {@link ToolCallback#getToolDefinition()}.{@code name()}.
     *
     * @param callbacks      full callback set resolved by the executor
     *                       (already decorated — descriptions overridden,
     *                       logging wrapped).
     * @param userMessage    the user's latest turn — the scoring query.
     * @param alwaysKeep     case-sensitive tool names that must survive
     *                       regardless of score (e.g. agent native tools,
     *                       active node {@code requiredTools}). May be
     *                       null/empty.
     * @return either the input array unchanged (when any bypass condition
     *         fires) or a fresh, length-capped array.
     */
    public ToolCallback[] filter(ToolCallback[] callbacks, String userMessage,
            Set<String> alwaysKeep) {
        long start = System.currentTimeMillis();
        if (callbacks == null || callbacks.length == 0) {
            // Degenerate input — no observability sample (no work to dashboard).
            return callbacks == null ? new ToolCallback[0] : callbacks;
        }
        TurGenAiToolPreFilterProperty props = configProperties.getGenai().getToolPrefilter();
        if (props == null || !props.isEnabled()) {
            recordObservation(callbacks.length, callbacks.length, start,
                    TurMeterNames.REASON_DISABLED);
            return callbacks;
        }
        int topK = Math.max(1, props.getTopK());
        int minThreshold = Math.max(0, props.getMinToolsThreshold());
        if (callbacks.length <= minThreshold) {
            recordObservation(callbacks.length, callbacks.length, start,
                    TurMeterNames.REASON_BELOW_THRESHOLD);
            return callbacks;
        }
        if (userMessage == null || userMessage.isBlank()) {
            recordObservation(callbacks.length, callbacks.length, start,
                    TurMeterNames.REASON_BLANK_QUERY);
            return callbacks;
        }
        Set<String> protectedNames = alwaysKeep == null ? Collections.emptySet() : alwaysKeep;
        // alwaysKeep already covers the entire pool — nothing to filter.
        if (containsAll(callbacks, protectedNames)) {
            recordObservation(callbacks.length, callbacks.length, start,
                    TurMeterNames.REASON_ALREADY_COVERED);
            return callbacks;
        }
        // The protected set alone already meets or exceeds the cap; the
        // BM25 ranking would still help by preferring high-scoring tools
        // among the non-protected ones, but only as a tie-breaker. Keep
        // protected + fill remaining quota with top-scoring callbacks.
        // Map names → original callbacks so we can rebuild the result in
        // the requested order without scanning the array N times.
        Map<String, ToolCallback> byName = indexByName(callbacks);
        if (byName.size() <= topK) {
            recordObservation(callbacks.length, callbacks.length, start,
                    TurMeterNames.REASON_CAP_COVERS_POOL);
            return callbacks;
        }

        // Normal quota for the ranked (non-protected = MCP/custom) pool.
        int protectedCount = countProtected(callbacks, protectedNames);
        int rankedPoolSize = Math.max(0, byName.size() - protectedCount);
        int quotaForRanked = topK - protectedCount;
        // T424 / §XXI — anti-starvation floor. When the always-keep native set
        // alone meets/exceeds topK the quota goes to ≤ 0, which silently dropped
        // EVERY operator-attached MCP/custom tool (real incident: 24 natives,
        // topK=10 → no dspace_* tool ever reached the model). ONLY in that
        // starved case do we reserve up to {@code reserveAttached} slots for the
        // top-scoring attached tools — when the quota is already positive we
        // respect topK and do not inflate it.
        if (quotaForRanked <= 0 && rankedPoolSize > 0) {
            quotaForRanked = Math.min(rankedPoolSize, Math.max(0, props.getReserveAttached()));
        }
        if (quotaForRanked <= 0) {
            // Either no attached tools exist or the floor is disabled (=0):
            // nothing to rank — protected set is the whole result.
            recordObservation(callbacks.length, protectedCount, start,
                    TurMeterNames.REASON_ENGAGED);
            return buildOrderedResult(callbacks, byName, Collections.emptyList(),
                    protectedNames, topK).toArray(new ToolCallback[0]);
        }

        List<String> rankedSurvivors = rankByBm25(byName, protectedNames, userMessage,
                quotaForRanked);

        List<ToolCallback> result = buildOrderedResult(callbacks, byName, rankedSurvivors,
                protectedNames, topK);
        if (log.isDebugEnabled()) {
            log.debug("[ToolPreFilter] {} → {} tools (protected={}, ranked={}) for msg=\"{}\"",
                    callbacks.length, result.size(), protectedNames.size(),
                    rankedSurvivors.size(), truncate(userMessage, 60));
        } else {
            log.info("[ToolPreFilter] {} → {} tools (top-K={}, threshold={})",
                    callbacks.length, result.size(), topK, minThreshold);
        }
        recordObservation(callbacks.length, result.size(), start, TurMeterNames.REASON_ENGAGED);
        return result.toArray(new ToolCallback[0]);
    }

    /** Indexes callbacks by tool name (first wins), skipping nameless ones. */
    private Map<String, ToolCallback> indexByName(ToolCallback[] callbacks) {
        Map<String, ToolCallback> byName = new LinkedHashMap<>();
        for (ToolCallback cb : callbacks) {
            String name = safeName(cb);
            if (name == null) continue;
            byName.putIfAbsent(name, cb);
        }
        return byName;
    }

    /** Assembles the survivor list: protected tools (original order) then ranked survivors (score order). */
    private List<ToolCallback> buildOrderedResult(ToolCallback[] callbacks,
            Map<String, ToolCallback> byName, List<String> rankedSurvivors,
            Set<String> protectedNames, int topK) {
        List<ToolCallback> result = new ArrayList<>(topK + protectedNames.size());
        Set<String> emitted = new HashSet<>();
        // 1) protected, in original order
        for (ToolCallback cb : callbacks) {
            String name = safeName(cb);
            if (name != null && protectedNames.contains(name) && emitted.add(name)) {
                result.add(cb);
            }
        }
        // 2) ranked survivors, in score order
        for (String name : rankedSurvivors) {
            if (emitted.add(name)) {
                ToolCallback cb = byName.get(name);
                if (cb != null) result.add(cb);
            }
        }
        return result;
    }

    private void recordObservation(int input, int output, long startMillis, String reason) {
        pipelineObservation.recordToolPrefilter(input, output,
                System.currentTimeMillis() - startMillis, reason);
    }

    /**
     * Builds the transient in-memory index over the non-protected tools
     * and returns the top-{@code quota} names by BM25 score against the
     * user message. Protected tools are excluded from the index so they
     * don't consume slots intended for tools the operator hasn't
     * already curated.
     */
    private List<String> rankByBm25(Map<String, ToolCallback> byName,
            Set<String> protectedNames, String userMessage, int quota) {
        if (quota <= 0) {
            return Collections.emptyList();
        }
        try (Directory directory = new ByteBuffersDirectory()) {
            IndexWriterConfig cfg = new IndexWriterConfig(ANALYZER)
                    .setSimilarity(new BM25Similarity());
            int docs;
            try (IndexWriter writer = new IndexWriter(directory, cfg)) {
                docs = indexToolDescriptions(writer, byName, protectedNames);
                writer.commit();
            }
            if (docs == 0) {
                return Collections.emptyList();
            }
            try (IndexReader reader = DirectoryReader.open(directory)) {
                MoreLikeThis mlt = new MoreLikeThis(reader);
                mlt.setAnalyzer(ANALYZER);
                mlt.setFieldNames(new String[] { FIELD_DESCRIPTION });
                mlt.setMinTermFreq(1);
                mlt.setMinDocFreq(1);
                mlt.setMinWordLen(3);
                mlt.setMaxQueryTerms(40);
                Query q = mlt.like(FIELD_DESCRIPTION, new java.io.StringReader(userMessage));
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs hits = searcher.search(q, Math.min(quota, docs));
                if (hits.scoreDocs.length == 0) {
                    // No term overlap — degenerate to "keep the first
                    // {quota} tools in insertion order" so the LLM still
                    // gets a workable pool. Without this an unrelated
                    // greeting would drop the entire ranked set and the
                    // agent could be left with zero tools.
                    return fallbackInsertionOrder(byName, protectedNames, quota);
                }
                return collectRankedSurvivors(hits, searcher.storedFields(),
                        byName, protectedNames, quota);
            }
        } catch (IOException e) {
            log.warn("[ToolPreFilter] BM25 ranking failed, falling back to all tools: {}",
                    e.getMessage());
            return new ArrayList<>(byName.keySet());
        }
    }

    /**
     * Indexes one BM25 document per non-protected tool that has a usable
     * description. Returns the number of documents written.
     */
    private int indexToolDescriptions(IndexWriter writer, Map<String, ToolCallback> byName,
            Set<String> protectedNames) throws IOException {
        int docs = 0;
        for (Map.Entry<String, ToolCallback> entry : byName.entrySet()) {
            String name = entry.getKey();
            if (protectedNames.contains(name)) {
                continue;
            }
            String description = resolveDescription(name, entry.getValue());
            if (description != null && !description.isBlank()) {
                Document doc = new Document();
                doc.add(new StringField(FIELD_NAME, name, Field.Store.YES));
                doc.add(new TextField(FIELD_DESCRIPTION, description, Field.Store.NO));
                writer.addDocument(doc);
                docs++;
            }
        }
        return docs;
    }

    /**
     * Collects the ranked hit names, then deterministically fills any remaining
     * quota from insertion order so the agent never gets fewer tools than top-K.
     */
    private List<String> collectRankedSurvivors(TopDocs hits, StoredFields stored,
            Map<String, ToolCallback> byName, Set<String> protectedNames, int quota)
            throws IOException {
        List<String> survivors = new ArrayList<>(hits.scoreDocs.length);
        for (ScoreDoc sd : hits.scoreDocs) {
            String name = stored.document(sd.doc).get(FIELD_NAME);
            if (name != null) survivors.add(name);
        }
        if (survivors.size() < quota) {
            for (String name : fallbackInsertionOrder(byName, protectedNames, quota)) {
                if (survivors.size() >= quota) break;
                if (!survivors.contains(name)) survivors.add(name);
            }
        }
        return survivors;
    }

    private static List<String> fallbackInsertionOrder(Map<String, ToolCallback> byName,
            Set<String> protectedNames, int quota) {
        List<String> result = new ArrayList<>(quota);
        for (String name : byName.keySet()) {
            if (result.size() >= quota) {
                break;
            }
            if (!protectedNames.contains(name)) {
                result.add(name);
            }
        }
        return result;
    }

    private String resolveDescription(String name, ToolCallback callback) {
        String override = toolDescriptionService.getDescription(name);
        if (override != null && !override.isBlank()) {
            return override;
        }
        // Fall back to the @Tool description (may be the "." placeholder).
        // The pipeline wrap already replaced "." when an .md exists, but
        // the underlying callback's definition still reads the raw value
        // — we re-fetch via getToolDefinition() to honor either path.
        try {
            String desc = callback.getToolDefinition().description();
            if (desc != null && !desc.isBlank() && !".".equals(desc.strip())) {
                return desc;
            }
        } catch (RuntimeException e) {
            log.debug("[ToolPreFilter] could not read description for '{}': {}", name, e.getMessage());
        }
        // Last-resort: index by name so the tool still has *some* token
        // surface to be matched against (better than 0 docs).
        return name;
    }

    private static boolean containsAll(ToolCallback[] callbacks, Set<String> names) {
        if (names.isEmpty()) return false;
        for (ToolCallback cb : callbacks) {
            String name = safeName(cb);
            if (name != null && !names.contains(name)) return false;
        }
        return true;
    }

    private static int countProtected(ToolCallback[] callbacks, Set<String> names) {
        if (names.isEmpty()) return 0;
        int count = 0;
        Set<String> seen = new HashSet<>();
        for (ToolCallback cb : callbacks) {
            String name = safeName(cb);
            if (name != null && names.contains(name) && seen.add(name)) count++;
        }
        return count;
    }

    private static String safeName(ToolCallback cb) {
        try {
            return cb.getToolDefinition().name();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Test-visible accessor for the analyzer-tokenized form of a string. */
    List<String> debugTokenize(String input) {
        List<String> tokens = new ArrayList<>();
        try (org.apache.lucene.analysis.TokenStream ts = ANALYZER.tokenStream(FIELD_DESCRIPTION, input)) {
            org.apache.lucene.analysis.tokenattributes.CharTermAttribute term =
                    ts.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                tokens.add(term.toString());
            }
            ts.end();
        } catch (IOException e) {
            return Arrays.asList(input.toLowerCase().split("\\s+"));
        }
        return tokens;
    }
}
