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

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.properties.TurCatalogCopilotProperty;
import com.viglet.turing.sn.dsl.eval.TurNLRankingPlanner.RankingIntent;

/**
 * Deterministic tests for the T811 ranking-intent planner (§LVII.1). Pure — no LLM,
 * no DB — so it is the always-on CI regression guard for the "superlative →
 * numeric sort" behaviour the LLM was unreliable at.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurNLRankingPlannerTest {

    private final TurNLRankingPlanner planner =
            new TurNLRankingPlanner(new TurRankingLexiconProvider(new TurCatalogCopilotProperty()));

    /** Shared with {@link TurRankingLexiconTest} — the same declared catalog schema. */
    static final List<TurNLFacetField> SCHEMA = List.of(
            new TurNLFacetField("kind", TurSEFieldType.STRING, true, "chat | embedding | image"),
            new TurNLFacetField("vendor", TurSEFieldType.STRING, true, "Provider name"),
            new TurNLFacetField("benchmarks_intelligenceIndex", TurSEFieldType.FLOAT, false,
                    "Overall intelligence index — the single headline metric"),
            new TurNLFacetField("pricing_inputPer1M", TurSEFieldType.DOUBLE, false,
                    "Input price per 1M tokens (USD)"),
            new TurNLFacetField("contextWindow", TurSEFieldType.INT, false,
                    "Maximum context window in tokens"));

    @Test
    void noSuperlativePassesThroughUnchanged() {
        RankingIntent intent = planner.detect("chat models from openai", SCHEMA);
        assertThat(intent.hasSort()).isFalse();
        assertThat(intent.strippedQuery()).isEqualTo("chat models from openai");
    }

    @Test
    void sortedByResolvesDescendingAndStripsTheRankingClause() {
        RankingIntent intent = planner.detect("chat models sorted by intelligence index", SCHEMA);
        assertThat(intent.hasSort()).isTrue();
        assertThat(intent.field()).isEqualTo("benchmarks_intelligenceIndex");
        assertThat(intent.order()).isEqualTo("desc");
        // The LLM must see only the faceting part so it keeps kind=CHAT.
        assertThat(intent.strippedQuery()).isEqualToIgnoringCase("chat models");
    }

    @Test
    void highestResolvesDescendingOnTheNamedMetric() {
        RankingIntent intent = planner.detect(
                "which chat model scores highest on the intelligence index?", SCHEMA);
        assertThat(intent.field()).isEqualTo("benchmarks_intelligenceIndex");
        assertThat(intent.order()).isEqualTo("desc");
        assertThat(intent.strippedQuery()).containsIgnoringCase("chat");
    }

    @Test
    void lowestPriceResolvesAscendingOnThePricingField() {
        RankingIntent intent = planner.detect(
                "which embedding model has the lowest price?", SCHEMA);
        assertThat(intent.field()).isEqualTo("pricing_inputPer1M");
        assertThat(intent.order()).isEqualTo("asc");
        assertThat(intent.strippedQuery()).containsIgnoringCase("embedding");
    }

    @Test
    void cheapestBridgesTheAdjectiveToThePriceFieldViaSynonym() {
        // "cheapest" carries no "price" token — the synonym seed must bridge it.
        RankingIntent intent = planner.detect("cheapest embedding models", SCHEMA);
        assertThat(intent.field()).isEqualTo("pricing_inputPer1M");
        assertThat(intent.order()).isEqualTo("asc");
        assertThat(intent.strippedQuery()).isEqualToIgnoringCase("embedding models");
    }

    @Test
    void mostIntelligentResolvesTheIntelligenceIndexNotAnotherNumericField() {
        RankingIntent intent = planner.detect("the most intelligent model", SCHEMA);
        assertThat(intent.field()).isEqualTo("benchmarks_intelligenceIndex");
        assertThat(intent.order()).isEqualTo("desc");
    }

    @Test
    void biggestContextWindowResolvesTheContextField() {
        RankingIntent intent = planner.detect("model with the biggest context window", SCHEMA);
        assertThat(intent.field()).isEqualTo("contextWindow");
        assertThat(intent.order()).isEqualTo("desc");
    }

    @Test
    void superlativeWithNoResolvableMetricYieldsNoSort() {
        // "best model" is a superlative but no field token matches — never guess a field.
        RankingIntent intent = planner.detect("best model", SCHEMA);
        assertThat(intent.hasSort()).isFalse();
    }

    @Test
    void superlativeNeverResolvesToANonNumericFacetField() {
        // No numeric field mentioned; the STRING facets must not be picked as a sort.
        RankingIntent intent = planner.detect("cheapest vendor", SCHEMA);
        assertThat(intent.field()).isNotEqualTo("vendor");
    }

    @Test
    void nullOrEmptyQueryIsNoSort() {
        assertThat(planner.detect(null, SCHEMA).hasSort()).isFalse();
        assertThat(planner.detect("  ", SCHEMA).hasSort()).isFalse();
    }

    // ─────────────── T816 / §LVIII.3 — the same questions, other languages ───────────────

    @Test
    void portugueseResolvesTheSameSortsAsEnglish() {
        // The three §LVIII questions that used to get no sort at all in pt_BR.
        RankingIntent cheapest = planner.detect("modelo mais barato", SCHEMA, "pt_BR");
        assertThat(cheapest.field()).isEqualTo("pricing_inputPer1M");
        assertThat(cheapest.order()).isEqualTo("asc");

        RankingIntent biggest = planner.detect("maior janela de contexto", SCHEMA, "pt_BR");
        assertThat(biggest.field()).isEqualTo("contextWindow");
        assertThat(biggest.order()).isEqualTo("desc");

        RankingIntent sorted = planner.detect("modelos de chat ordenados por preço", SCHEMA, "pt");
        assertThat(sorted.field()).isEqualTo("pricing_inputPer1M");
        assertThat(sorted.order()).isEqualTo("desc");
        // The facet must survive the strip here exactly as it does in English.
        assertThat(sorted.strippedQuery()).containsIgnoringCase("chat");
    }

    @Test
    void spanishResolvesItsOwnSuperlatives() {
        RankingIntent cheapest = planner.detect("el modelo más barato", SCHEMA, "es");
        assertThat(cheapest.field()).isEqualTo("pricing_inputPer1M");
        assertThat(cheapest.order()).isEqualTo("asc");

        RankingIntent smartest = planner.detect("el modelo más inteligente", SCHEMA, "es");
        assertThat(smartest.field()).isEqualTo("benchmarks_intelligenceIndex");
        assertThat(smartest.order()).isEqualTo("desc");
    }

    // ─────────────── T817 / §LVIII.4 — a site's own vocabulary ───────────────

    @Test
    void aSiteLexiconResolvesAWordTheShippedBundleNeverHeardOf() {
        // "gutsiest" is nobody's superlative — until this catalog says it means the
        // intelligence index, descending.
        String siteLexicon = """
                {"descWords":["gutsiest"],"synonyms":{"gutsiest":["intelligence"]}}""";

        assertThat(planner.detect("the gutsiest model", SCHEMA, "en").hasSort()).isFalse();

        RankingIntent intent = planner.detect("the gutsiest model", SCHEMA, "en", siteLexicon);
        assertThat(intent.field()).isEqualTo("benchmarks_intelligenceIndex");
        assertThat(intent.order()).isEqualTo("desc");
    }

    @Test
    void aSiteLexiconDoesNotCostTheShippedVocabulary() {
        RankingIntent intent = planner.detect("cheapest embedding models", SCHEMA, "en", """
                {"descWords":["gutsiest"]}""");

        assertThat(intent.field()).isEqualTo("pricing_inputPer1M");
        assertThat(intent.order()).isEqualTo("asc");
    }

    @Test
    void aLanguageWithNoBundleStillRanksInEnglish() {
        // The fallback is the pre-block behaviour: an English question in a de_DE
        // request must not lose its sort because German ships no lexicon.
        RankingIntent intent = planner.detect("cheapest embedding models", SCHEMA, "de_DE");
        assertThat(intent.field()).isEqualTo("pricing_inputPer1M");
        assertThat(intent.order()).isEqualTo("asc");
    }
}
