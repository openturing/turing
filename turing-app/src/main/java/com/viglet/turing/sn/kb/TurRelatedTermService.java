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
package com.viglet.turing.sn.kb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.dto.kb.TurRelatedTermSuggestionDto;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRelationRepository;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * T678 / §XL (Block AQ) — query-time "related concepts" suggestions built on the
 * microthesaurus {@code RELATED} (RT) links. It reuses the T671 recognition
 * dictionary to spot controlled-vocabulary terms in the raw query, then walks
 * each recognised term's associative ({@code RELATED}) edges — in <em>both</em>
 * directions, because a reciprocal RT pair is stored as a single bidirectional
 * edge (ADR 0003 / T673) — and returns the neighbour labels.
 *
 * <p>Complements the Solr spell-check "did-you-mean" surface: spell-check fixes a
 * typo, this proposes conceptually-related searches. Read-only, defensive (never
 * throws to the caller), and does no query expansion — it is a pure suggestion
 * surface the client can render or ignore.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurRelatedTermService {

    private final TurMicrothesaurusDictionaryService dictionaryService;
    private final TurThesaurusTermRepository termRepository;
    private final TurThesaurusTermRelationRepository relationRepository;

    /**
     * Returns the related-concept suggestions for a query on a site+locale, one
     * entry per recognised term that has at least one {@code RELATED} neighbour.
     * An opted-out site (empty dictionary) or a query with no recognised term
     * yields an empty list.
     */
    @Transactional(readOnly = true)
    public List<TurRelatedTermSuggestionDto> suggest(TurSNSite site, Locale locale, String query) {
        if (site == null || StringUtils.isBlank(query)) {
            return List.of();
        }
        try {
            TurRecognitionDictionary dictionary = dictionaryService.getDictionary(site, locale);
            if (dictionary.isEmpty()) {
                return List.of();
            }
            List<TurRelatedTermSuggestionDto> suggestions = new ArrayList<>();
            for (TurRecognizedTerm recognized : dictionary.recognize(query)) {
                List<String> related = relatedLabels(recognized.termId());
                if (!related.isEmpty()) {
                    suggestions.add(new TurRelatedTermSuggestionDto(recognized.label(), related));
                }
            }
            return suggestions;
        } catch (Exception e) {
            log.warn("[T678] related-term suggestion failed on site '{}': {}",
                    site.getName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Collects the enabled labels of every {@code RELATED} neighbour of the term,
     * following the edge in either direction (the source's outgoing targets and
     * any edge that targets this term), de-duplicated and excluding the term
     * itself.
     */
    private List<String> relatedLabels(String termId) {
        Set<String> neighbourIds = new LinkedHashSet<>();
        termRepository.findById(termId).ifPresent(term ->
                relationRepository.findByTurThesaurusTermAndType(term, TurThesaurusRelationType.RELATED)
                        .forEach(rel -> addIfPresent(neighbourIds, rel.getTargetTermId())));
        for (TurThesaurusTermRelation incoming : relationRepository.findByTargetTermId(termId)) {
            if (incoming.getType() == TurThesaurusRelationType.RELATED
                    && incoming.getTurThesaurusTerm() != null) {
                addIfPresent(neighbourIds, incoming.getTurThesaurusTerm().getId());
            }
        }
        neighbourIds.remove(termId);

        List<String> labels = new ArrayList<>();
        Set<String> seenLabels = new LinkedHashSet<>();
        for (String neighbourId : neighbourIds) {
            termRepository.findById(neighbourId)
                    .filter(TurThesaurusTerm::isEnabled)
                    .map(TurThesaurusTerm::getLabel)
                    .filter(StringUtils::isNotBlank)
                    .filter(seenLabels::add)
                    .ifPresent(labels::add);
        }
        return labels;
    }

    private static void addIfPresent(Set<String> ids, String id) {
        if (StringUtils.isNotBlank(id)) {
            ids.add(id);
        }
    }
}
