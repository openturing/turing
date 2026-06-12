/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

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
 * T337 / §XVII.1 — LLM-as-reranker, the legacy default strategy (T328).
 *
 * <p>Asks a chat model which numbered snippets actually bear on the query and
 * keeps the top-k in the model's order. The model is asked for a plain ranked
 * list of numbers (not structured output) and the reply is parsed leniently —
 * any integers, in order, deduped, kept if a valid 1-based snippet number. This
 * sidesteps the structured-output fragility documented for small models.
 *
 * <p>This is the verbatim move of the original {@code TurRagReranker} ranking
 * logic into a strategy; behavior is unchanged. Returns an empty list when the
 * chat model is absent so the {@link com.viglet.turing.genai.rag.TurRagReranker}
 * facade keeps retrieval order.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
public class TurLlmRerankStrategy implements TurRagRerankStrategy {

    /** Caps the chunk preview fed to the ranker so the prompt stays bounded. */
    private static final int MAX_PREVIEW_CHARS = 600;

    private static final Pattern INTEGER = Pattern.compile("\\d+");

    @Override
    public TurRagRerankStrategyType getType() {
        return TurRagRerankStrategyType.LLM;
    }

    @Override
    public List<Document> rerank(TurRagRerankRequest request) {
        ChatModel chatModel = request.chatModel();
        List<Document> candidates = request.candidates();
        int topK = request.topK();
        if (chatModel == null) {
            log.debug("[RAG] LLM reranker: no chat model available; keeping retrieval order");
            return List.of();
        }
        String answer = chatModel.call(new Prompt(buildPrompt(request.query(), candidates)))
                .getResult().getOutput().getText();
        List<Integer> order = parseRankedIndices(answer, candidates.size());
        if (order.isEmpty()) {
            log.debug("[RAG] LLM reranker returned no parseable indices; keeping retrieval order");
            return List.of();
        }
        List<Document> reranked = new ArrayList<>(topK);
        for (Integer idx : order) {
            reranked.add(candidates.get(idx));
            if (reranked.size() == topK) {
                break;
            }
        }
        log.debug("[RAG] LLM reranked {} candidate(s) → top {} (order={})",
                candidates.size(), reranked.size(), order);
        return reranked;
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
