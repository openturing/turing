/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatmemory;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
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
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

import lombok.extern.slf4j.Slf4j;

/**
 * T30 / §IV.4 — conversation-relevance retrieval for chat memory.
 *
 * <p>The legacy chat-memory store gives the LLM a chronological FIFO of the
 * last {@code chatMemoryMaxMessages} turns (default 100). For long
 * conversations, the early turns scroll off and any context they carried (a
 * preference the user stated on turn 3 of a 30-turn chat, a constraint the
 * agent committed to on turn 5, …) is invisible to the model from turn ~25
 * onwards.
 *
 * <p>This service augments the recent-N window passed to the executor with
 * the most relevant OLDER turns retrieved by BM25:
 *
 * <ol>
 *   <li>Read the full persisted history from {@link TurChatMemoryStore}
 *       (capped at {@link TurAIAgent#chatMemoryMaxMessages}).</li>
 *   <li>Split into <em>recent</em> (last
 *       {@link TurAIAgent#chatMemoryRecentN} entries) and <em>older</em>
 *       (everything before).</li>
 *   <li>Build a per-conversation Lucene {@link ByteBuffersDirectory} over
 *       the older pool, scored with {@link BM25Similarity}.</li>
 *   <li>Score the latest user message against the older pool via
 *       {@link MoreLikeThis} and keep the top
 *       {@link TurAIAgent#chatMemoryRelevanceTopK} hits.</li>
 *   <li>Sort the retrieved set chronologically (oldest first), prefix each
 *       turn with a position/timestamp marker so the LLM can place it on
 *       the conversation timeline despite the out-of-order injection, and
 *       prepend the resulting block to the existing client-supplied
 *       history.</li>
 * </ol>
 *
 * <h2>Bypass conditions</h2>
 *
 * The retriever is a transparent no-op (returns the input list unchanged)
 * when any of:
 *
 * <ul>
 *   <li>{@code agent.chatMemoryEnabled = false} — the store isn't receiving
 *       writes for this agent, nothing useful to read;</li>
 *   <li>{@code agent.chatMemoryRelevanceEnabled = false} — operator opt-in
 *       is required;</li>
 *   <li>{@link TurChatMemoryStore#isEnabled()} is false — backend is the
 *       NoOp implementation;</li>
 *   <li>{@code conversationId} is blank — without it the store cannot key
 *       the read;</li>
 *   <li>The latest user message is blank — no signal to score against;</li>
 *   <li>The persisted history has {@code &le; chatMemoryRecentN} entries —
 *       there are no older turns to rescue;</li>
 *   <li>BM25 returns zero hits — we don't prepend empty filler.</li>
 * </ul>
 *
 * <h2>Indexing model</h2>
 *
 * The index is built per-call (one tiny {@link ByteBuffersDirectory} per
 * chat turn, then closed) — sub-millisecond for the typical &lt;200-message
 * pool. This matches the pattern used by
 * {@link com.viglet.turing.genai.tool.TurToolPreFilterService} for the tool
 * pre-filter: stateless, no cache, no eviction headaches. Per-conversation
 * caching is intentionally avoided because most conversations only see a
 * handful of turns and the cache would be cold on the first call anyway.
 *
 * <p>Each document carries:
 *
 * <ul>
 *   <li>{@code position}    — chronological index in the persisted list,
 *                              stored so the hit can map back to the original
 *                              message;</li>
 *   <li>{@code content}     — the message body, indexed as
 *                              {@link TextField} so BM25 scores it; analyzer
 *                              is {@link StandardAnalyzer} (locale-agnostic,
 *                              same trade-off as the tool pre-filter — chat
 *                              messages may be PT or EN and we don't yet
 *                              tag turns with their language).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatMemoryRelevanceRetriever {

    static final String FIELD_POSITION = "position";
    static final String FIELD_CONTENT = "content";

    /** Hard upper bound on retrieved set size — guards against pathological config. */
    static final int RELEVANCE_TOP_K_MAX = 20;

    /** Hard upper bound on the persisted-read size, matches store contract. */
    static final int MAX_READ_LIMIT = 1000;

    /**
     * Locale-agnostic analyzer. Chat messages in this codebase are PT or EN
     * with no per-message language tag; per-locale analyzers would require
     * either detection (extra cost on the hot path) or a schema change.
     * {@link StandardAnalyzer} (lowercase + EN stop-words) is the no-regret
     * default for the term-overlap shape this retriever does — the same
     * choice {@link com.viglet.turing.genai.tool.TurToolPreFilterService}
     * landed on.
     */
    private static final Analyzer ANALYZER = new StandardAnalyzer();

    private final TurChatMemoryStore store;
    private final TurChatPipelineObservation pipelineObservation;

    public TurChatMemoryRelevanceRetriever(TurChatMemoryStore store,
            TurChatPipelineObservation pipelineObservation) {
        this.store = store;
        this.pipelineObservation = pipelineObservation;
    }

    /**
     * Returns an augmented history for the given turn. The returned list is
     * {@code [retrieved-older-turns-with-markers] + history}; when any
     * bypass condition fires, the original {@code history} is returned
     * unchanged (never null).
     *
     * @param agent             agent being executed — read for the relevance
     *                          knobs (enabled flag, topK, recentN, max
     *                          messages cap).
     * @param conversationId    persisted-memory key; blank means no-op.
     * @param latestUserMessage the user's latest turn — the scoring query.
     * @param history           the client-supplied recent context (used
     *                          as-is for the FIFO tail of the result).
     * @return the augmented history, or the unmodified input on any bypass
     *         or error path.
     */
    public List<ChatMessageItem> enrich(TurAIAgent agent,
                                        String conversationId,
                                        String latestUserMessage,
                                        List<ChatMessageItem> history) {
        if (history == null) {
            // No timer sample on the null-history degenerate case — caller
            // misuse, not a runtime path worth dashboarding.
            return Collections.emptyList();
        }
        List<ChatMessageItem> prefix = retrievePrefix(agent, conversationId, latestUserMessage);
        if (prefix.isEmpty()) {
            return history;
        }
        List<ChatMessageItem> augmented = new ArrayList<>(prefix.size() + history.size());
        augmented.addAll(prefix);
        augmented.addAll(history);
        return augmented;
    }

    /**
     * Returns <em>only</em> the relevance-retrieved older turns (marked up with
     * their position/timestamp), in chronological order — the block {@link #enrich}
     * prepends to the history. Never {@code null}; empty on any bypass condition,
     * store error, or when BM25 finds nothing on-topic.
     *
     * <p>This is the message-level entry point the T616
     * {@link com.viglet.turing.genai.prompt.TurMessageContributor} pipeline calls:
     * the retriever contributes a history <em>prefix</em>, and the assembler owns
     * the single concatenation with the client history. {@link #enrich} is retained
     * as the {@code prefix + history} convenience for the legacy inline path.
     *
     * @param agent             agent being executed — read for the relevance knobs.
     * @param conversationId    persisted-memory key; blank means no-op.
     * @param latestUserMessage the user's latest turn — the scoring query.
     * @return the retrieved older turns, or an empty list on any bypass/error path.
     */
    public List<ChatMessageItem> retrievePrefix(TurAIAgent agent,
                                                String conversationId,
                                                String latestUserMessage) {
        long start = System.currentTimeMillis();
        if (!isRelevanceApplicable(agent, conversationId, latestUserMessage)) {
            recordBypass(start);
            return Collections.emptyList();
        }

        int recentN = Math.max(0, agent.getChatMemoryRecentN());
        int topK = Math.clamp(agent.getChatMemoryRelevanceTopK(), 1, RELEVANCE_TOP_K_MAX);
        int readLimit = clamp(agent.getChatMemoryMaxMessages(), 1, MAX_READ_LIMIT);

        List<Map<String, Object>> persisted;
        try {
            persisted = store.findMessages(conversationId, readLimit);
        } catch (RuntimeException e) {
            log.warn("[ChatMemoryRelevance] store read failed for conv '{}': {}",
                    conversationId, e.getMessage());
            recordBypass(start);
            return Collections.emptyList();
        }
        if (persisted == null || persisted.size() <= recentN) {
            // Either nothing persisted yet (first turn after enablement) or
            // the recent-N window already covers the entire pool — no
            // "older" turns to rescue.
            recordBypass(start);
            return Collections.emptyList();
        }

        int olderCount = persisted.size() - recentN;
        List<PersistedMessage> older = parseOlderMessages(persisted, olderCount);
        if (older.isEmpty()) {
            recordBypass(start);
            return Collections.emptyList();
        }

        List<PersistedMessage> retrieved = retrieveRelevant(older, latestUserMessage, topK);
        if (retrieved.isEmpty()) {
            // BM25 ran but found nothing on-topic — distinct outcome from a
            // pure bypass because dashboards want to see this case
            // (the retrieval cost was paid but produced no signal).
            pipelineObservation.recordMemoryRetrieval(older.size(), 0,
                    System.currentTimeMillis() - start, TurMeterNames.OUTCOME_NO_HITS);
            return Collections.emptyList();
        }

        // Build the prepended block in chronological order so the LLM sees
        // the retrieved past in the order it originally happened, despite
        // the BM25 ranking dropping turns out of sequence.
        retrieved.sort((a, b) -> Integer.compare(a.position(), b.position()));

        List<ChatMessageItem> prefix = new ArrayList<>(retrieved.size());
        for (PersistedMessage m : retrieved) {
            prefix.add(new ChatMessageItem(m.role(), markup(m)));
        }
        if (log.isDebugEnabled()) {
            log.debug("[ChatMemoryRelevance] conv '{}': retrieved {} older turn(s) from a pool of {} (recent-N={})",
                    conversationId, retrieved.size(), older.size(), recentN);
        }
        pipelineObservation.recordMemoryRetrieval(older.size(), retrieved.size(),
                System.currentTimeMillis() - start, TurMeterNames.OUTCOME_HITS);
        return prefix;
    }

    /**
     * True when relevance retrieval should run: the agent has it enabled, the
     * store is up, and both the conversation id and latest user message are
     * present.
     */
    private boolean isRelevanceApplicable(TurAIAgent agent, String conversationId,
            String latestUserMessage) {
        if (agent == null || !agent.isChatMemoryEnabled() || !agent.isChatMemoryRelevanceEnabled()) {
            return false;
        }
        if (!store.isEnabled()) {
            return false;
        }
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }
        return latestUserMessage != null && !latestUserMessage.isBlank();
    }

    /**
     * Parses the first {@code olderCount} persisted entries into
     * {@link PersistedMessage}s, skipping rows missing a role or content.
     */
    private List<PersistedMessage> parseOlderMessages(List<Map<String, Object>> persisted,
            int olderCount) {
        List<PersistedMessage> older = new ArrayList<>(olderCount);
        for (int i = 0; i < olderCount; i++) {
            Map<String, Object> raw = persisted.get(i);
            String role = asString(raw.get("role"));
            String content = asString(raw.get(FIELD_CONTENT));
            String timestamp = asString(raw.get("timestamp"));
            if (role == null || role.isBlank() || content == null || content.isBlank()) {
                continue;
            }
            older.add(new PersistedMessage(i, role, content, timestamp));
        }
        return older;
    }

    private void recordBypass(long startMillis) {
        pipelineObservation.recordMemoryRetrieval(-1, -1,
                System.currentTimeMillis() - startMillis, TurMeterNames.OUTCOME_BYPASS);
    }

    /**
     * Builds an in-memory BM25 index over the older messages, scores the
     * latest user message via {@link MoreLikeThis}, returns the top-K hits
     * in score-descending order. Empty pool / IO failure → empty list.
     */
    private List<PersistedMessage> retrieveRelevant(List<PersistedMessage> older,
                                                     String query, int topK) {
        if (older.isEmpty()) {
            return Collections.emptyList();
        }
        int cap = Math.min(topK, older.size());
        try (Directory directory = new ByteBuffersDirectory()) {
            IndexWriterConfig cfg = new IndexWriterConfig(ANALYZER)
                    .setSimilarity(new BM25Similarity());
            try (IndexWriter writer = new IndexWriter(directory, cfg)) {
                for (PersistedMessage m : older) {
                    Document doc = new Document();
                    doc.add(new StoredField(FIELD_POSITION, m.position()));
                    doc.add(new TextField(FIELD_CONTENT, m.content(), Field.Store.NO));
                    writer.addDocument(doc);
                }
                writer.commit();
            }
            try (IndexReader reader = DirectoryReader.open(directory)) {
                MoreLikeThis mlt = new MoreLikeThis(reader);
                mlt.setAnalyzer(ANALYZER);
                mlt.setFieldNames(new String[] { FIELD_CONTENT });
                mlt.setMinTermFreq(1);
                mlt.setMinDocFreq(1);
                mlt.setMinWordLen(3);
                mlt.setMaxQueryTerms(40);
                Query q = mlt.like(FIELD_CONTENT, new StringReader(query));
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs hits = searcher.search(q, cap);
                if (hits.scoreDocs.length == 0) {
                    return Collections.emptyList();
                }
                StoredFields stored = searcher.storedFields();
                Set<Integer> emittedPositions = new LinkedHashSet<>();
                List<PersistedMessage> survivors = new ArrayList<>(hits.scoreDocs.length);
                for (ScoreDoc sd : hits.scoreDocs) {
                    Number raw = stored.document(sd.doc).getField(FIELD_POSITION).numericValue();
                    if (raw != null) {
                        int position = raw.intValue();
                        if (position >= 0 && position < older.size() && emittedPositions.add(position)) {
                            survivors.add(older.get(position));
                        }
                    }
                }
                return survivors;
            }
        } catch (IOException e) {
            log.warn("[ChatMemoryRelevance] BM25 retrieval failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Formats a retrieved message with a position/timestamp prefix so the
     * LLM understands the turn was lifted out of its original order. Marker
     * format is intentionally short — chat tokens are expensive — and
     * matches the structure documented in §IV.4.
     */
    static String markup(PersistedMessage m) {
        String tsTag = formatTimestamp(m.timestamp());
        // Use a position offset starting at 1 so the marker reads naturally
        // ("earlier turn #4") rather than being zero-indexed.
        int displayPosition = m.position() + 1;
        if (tsTag == null) {
            return "[earlier turn #" + displayPosition + "] " + m.content();
        }
        return "[earlier turn #" + displayPosition + " at " + tsTag + "] " + m.content();
    }

    /**
     * Normalises persisted timestamps to a compact {@code yyyy-MM-dd'T'HH:mm:ssX}
     * form. The store may have written either ISO-8601 with offset (Mongo
     * Instant.toString()) or epoch millis (Redis legacy path). Anything
     * unparseable degrades to {@code null} so the marker just omits the
     * timestamp piece instead of crashing the retrieval.
     */
    private static String formatTimestamp(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            Instant instant = Instant.parse(raw);
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        } catch (DateTimeParseException ignored) {
            try {
                long epoch = Long.parseLong(raw);
                return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epoch));
            } catch (NumberFormatException ignoredToo) {
                return raw;
            }
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Internal representation of a persisted turn. Package-private for tests. */
    record PersistedMessage(int position, String role, String content, String timestamp) {
    }
}
