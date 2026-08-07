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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * T814 / §LVIII.1 (Block BJ) — the lexicon is now data, so these are the guards the
 * hardcoded sets never needed: the shipped {@code en} bundle really is on the
 * classpath and carries the T811 vocabulary, a bundle that declares nothing is
 * legal, and an absent one degrades to a lexicon that resolves no ranking rather
 * than to an exception.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurRankingLexiconTest {

    private final TurRankingLexiconProvider provider = new TurRankingLexiconProvider();

    @Test
    void theShippedEnglishBundleCarriesTheT811Vocabulary() {
        TurRankingLexicon lexicon = provider.lexicon();

        assertThat(lexicon.language()).isEqualTo("en");
        assertThat(lexicon.ascWords()).contains("cheapest", "lowest", "fastest");
        assertThat(lexicon.descWords()).contains("highest", "biggest", "most");
        assertThat(lexicon.stopwords()).contains("the", "which", "models");
        assertThat(lexicon.synonymsOf("cheapest")).contains("price", "pricing", "cost");
        assertThat(lexicon.synonymsOf("nothing-bridges-this")).isEmpty();
    }

    @Test
    void theSortTriggerPatternMatchesEverySortByPhrasing() {
        TurRankingLexicon lexicon = provider.lexicon();

        assertThat(lexicon.sortTriggerPattern()).isNotNull();
        assertThat(lexicon.sortTriggerPattern().matcher("chat models sorted by price").find())
                .isTrue();
        assertThat(lexicon.sortTriggerPattern().matcher("rank by intelligence").find()).isTrue();
        // Whitespace inside the phrase is a run, not a single space.
        assertThat(lexicon.sortTriggerPattern().matcher("order   by latency").find()).isTrue();
        // A trigger verb without its preposition is not a trigger.
        assertThat(lexicon.sortTriggerPattern().matcher("sorted results").find()).isFalse();
    }

    @Test
    void theSameLexiconInstanceIsServedOnEveryCall() {
        assertThat(provider.lexicon()).isSameAs(provider.lexicon("EN"));
    }

    @Test
    void anAbsentBundleDegradesToALexiconThatResolvesNoRanking() {
        TurRankingLexicon lexicon = provider.lexicon("zz");

        assertThat(lexicon.language()).isEqualTo("zz");
        assertThat(lexicon.ascWords()).isEmpty();
        assertThat(lexicon.descWords()).isEmpty();
        assertThat(lexicon.sortTriggerPattern()).isNull();
    }

    @Test
    void aBundleDeclaringOnlySynonymsIsLegal() {
        TurRankingLexicon lexicon = TurRankingLexicon.of(new TurRankingLexiconBundle(
                null, null, null, null, null, Map.of("Barato", List.of("Preco"))));

        assertThat(lexicon.language()).isEqualTo(TurRankingLexicon.DEFAULT_LANGUAGE);
        assertThat(lexicon.synonymsOf("barato")).containsExactly("preco");
        assertThat(lexicon.sortTriggerPattern()).isNull();
    }

    @Test
    void aPlannerOverAnEmptyLexiconResolvesNoSortAndKeepsTheQuery() {
        TurNLRankingPlanner planner = new TurNLRankingPlanner(new TurRankingLexiconProvider() {
            @Override
            public TurRankingLexicon lexicon() {
                return TurRankingLexicon.of(TurRankingLexiconBundle.empty("zz"));
            }
        });

        TurNLRankingPlanner.RankingIntent intent = planner.detect("cheapest embedding models",
                TurNLRankingPlannerTest.SCHEMA);

        assertThat(intent.hasSort()).isFalse();
        assertThat(intent.strippedQuery()).isEqualTo("cheapest embedding models");
    }
}
