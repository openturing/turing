/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.answer.TurAnswerShare;
import com.viglet.turing.persistence.repository.answer.TurAnswerShareRepository;
import com.viglet.turing.service.chatslots.TurChatShareOgService;

/**
 * T844 / §LXII.13 — mints and reads back {@link TurAnswerShare answer permalinks}:
 * the immutable snapshot that turns a good answer into a linkable artifact, plus
 * the Open-Graph card (reusing the shipped {@link TurChatShareOgService}) that lets
 * a shared answer unfurl on social / be indexed by crawlers.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurAnswerShareService {

    private static final int QUERY_MAX = 2000;
    private static final int OG_DESCRIPTION_MAX = 200;

    private final TurAnswerShareRepository repository;
    private final TurChatShareOgService shareOgService;

    public TurAnswerShareService(TurAnswerShareRepository repository,
            TurChatShareOgService shareOgService) {
        this.repository = repository;
        this.shareOgService = shareOgService;
    }

    /**
     * Persists an answer snapshot and returns its stable share id.
     *
     * @param siteName      the SN site the answer was produced over
     * @param query         the user's question
     * @param filtersJson   the active facet filter state as JSON (may be {@code null})
     * @param answerText    the synthesized answer markdown
     * @param sourcesJson   the source snapshot as JSON (may be {@code null})
     * @param citationsJson the citation snapshot as JSON (may be {@code null})
     * @param answeredAt    when the answer was produced (may be {@code null})
     * @return the new permalink id
     */
    @Transactional
    public String create(String siteName, String query, String filtersJson, String answerText,
            String sourcesJson, String citationsJson, Instant answeredAt) {
        TurAnswerShare share = new TurAnswerShare();
        share.setSiteName(siteName);
        share.setQuery(truncate(query, QUERY_MAX));
        share.setFiltersJson(filtersJson);
        share.setAnswerText(answerText);
        share.setSourcesJson(sourcesJson);
        share.setCitationsJson(citationsJson);
        share.setAnsweredAt(answeredAt);
        share.setCreationDate(LocalDateTime.now());
        return repository.save(share).getId();
    }

    /** Reads a shared answer snapshot by id. */
    public Optional<TurAnswerShare> find(String id) {
        return repository.findById(id);
    }

    /**
     * Renders the Open-Graph card for a shared answer — its question as the title, a
     * truncated flattening of the answer as the description — redirecting human
     * visitors to {@code dest} (the read-only SPA view). Empty when the id is unknown.
     */
    public Optional<String> renderOgCard(String id, String dest) {
        return find(id).map(share -> {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("title", share.getQuery() == null ? "" : share.getQuery());
            params.put("description", ogDescription(share.getAnswerText()));
            if (dest != null && !dest.isBlank()) {
                params.put("dest", dest);
            }
            return shareOgService.renderCard(params);
        });
    }

    private static String ogDescription(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String flat = answer.replaceAll("\\s+", " ").strip();
        return flat.length() > OG_DESCRIPTION_MAX
                ? flat.substring(0, OG_DESCRIPTION_MAX - 3) + "..."
                : flat;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
