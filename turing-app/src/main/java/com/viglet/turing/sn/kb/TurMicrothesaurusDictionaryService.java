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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurusConfig;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermVariation;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusConfigRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;

/**
 * T671 / §XL (Block AQ) — compiles and caches the per-(site, locale)
 * {@link TurRecognitionDictionary}. On a miss it loads the site's <em>enabled</em>
 * microthesaurus selections whose language matches the document locale, walks
 * each tree once to pre-compute every term's root→term path, and registers every
 * surface form (preferred label + variations + — when the config allows —
 * {@code USE}/{@code USED_FOR} synonyms resolving to the preferred term) into a
 * single Aho-Corasick automaton.
 *
 * <p>The compiled dictionary is an immutable read-model held in an in-process
 * map (the caching-skill rule: cache read-models, never repositories, always pair
 * with an eviction). It is evicted on any Knowledge Base / term / selection /
 * config change via {@link #evictAll()} — index-time compilation is rare, so a
 * coarse full flush is correct and cheap.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurMicrothesaurusDictionaryService {

    /** Guards against a corrupt cyclic parent chain when walking ancestors. */
    private static final int MAX_DEPTH = 64;

    private final TurSNSiteMicrothesaurusRepository selectionRepository;
    private final TurSNSiteMicrothesaurusConfigRepository configRepository;
    private final TurMicrothesaurusRepository microthesaurusRepository;
    private final TurThesaurusTermRepository termRepository;

    private final Map<String, TurRecognitionDictionary> cache = new ConcurrentHashMap<>();

    public TurMicrothesaurusDictionaryService(
            TurSNSiteMicrothesaurusRepository selectionRepository,
            TurSNSiteMicrothesaurusConfigRepository configRepository,
            TurMicrothesaurusRepository microthesaurusRepository,
            TurThesaurusTermRepository termRepository) {
        this.selectionRepository = selectionRepository;
        this.configRepository = configRepository;
        this.microthesaurusRepository = microthesaurusRepository;
        this.termRepository = termRepository;
    }

    /** Returns the compiled dictionary for (site, locale), building it on a miss. */
    @Transactional(readOnly = true)
    public TurRecognitionDictionary getDictionary(TurSNSite site, Locale locale) {
        return cache.computeIfAbsent(cacheKey(site, locale), k -> compile(site, locale));
    }

    /** Drops every cached dictionary; call on any KB / term / selection / config change. */
    public void evictAll() {
        cache.clear();
    }

    private static String cacheKey(TurSNSite site, Locale locale) {
        return site.getId() + "|" + (locale == null ? "" : locale.toString());
    }

    private TurRecognitionDictionary compile(TurSNSite site, Locale locale) {
        boolean includeSynonyms = configRepository.findByTurSNSite(site)
                .map(TurSNSiteMicrothesaurusConfig::isIncludeSynonyms).orElse(true);
        TurRecognitionDictionary.Builder builder = TurRecognitionDictionary.builder();
        for (TurSNSiteMicrothesaurus selection :
                selectionRepository.findByTurSNSiteAndEnabledTrue(site)) {
            microthesaurusRepository.findById(selection.getMicrothesaurusId())
                    .filter(m -> localeMatches(m.getLanguage(), locale))
                    .ifPresent(m -> addMicrothesaurus(builder, m, includeSynonyms));
        }
        return builder.build();
    }

    private void addMicrothesaurus(TurRecognitionDictionary.Builder builder,
            TurMicrothesaurus microthesaurus, boolean includeSynonyms) {
        List<TurThesaurusTerm> allTerms = termRepository.findByTurMicrothesaurus(microthesaurus);
        Map<String, TurThesaurusTerm> byId = new HashMap<>();
        allTerms.forEach(t -> byId.put(t.getId(), t));
        Map<String, TurRecognizedTerm> pathCache = new HashMap<>();

        for (TurThesaurusTerm term : allTerms) {
            if (!term.isEnabled()) {
                continue;
            }
            TurThesaurusTerm canonical = resolveCanonical(term, byId, includeSynonyms);
            TurRecognizedTerm ref = pathCache.computeIfAbsent(canonical.getId(),
                    id -> new TurRecognizedTerm(canonical.getId(), canonical.getLabel(),
                            ancestorPath(canonical, byId)));
            // The preferred label matches permissively (case- and accent-insensitive) for recall.
            builder.add(term.getLabel(), false, false, ref);
            for (TurThesaurusTermVariation variation : term.getVariations()) {
                builder.add(variation.getSurfaceForm(), variation.isCaseSensitive(),
                        variation.isAccentSensitive(), ref);
            }
        }
    }

    /**
     * A non-preferred term (one carrying a {@code USE} relation) resolves to its
     * preferred target so its surface forms expand the target's path. Preferred
     * terms resolve to themselves. Disabled or dangling targets fall back to the
     * term itself.
     */
    private TurThesaurusTerm resolveCanonical(TurThesaurusTerm term,
            Map<String, TurThesaurusTerm> byId, boolean includeSynonyms) {
        if (!includeSynonyms) {
            return term;
        }
        for (TurThesaurusTermRelation relation : term.getRelations()) {
            if (relation.getType() == TurThesaurusRelationType.USE
                    && StringUtils.isNotBlank(relation.getTargetTermId())) {
                TurThesaurusTerm target = byId.get(relation.getTargetTermId());
                if (target != null && target.isEnabled()) {
                    return target;
                }
            }
        }
        return term;
    }

    /** Walks {@code parentTermId} up to the root, returning labels root→term inclusive. */
    private List<String> ancestorPath(TurThesaurusTerm term, Map<String, TurThesaurusTerm> byId) {
        List<String> reversed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        TurThesaurusTerm current = term;
        int depth = 0;
        while (current != null && depth++ < MAX_DEPTH && seen.add(current.getId())) {
            reversed.add(current.getLabel());
            String parentId = current.getParentTermId();
            current = StringUtils.isBlank(parentId) ? null : byId.get(parentId);
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static boolean localeMatches(Locale treeLanguage, Locale documentLocale) {
        if (treeLanguage == null) {
            return false;
        }
        if (documentLocale == null) {
            return true;
        }
        return treeLanguage.getLanguage().equalsIgnoreCase(documentLocale.getLanguage());
    }
}
