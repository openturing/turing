/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * T328 / §XVII.4 — optional LLM-as-reranker for the SN RAG path.
 *
 * <p>The hybrid retriever orders candidates by vector/BM25 score, which is a
 * coarse proxy for "answers the question". When the relevance gate alone is too
 * blunt, this stage re-orders the top-N candidates by asking a chat model which
 * chunks actually bear on the query, then keeps the top-k for stuffing — fewer,
 * higher-precision chunks in the prompt.
 *
 * <p><b>Provider-agnostic and fail-open.</b> Rather than risk the structured-
 * output fragility documented for small models, the model is asked for a plain
 * ranked list of chunk numbers; the response is parsed leniently (any integers,
 * in order). On <em>any</em> failure — empty/garbled response, exception, no
 * parseable indices — the original retrieval order's top-k is returned
 * unchanged, so enabling the reranker can never make retrieval worse than the
 * gate-only path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurRagReranker {

    /** Caps the chunk preview fed to the ranker so the prompt stays bounded. */
    private static final int MAX_PREVIEW_CHARS = 600;

    private static final Pattern INTEGER = Pattern.compile("\\d+");

    /**
     * Re-orders {@code candidates} by query relevance and returns the top
     * {@code topK}. Returns the input's first {@code topK} (retrieval order)
     * unchanged when reranking can't run or adds no signal.
     *
     * @param chatModel  the model used to rank (typically the agent's chat model)
     * @param query      the user query the chunks should answer
     * @param candidates the retrieved candidate chunks, in retrieval order
     * @param topK       how many chunks to keep for stuffing
     */
    public List<Document> rerank(ChatModel chatModel, String query,
            List<Document> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return candidates == null ? List.of() : candidates;
        }
        List<Document> firstK = candidates.size() > topK
                ? new ArrayList<>(candidates.subList(0, topK))
                : new ArrayList<>(candidates);
        // Nothing to re-order: fewer candidates than we'd keep anyway.
        if (candidates.size() <= topK || chatModel == null
                || query == null || query.isBlank()) {
            return firstK;
        }
        try {
            String answer = chatModel.call(new Prompt(buildPrompt(query, candidates)))
                    .getResult().getOutput().getText();
            List<Integer> order = parseRankedIndices(answer, candidates.size());
            if (order.isEmpty()) {
                log.debug("[RAG] reranker returned no parseable indices; keeping retrieval order");
                return firstK;
            }
            List<Document> reranked = new ArrayList<>(topK);
            for (Integer idx : order) {
                reranked.add(candidates.get(idx));
                if (reranked.size() == topK) {
                    break;
                }
            }
            log.debug("[RAG] reranked {} candidate(s) → top {} (order={})",
                    candidates.size(), reranked.size(), order);
            return reranked;
        } catch (RuntimeException e) {
            log.warn("[RAG] reranker failed ({}); keeping retrieval order", e.getMessage());
            return firstK;
        }
    }

    private static String buildPrompt(String query, List<Document> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a search relevance ranker. Given a user question and a numbered list of ")
                .append("document snippets, decide which snippets actually help answer the question.\n\n")
                .append("Question: ").append(query).append("\n\nSnippets:\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append('[').append(i + 1).append("] ").append(preview(candidates.get(i))).append('\n');
        }
        sb.append("\nReply with ONLY the snippet numbers most relevant to the question, ")
                .append("most relevant first, separated by commas (e.g. \"3, 1, 5\"). ")
                .append("Omit snippets that are off-topic. Do not explain.");
        return sb.toString();
    }

    private static String preview(Document doc) {
        String text = doc.getText() == null ? "" : doc.getText().strip().replaceAll("\\s+", " ");
        return text.length() > MAX_PREVIEW_CHARS ? text.substring(0, MAX_PREVIEW_CHARS) + "…" : text;
    }

    /**
     * Lenient parse: pulls every integer out of the model's reply, in order,
     * keeps the ones that are valid 1-based snippet numbers, de-dupes, and
     * converts to 0-based indices. Tolerates prose, fences, and stray text.
     */
    private static List<Integer> parseRankedIndices(String answer, int count) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        Set<Integer> seen = new LinkedHashSet<>();
        Matcher m = INTEGER.matcher(answer);
        while (m.find()) {
            int oneBased;
            try {
                oneBased = Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                continue;
            }
            if (oneBased >= 1 && oneBased <= count) {
                seen.add(oneBased - 1);
            }
        }
        return new ArrayList<>(seen);
    }
}
