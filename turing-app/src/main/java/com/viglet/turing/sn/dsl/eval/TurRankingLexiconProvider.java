/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T814 / §LVIII.1 (Block BJ) — serves the {@link TurRankingLexicon} the
 * {@link TurNLRankingPlanner} ranks with, read from a classpath resource
 * ({@code ranking/lexicon-<language>.json}) instead of compiled into the planner.
 *
 * <p>Resolution is cached per language for the life of the bean: the bundles are
 * immutable resources, and building one costs a JSON parse plus a regex compile.
 *
 * <p><strong>Fail-open by construction.</strong> A missing or unreadable resource
 * is logged once and served as an empty lexicon, which resolves no direction and
 * therefore no sort — exactly the degradation a query with no superlative already
 * takes. Ranking never throws, and a broken lexicon never costs the caller its
 * facet parse.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurRankingLexiconProvider {

    private static final String RESOURCE_PATTERN = "ranking/lexicon-%s.json";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final ConcurrentMap<String, TurRankingLexicon> byLanguage = new ConcurrentHashMap<>();

    /** The default ({@code en}) lexicon — what T811's hardcoded sets became. */
    public TurRankingLexicon lexicon() {
        return lexicon(TurRankingLexicon.DEFAULT_LANGUAGE);
    }

    /**
     * The lexicon for {@code language}, built and cached on first use. An absent
     * bundle yields an empty lexicon rather than an exception; T816 is what turns
     * that into a fallback onto {@link TurRankingLexicon#DEFAULT_LANGUAGE}.
     */
    public TurRankingLexicon lexicon(String language) {
        String key = language == null || language.isBlank()
                ? TurRankingLexicon.DEFAULT_LANGUAGE
                : language.toLowerCase(Locale.ROOT);
        return byLanguage.computeIfAbsent(key, this::load);
    }

    private TurRankingLexicon load(String language) {
        String resource = RESOURCE_PATTERN.formatted(language);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("[RankingLexicon] no bundle at '{}' — ranking is disabled for '{}'",
                        resource, language);
                return TurRankingLexicon.of(TurRankingLexiconBundle.empty(language));
            }
            TurRankingLexiconBundle bundle = MAPPER.readValue(in, TurRankingLexiconBundle.class);
            return TurRankingLexicon.of(bundle);
        } catch (Exception e) {
            log.warn("[RankingLexicon] bundle '{}' is unreadable: {} — ranking is disabled for '{}'",
                    resource, e.getMessage(), language);
            return TurRankingLexicon.of(TurRankingLexiconBundle.empty(language));
        }
    }
}
