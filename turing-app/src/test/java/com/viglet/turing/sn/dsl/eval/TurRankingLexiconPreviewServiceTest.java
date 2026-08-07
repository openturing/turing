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

import org.junit.jupiter.api.Test;

import com.viglet.turing.properties.TurCatalogCopilotProperty;

/**
 * T943 / §LVIII.8 (Block BJ) — the preview exists because ranking is fail-open:
 * a bundle that does not parse is ignored at request time, so without this the
 * admin's only evidence is that nothing changed. These pin the three answers the
 * form has to be able to show — it did not parse, it parsed but added nothing new,
 * and it parsed and this is the sort your question now gets.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurRankingLexiconPreviewServiceTest {

    private static final List<TurNLFacetField> SCHEMA = TurNLRankingPlannerTest.SCHEMA;

    private final TurRankingLexiconProvider provider =
            new TurRankingLexiconProvider(new TurCatalogCopilotProperty());
    private final TurRankingLexiconPreviewService service = new TurRankingLexiconPreviewService(
            provider, new TurNLRankingPlanner(provider));

    @Test
    void anUnparseableBundleSaysSoInsteadOfPassingSilently() {
        TurRankingLexiconPreview preview = service.preview("en", "{ not json", "cheapest models",
                SCHEMA);

        assertThat(preview.valid()).isFalse();
        assertThat(preview.error()).isNotBlank();
        assertThat(preview.sortResolved()).isFalse();
    }

    @Test
    void reportsWhatTheBundleAddsOverTheShippedVocabulary() {
        TurRankingLexiconPreview preview = service.preview("en", """
                {"descWords":["gutsiest","most"],"synonyms":{"gutsiest":["intelligence"]}}""",
                null, SCHEMA);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.language()).isEqualTo("en");
        // "most" is already shipped, so restating it is legal and is not an addition —
        // which is how the admin learns the bundle extends rather than replaces.
        assertThat(preview.addedDescWords()).containsExactly("gutsiest");
        assertThat(preview.addedSynonyms()).containsExactly("gutsiest → intelligence");
    }

    @Test
    void aSampleQuestionAnswersWithTheSortTheSiteWouldActuallyGet() {
        TurRankingLexiconPreview preview = service.preview("en", """
                {"descWords":["gutsiest"],"synonyms":{"gutsiest":["intelligence"]}}""",
                "the gutsiest chat model", SCHEMA);

        assertThat(preview.sortResolved()).isTrue();
        assertThat(preview.field()).isEqualTo("benchmarks_intelligenceIndex");
        assertThat(preview.order()).isEqualTo("desc");
        assertThat(preview.strippedQuery()).containsIgnoringCase("chat");
    }

    @Test
    void aBundleThatParsesButResolvesNothingIsTheCaseAValidatorWouldCallFine() {
        // Valid JSON, a real added word, and still no sort: the word bridges to no
        // declared field. This is the answer the admin could not get any other way.
        TurRankingLexiconPreview preview = service.preview("en", """
                {"descWords":["gutsiest"]}""", "the gutsiest chat model", SCHEMA);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.addedDescWords()).containsExactly("gutsiest");
        assertThat(preview.sortResolved()).isFalse();
    }

    @Test
    void aBlankBundleIsAnsweredAgainstTheShippedVocabularyAlone() {
        TurRankingLexiconPreview preview = service.preview("en", "  ",
                "cheapest embedding models", SCHEMA);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.addedAscWords()).isEmpty();
        assertThat(preview.sortResolved()).isTrue();
        assertThat(preview.field()).isEqualTo("pricing_inputPer1M");
    }

    @Test
    void thePreviewFollowsTheLocaleTheQuestionWouldArriveIn() {
        TurRankingLexiconPreview preview = service.preview("pt_BR", """
                {"ascWords":["econômico"],"synonyms":{"econômico":["preço"]}}""",
                "modelo mais econômico", SCHEMA);

        assertThat(preview.language()).isEqualTo("pt");
        assertThat(preview.sortResolved()).isTrue();
        assertThat(preview.field()).isEqualTo("pricing_inputPer1M");
        assertThat(preview.order()).isEqualTo("asc");
    }

    @Test
    void aSiteWithNoDeclaredSchemaStillGetsTheAddedWords() {
        TurRankingLexiconPreview preview = service.preview("en", """
                {"descWords":["gutsiest"]}""", "the gutsiest model", List.of());

        assertThat(preview.valid()).isTrue();
        assertThat(preview.addedDescWords()).containsExactly("gutsiest");
        assertThat(preview.sortResolved()).isFalse();
    }
}
