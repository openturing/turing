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
package com.viglet.turing.sn.synonym;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.dto.sn.synonym.TurSNSynonymSupportDto;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSESynonymApplyResult;
import com.viglet.turing.plugins.se.TurSESynonymRule;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * T663 / §XXXIX (Block AP) — orchestrates pushing a site's synonym rules into
 * its search engine at query time. It resolves the engine plugin for the site
 * and, for each locale, calls {@code applySynonyms} against that locale's core
 * with the engine-neutral rules from {@link TurSNSynonymService}.
 *
 * <p>Every public method takes the site <em>id</em> and re-loads the site inside
 * a read transaction, so walking the lazy {@code site → seInstance → vendor}
 * graph is safe (the app runs with {@code enable_lazy_load_no_trans=false}).
 * The engine push is infrequent, admin-triggered, and Lucene short-circuits with
 * no I/O, so running it inside the read transaction is acceptable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSNSynonymApplyService {

    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSynonymService synonymService;
    private final TurSNSiteRepository siteRepository;
    private final TurSNSiteLocaleRepository siteLocaleRepository;

    public TurSNSynonymApplyService(TurSearchEnginePluginFactory pluginFactory,
            TurSNSynonymService synonymService,
            TurSNSiteRepository siteRepository,
            TurSNSiteLocaleRepository siteLocaleRepository) {
        this.pluginFactory = pluginFactory;
        this.synonymService = synonymService;
        this.siteRepository = siteRepository;
        this.siteLocaleRepository = siteLocaleRepository;
    }

    /** Whether the site's engine supports query-time synonyms. */
    @Transactional(readOnly = true)
    public TurSNSynonymSupportDto support(String siteId) {
        TurSNSite site = siteRepository.findById(siteId).orElse(null);
        TurSearchEnginePlugin plugin = pluginFactory.getPluginForSite(site);
        return new TurSNSynonymSupportDto(plugin.getPluginType(), plugin.supportsSynonyms());
    }

    /** Pushes synonyms for every locale of the site; keyed by locale tag. */
    @Transactional(readOnly = true)
    public Map<String, TurSESynonymApplyResult> applyAll(String siteId) {
        TurSNSite site = siteRepository.findById(siteId).orElse(null);
        Map<String, TurSESynonymApplyResult> results = new LinkedHashMap<>();
        if (site == null) {
            return results;
        }
        for (TurSNSiteLocale locale : siteLocaleRepository.findByTurSNSite(site)) {
            results.put(locale.getLanguage().toLanguageTag(), applyForLocale(site, locale));
        }
        return results;
    }

    /** Pushes synonyms for a single locale of the site. */
    @Transactional(readOnly = true)
    public TurSESynonymApplyResult apply(String siteId, Locale language) {
        TurSNSite site = siteRepository.findById(siteId).orElse(null);
        if (site == null) {
            return new TurSESynonymApplyResult(false, 0, Set.of(),
                    List.of("SN Site not found: " + siteId));
        }
        Optional<TurSNSiteLocale> locale = siteLocaleRepository.findByTurSNSite(site).stream()
                .filter(l -> l.getLanguage() != null && l.getLanguage().equals(language))
                .findFirst();
        return locale.map(l -> applyForLocale(site, l))
                .orElseGet(() -> new TurSESynonymApplyResult(false, 0, Set.of(),
                        List.of("Site has no locale/core for " + language)));
    }

    private TurSESynonymApplyResult applyForLocale(TurSNSite site, TurSNSiteLocale locale) {
        TurSEInstance seInstance = site.getTurSEInstance();
        if (seInstance == null) {
            return new TurSESynonymApplyResult(false, 0, Set.of(),
                    List.of("Site has no search engine instance; cannot apply synonyms."));
        }
        List<TurSESynonymRule> rules = synonymService.rulesForApply(site, locale.getLanguage());
        TurSESynonymApplyResult result = pluginFactory.getPluginForSite(site)
                .applySynonyms(seInstance, locale.getCore(), locale.getLanguage(), rules);
        log.info("Applied {} synonym rule(s) to core '{}' ({}) — supported={}, unsupportedTypes={}",
                result.applied(), locale.getCore(), locale.getLanguage(), result.supported(),
                result.unsupportedTypes());
        return result;
    }
}
