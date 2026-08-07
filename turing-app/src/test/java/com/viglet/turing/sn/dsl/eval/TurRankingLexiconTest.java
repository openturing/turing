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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.turing.properties.TurCatalogCopilotProperty;

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

    private final TurCatalogCopilotProperty copilotProperty = new TurCatalogCopilotProperty();
    private final TurRankingLexiconProvider provider =
            new TurRankingLexiconProvider(copilotProperty);

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

    // ─────────────── T816 / §LVIII.3 — per-language resolution ───────────────

    @Test
    void aLocaleResolvesToItsLanguagesBundle() {
        assertThat(provider.lexicon("pt_BR").language()).isEqualTo("pt");
        assertThat(provider.lexicon("pt-BR").language()).isEqualTo("pt");
        assertThat(provider.lexicon("ES").language()).isEqualTo("es");
    }

    @Test
    void aLanguageNothingDeclaresFallsBackToEnglish() {
        // The pre-block behaviour for every locale, now the explicit fallback.
        assertThat(provider.lexicon("zz")).isSameAs(provider.lexicon());
        assertThat(provider.lexicon(null)).isSameAs(provider.lexicon());
    }

    @Test
    void portugueseResolvesADirectionEnglishNeverCould() {
        TurRankingLexicon lexicon = provider.lexicon("pt_BR");

        // "mais" is a descending word, but "mais barato" is ascending — the adjective's
        // polarity decides, which is why asc is tested before desc.
        assertThat(lexicon.tokens("modelo mais barato"))
                .containsAnyElementsOf(lexicon.ascStems());
        assertThat(lexicon.tokens("maior janela de contexto"))
                .containsAnyElementsOf(lexicon.descStems());
        // …and the Portuguese synonym bridges to the English field name the catalog uses.
        assertThat(lexicon.tokens("barato")).containsAnyElementsOf(lexicon.tokens("pricing"));
        assertThat(lexicon.tokens("janela")).containsAnyElementsOf(lexicon.tokens("contextWindow"));
    }

    @Test
    void spanishBridgesItsOwnVocabularyToTheSameFields() {
        TurRankingLexicon lexicon = provider.lexicon("es");

        assertThat(lexicon.tokens("el modelo más barato"))
                .containsAnyElementsOf(lexicon.ascStems());
        assertThat(lexicon.tokens("barato")).containsAnyElementsOf(lexicon.tokens("pricing"));
        assertThat(lexicon.sortTriggerPattern().matcher("modelos ordenados por precio").find())
                .isTrue();
    }

    @Test
    void anExternalBundleIsMergedOverTheShippedOne(@TempDir Path lexiconDir) throws IOException {
        Files.writeString(lexiconDir.resolve("lexicon-en.json"), """
                {"ascWords":["thriftiest"],"synonyms":{"thriftiest":["pricing"]}}""");
        copilotProperty.getRanking().setLexiconDir(lexiconDir.toString());

        TurRankingLexicon lexicon = new TurRankingLexiconProvider(copilotProperty).lexicon();

        // The deployment's word is added…
        assertThat(lexicon.ascWords()).contains("thriftiest");
        assertThat(lexicon.tokens("thriftiest")).containsAnyElementsOf(lexicon.tokens("pricing"));
        // …without the shipped vocabulary having to be restated to keep it.
        assertThat(lexicon.ascWords()).contains("cheapest");
        assertThat(lexicon.tokens("cheapest")).containsAnyElementsOf(lexicon.tokens("pricing"));
    }

    @Test
    void anExternalBundleCanAddAWholeLanguage(@TempDir Path lexiconDir) throws IOException {
        Files.writeString(lexiconDir.resolve("lexicon-fr.json"), """
                {"language":"fr","stemmer":"French","descWords":["plus"],\
                "synonyms":{"intelligent":["intelligence"]}}""");
        copilotProperty.getRanking().setLexiconDir(lexiconDir.toString());

        TurRankingLexicon lexicon = new TurRankingLexiconProvider(copilotProperty).lexicon("fr_FR");

        assertThat(lexicon.language()).isEqualTo("fr");
        assertThat(lexicon.tokens("le plus intelligent")).containsAnyElementsOf(lexicon.descStems());
    }

    @Test
    void anUnreadableExternalBundleLeavesTheShippedOneStanding(@TempDir Path lexiconDir)
            throws IOException {
        Files.writeString(lexiconDir.resolve("lexicon-en.json"), "{ this is not json");
        copilotProperty.getRanking().setLexiconDir(lexiconDir.toString());

        TurRankingLexicon lexicon = new TurRankingLexiconProvider(copilotProperty).lexicon();

        assertThat(lexicon.ascWords()).contains("cheapest");
    }

    @Test
    void aDeploymentWithNoBundleAtAllResolvesNoRanking() {
        TurRankingLexicon lexicon = TurRankingLexicon.of(TurRankingLexiconBundle.empty("zz"));

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
        TurNLRankingPlanner planner = new TurNLRankingPlanner(
                new TurRankingLexiconProvider(copilotProperty) {
                    @Override
                    public TurRankingLexicon lexicon(String locale) {
                        return TurRankingLexicon.of(TurRankingLexiconBundle.empty("zz"));
                    }
                });

        TurNLRankingPlanner.RankingIntent intent = planner.detect("cheapest embedding models",
                TurNLRankingPlannerTest.SCHEMA);

        assertThat(intent.hasSort()).isFalse();
        assertThat(intent.strippedQuery()).isEqualTo("cheapest embedding models");
    }
}
