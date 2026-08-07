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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner.RankingIntent;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T943 / §LVIII.8 (Block BJ) — answers what a site's ranking-lexicon bundle adds,
 * and what a sample question resolves to under it.
 *
 * <p>Ranking stays fail-open: an unparseable bundle is ignored at request time and
 * a typo never costs a site its sort. That is right, and it is also why it cannot
 * be the <em>only</em> feedback — the admin's evidence that a bundle took effect
 * would otherwise be that questions still rank the way they did before. This is
 * the read that closes the gap, and it reuses {@link TurNLRankingPlanner#detect}
 * rather than re-describing it, so the preview cannot drift from the behaviour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurRankingLexiconPreviewService {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final TurRankingLexiconProvider lexiconProvider;
    private final TurNLRankingPlanner rankingPlanner;

    public TurRankingLexiconPreviewService(TurRankingLexiconProvider lexiconProvider,
            TurNLRankingPlanner rankingPlanner) {
        this.lexiconProvider = lexiconProvider;
        this.rankingPlanner = rankingPlanner;
    }

    /**
     * Reads {@code bundleJson} as a lexicon override for {@code locale} and reports
     * what it adds over the resolved bundle. When {@code query} is present it is run
     * through the real planner against {@code schema}, so the answer is the sort the
     * site would actually get. Never throws.
     */
    public TurRankingLexiconPreview preview(String locale, String bundleJson, String query,
            List<TurNLFacetField> schema) {
        TurRankingLexicon base = lexiconProvider.lexicon(locale);
        if (!StringUtils.hasText(bundleJson)) {
            return describe(base, TurRankingLexiconBundle.empty(base.language()), query, schema,
                    bundleJson);
        }
        TurRankingLexiconBundle override;
        try {
            override = MAPPER.readValue(bundleJson, TurRankingLexiconBundle.class);
        } catch (Exception e) {
            return TurRankingLexiconPreview.invalid(base.language(), e.getMessage());
        }
        return describe(base, override, query, schema, bundleJson);
    }

    private TurRankingLexiconPreview describe(TurRankingLexicon base,
            TurRankingLexiconBundle override, String query, List<TurNLFacetField> schema,
            String bundleJson) {
        TurRankingLexicon merged = lexiconProvider.lexicon(base.language(), bundleJson);
        RankingIntent intent = StringUtils.hasText(query)
                ? rankingPlanner.detect(query, schema, base.language(), bundleJson)
                : RankingIntent.none(query);
        return new TurRankingLexiconPreview(true, null, merged.language(),
                added(override.ascWords(), base.ascWords()),
                added(override.descWords(), base.descWords()),
                added(override.sortTriggers(), base.sortTriggers()),
                addedSynonyms(override.synonyms()),
                intent.hasSort(), intent.field(), intent.order(), intent.strippedQuery());
    }

    /**
     * What the override contributed — the words the shipped vocabulary did not
     * already carry. Restating a shipped word is legal and is not an addition, and
     * saying so is how the admin learns the bundle extends rather than replaces.
     */
    private static List<String> added(List<String> declared, Set<String> alreadyThere) {
        List<String> additions = new ArrayList<>();
        for (String word : declared) {
            String normalized = word == null ? null : word.trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(normalized) && !alreadyThere.contains(normalized)
                    && !additions.contains(normalized)) {
                additions.add(normalized);
            }
        }
        return additions;
    }

    /** The synonym triggers the bundle declares, each with how many concepts it maps to. */
    private static List<String> addedSynonyms(Map<String, List<String>> synonyms) {
        List<String> triggers = new ArrayList<>();
        synonyms.forEach((trigger, concepts) -> {
            if (StringUtils.hasText(trigger) && concepts != null && !concepts.isEmpty()) {
                triggers.add("%s → %s".formatted(trigger.trim().toLowerCase(Locale.ROOT),
                        String.join(", ", concepts)));
            }
        });
        return triggers;
    }
}
