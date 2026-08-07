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
 * T814 / §LVIII.1 · T815 / §LVIII.2 (Block BJ) — the lexicon is now data run
 * through a Lucene analyzer, so these are the guards the hardcoded sets never
 * needed: the shipped {@code en} bundle is on the classpath, inflections collapse
 * to one stem, a ranking word the language calls a stopword survives its own
 * stopword list, and an absent bundle degrades to a lexicon that resolves no
 * ranking rather than to an exception.
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
        // "affordable" stems to "afford", which is why the stems are kept separately.
        assertThat(lexicon.ascStems()).contains("afford");
        assertThat(lexicon.descStems()).contains("most", "biggest");
    }

    @Test
    void inflectionsOfOneWordCollapseToOneStem() {
        TurRankingLexicon lexicon = provider.lexicon();

        // The whole point of the analyzer: the field says "pricing", the query "price".
        assertThat(lexicon.tokens("pricing")).containsExactly("price");
        // "intelligent" and "intelligence" are one stem, so an adjective reaches a
        // field named after the noun without either being enumerated.
        assertThat(lexicon.tokens("intelligent"))
                .containsAnyElementsOf(lexicon.tokens("intelligence"));
    }

    @Test
    void theSynonymFilterBridgesAnAdjectiveToItsConcept() {
        TurRankingLexicon lexicon = provider.lexicon();

        // "cheapest" carries no price token of its own — the synonym filter adds it,
        // and the original survives because the direction still has to be recognised.
        assertThat(lexicon.tokens("cheapest"))
                .containsAll(lexicon.tokens("pricing"))
                .contains("cheapest");
    }

    @Test
    void aRankingWordTheLanguageCallsAStopwordSurvives() {
        TurRankingLexicon lexicon = provider.lexicon();

        // Snowball's English stop list holds "most" — losing it would cost every
        // "the most intelligent model" its direction.
        assertThat(lexicon.tokens("the most intelligent model")).contains("most");
        // A stopword that is not ranking vocabulary is still dropped.
        assertThat(lexicon.tokens("the most intelligent model")).doesNotContain("the", "model");
    }

    @Test
    void camelCaseAndUnderscoresAreSplitBeforeAnalysis() {
        assertThat(provider.lexicon().tokens("benchmarks_intelligenceIndex"))
                .contains("index")
                .containsAnyElementsOf(provider.lexicon().tokens("intelligence"));
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
                null, null, null, null, null, null, Map.of("barato", List.of("preco"))));

        assertThat(lexicon.language()).isEqualTo(TurRankingLexicon.DEFAULT_LANGUAGE);
        assertThat(lexicon.tokens("barato")).contains("preco");
        assertThat(lexicon.sortTriggerPattern()).isNull();
    }

    @Test
    void anUnknownStemmerNameIsNotFatal() {
        TurRankingLexicon lexicon = TurRankingLexicon.of(new TurRankingLexiconBundle(
                "zz", "NoSuchLanguage", List.of("menor"), List.of(), List.of(), List.of(),
                Map.of()));

        assertThat(lexicon.ascStems()).containsExactly("menor");
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
