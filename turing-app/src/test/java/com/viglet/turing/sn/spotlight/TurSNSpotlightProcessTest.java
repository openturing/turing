/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.spotlight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.solr.TurSolr;

/**
 * Unit tests for TurSNSpotlightProcess.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSpotlightProcessTest {

        @Mock
        private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;

        @Mock
        private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;

        @Mock
        private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;

        @Mock
        private TurSolr turSolr;

        @Mock
        private TurSpotlightCache turSpotlightCache;

        @Mock
        private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

        @Test
        void testIsSpotlightJobReturnsTrueForSpotlightType() {
                TurSNJobItem jobItem = mock(TurSNJobItem.class);
                when(jobItem.getAttributes()).thenReturn(Map.of(TurSNFieldName.TYPE, "TUR_SPOTLIGHT"));

                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                assertThat(process.isSpotlightJob(jobItem)).isTrue();
        }

        @Test
        void testIsSpotlightJobReturnsFalseForNullAndMissingAttributes() {
                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                assertThat(process.isSpotlightJob(null)).isFalse();

                TurSNJobItem jobItem = mock(TurSNJobItem.class);
                when(jobItem.getAttributes()).thenReturn(Map.of("other", "value"));
                assertThat(process.isSpotlightJob(jobItem)).isFalse();
        }

        @Test
        void testDeleteUnmanagedSpotlightRemovesEntriesById() {
                TurSNJobItem jobItem = mock(TurSNJobItem.class);
                when(jobItem.getAttributes()).thenReturn(Map.of(TurSNFieldName.ID, "spotlight-id"));
                when(jobItem.getLocale()).thenReturn(Locale.US);

                TurSNSite site = new TurSNSite();
                TurSNSiteLocale locale = new TurSNSiteLocale();
                locale.setLanguage(Locale.US);
                when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.US))
                                .thenReturn(locale);

                Set<TurSNSiteSpotlight> spotlights = Set.of(new TurSNSiteSpotlight());
                when(turSNSiteSpotlightRepository.findByUnmanagedIdAndTurSNSiteAndLanguage(
                                "spotlight-id", site, Locale.US))
                                .thenReturn(spotlights);

                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                assertThat(process.deleteUnmanagedSpotlight(jobItem, site)).isTrue();
                verify(turSNSiteSpotlightRepository).deleteAllInBatch(spotlights);
        }

        @Test
        void testDeleteUnmanagedSpotlightRemovesByProvider() {
                TurSNJobItem jobItem = mock(TurSNJobItem.class);
                when(jobItem.getAttributes()).thenReturn(Map.of(TurSNFieldName.SOURCE_APPS, "cms-provider"));

                Set<TurSNSiteSpotlight> spotlights = Set.of(new TurSNSiteSpotlight());
                when(turSNSiteSpotlightRepository.findByProvider("cms-provider")).thenReturn(spotlights);

                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                assertThat(process.deleteUnmanagedSpotlight(jobItem, new TurSNSite())).isTrue();
                verify(turSNSiteSpotlightRepository).deleteAllInBatch(spotlights);
        }

        @Test
        void testCreateUnmanagedSpotlightReturnsFalseWhenLocaleMissing() {
                TurSNJobItem jobItem = mock(TurSNJobItem.class);
                Map<String, Object> attrs = new HashMap<>();
                attrs.put(TurSNFieldName.ID, "sp-id");
                attrs.put(TurSNFieldName.MODIFICATION_DATE, LocalDateTime.now().toString());
                when(jobItem.getAttributes()).thenReturn(attrs);
                when(jobItem.getLocale()).thenReturn(Locale.US);

                TurSNSite site = new TurSNSite();
                site.setName("demo-site");

                when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.US)).thenReturn(null);

                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                assertThat(process.createUnmanagedSpotlight(jobItem, site)).isFalse();
        }

        @Test
        void testCreateUnmanagedSpotlightCreatesTermsAndDocuments() {
                TurSNJobItem jobItem = mock(TurSNJobItem.class);
                Map<String, Object> attrs = new HashMap<>();
                attrs.put(TurSNFieldName.ID, "sp-id");
                attrs.put("name", "Spotlight Demo");
                attrs.put(TurSNFieldName.SOURCE_APPS, "cms-provider");
                attrs.put(TurSNFieldName.MODIFICATION_DATE, "2025-01-01T10:15:30");
                attrs.put("terms", "java, spring");
                attrs.put("content",
                                "[{\"position\":1,\"title\":\"Title 1\",\"content\":\"C1\",\"link\":\"/l1\"},"
                                                + "{\"position\":2,\"title\":\"Title 2\",\"content\":\"C2\",\"link\":\"/l2\"}]");
                when(jobItem.getAttributes()).thenReturn(attrs);
                when(jobItem.getLocale()).thenReturn(Locale.US);

                TurSNSite site = new TurSNSite();
                site.setName("demo-site");

                TurSNSiteLocale locale = new TurSNSiteLocale();
                locale.setLanguage(Locale.US);
                when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.US)).thenReturn(locale);

                TurSNSiteSpotlight existing1 = new TurSNSiteSpotlight();
                TurSNSiteSpotlight existing2 = new TurSNSiteSpotlight();
                Set<TurSNSiteSpotlight> existing = Set.of(existing1, existing2);

                when(turSNSiteSpotlightRepository.findByUnmanagedIdAndTurSNSiteAndLanguage("sp-id", site, Locale.US))
                                .thenReturn(existing);
                when(turSNSiteSpotlightTermRepository.findByTurSNSiteSpotlight(any())).thenReturn(Set.of());
                when(turSNSiteSpotlightDocumentRepository.findByTurSNSiteSpotlight(any())).thenReturn(Set.of());

                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                assertThat(process.createUnmanagedSpotlight(jobItem, site)).isTrue();
                verify(turSNSiteSpotlightRepository).deleteAllInBatch(existing);
                verify(turSNSiteSpotlightRepository).save(any(TurSNSiteSpotlight.class));
                verify(turSNSiteSpotlightTermRepository, times(2)).save(any(TurSNSiteSpotlightTerm.class));
                verify(turSNSiteSpotlightDocumentRepository, times(2)).save(any(TurSNSiteSpotlightDocument.class));
        }

        @Test
        void testGetSpotlightsFromQueryBuildsMapAndMergesSamePosition() {
                TurSNSite spotlightSite = new TurSNSite();
                spotlightSite.setName("demo-site");

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("java spring guide");
                searchParams.setLocale(Locale.US);
                searchParams.setP(1);
                searchParams.setRows(10);
                TurSNSiteSearchContext context = new TurSNSiteSearchContext("demo-site", new TurSNConfig(),
                                new TurSEParameters(searchParams), Locale.US,
                                URI.create("http://localhost/search?q=java"));

                TurSNSiteSpotlight spotlight = new TurSNSiteSpotlight();
                TurSNSpotlightTermCacheBean term1 = new TurSNSpotlightTermCacheBean("java", spotlight);
                TurSNSpotlightTermCacheBean term2 = new TurSNSpotlightTermCacheBean("spring", spotlight);
                when(turSpotlightCache.findTermsBySNSiteAndLanguage("demo-site", Locale.US))
                                .thenReturn(List.of(term1, term2));

                TurSNSiteSpotlightDocument doc1 = new TurSNSiteSpotlightDocument();
                doc1.setPosition(1);
                TurSNSiteSpotlightDocument doc2 = new TurSNSiteSpotlightDocument();
                doc2.setPosition(1);
                when(turSNSiteSpotlightDocumentRepository.findByTurSNSiteSpotlight(spotlight))
                                .thenReturn(Set.of(doc1, doc2));

                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                Map<Integer, List<TurSNSiteSpotlightDocument>> result = process.getSpotlightsFromQuery(context,
                                spotlightSite);

                assertThat(result).containsKey(1);
                assertThat(result.get(1)).hasSize(2);
        }

        @Test
        void testAddSpotlightToResultsWithoutSpotlightsKeepsDocuments() {
                TurSNSite site = new TurSNSite();
                site.setName("demo-site");
                site.setDefaultDescriptionField("description");
                site.setDefaultURLField("url");
                site.setDefaultTitleField("title");

                TurSNSearchParams searchParams = new TurSNSearchParams();
                searchParams.setQ("java");
                searchParams.setP(1);
                searchParams.setRows(10);
                searchParams.setLocale(Locale.US);

                TurSNSiteSearchContext context = new TurSNSiteSearchContext("demo-site", new TurSNConfig(),
                                new TurSEParameters(searchParams), Locale.US,
                                URI.create("http://localhost/search?q=java"));

                when(turSpotlightCache.findTermsBySNSiteAndLanguage("demo-site", Locale.US)).thenReturn(List.of());

                List<TurSNSiteSearchDocumentBean> docs = new ArrayList<>();
                docs.add(TurSNSiteSearchDocumentBean.builder().fields(Map.of("title", "Doc")).build());

                TurSNSpotlightProcess process = new TurSNSpotlightProcess(turSNSiteSpotlightRepository,
                                turSNSiteSpotlightTermRepository, turSNSiteSpotlightDocumentRepository, turSolr,
                                turSpotlightCache, turSNSiteLocaleRepository);

                process.addSpotlightToResults(context, null, site, Map.of(), Map.of(), docs);

                assertThat(docs).hasSize(1);
        }

        // ─────────────────────────── T21 / §III.4 V2 ───────────────────────────
        // MemoryIndex + QueryParser match contract via PortugueseAnalyzer /
        // EnglishAnalyzer. V2 subsumes V1 (the plain-term cases below mirror
        // the original §III.4 V1 regressions) AND adds phrase / fuzzy /
        // boolean syntax. Tests pin both layers so a V1-equivalent regression
        // and a V2-syntax regression both surface here.

        // ── V1-equivalent cases (must keep working post-V2) ──

        @Test
        void v2_ptPlural_matchesSingular_bothDirections() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                assertThat(matches(pt, "tenho dois carros", "carro")).isTrue();
                // Inverse direction: query is singular, spotlight is plural.
                assertThat(matches(pt, "carro novo", "carros")).isTrue();
        }

        @Test
        void v2_ptFillerWords_doNotBlockMatch() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                // The headline §III.4 case: "planos para carreira" ↔ "plano de
                // carreira". Stopwords drop, stems align.
                assertThat(matches(pt, "quero discutir planos para carreira", "plano de carreira")).isTrue();
        }

        @Test
        void v2_noOverlap_returnsFalse() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                assertThat(matches(pt, "preços de hospedagem", "plano de carreira")).isFalse();
        }

        @Test
        void v2_termFullyStopwords_returnsFalse() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                // QueryParser over "de para com" with the PT analyzer drops every
                // token as a stopword → no clauses → empty BooleanQuery → 0 score.
                assertThat(matches(pt, "qualquer coisa", "de para com")).isFalse();
        }

        @Test
        void v2_englishStemming_pluralAndVerbConjugation() {
                org.apache.lucene.analysis.Analyzer en = new org.apache.lucene.analysis.en.EnglishAnalyzer();
                assertThat(matches(en, "I need running shoes", "run shoe")).isTrue();
                assertThat(matches(en, "shoe", "shoes")).isTrue();
        }

        @Test
        void v2_partialOverlap_returnsFalseWithAndDefault() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                // Spotlight "plano de carreira" → required stems {plan, carreir}.
                // Query "tenho um plano" yields only {plan} — AND default fails.
                // This pins the AND default: switching to OR would make this
                // case false-positive and degrade spotlight precision.
                assertThat(matches(pt, "tenho um plano", "plano de carreira")).isFalse();
        }

        // ── V2-only capabilities ──

        @Test
        void v2_phraseQuery_requiresWordOrder() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                // Phrase ties words to a strict order (no slop). "carreira de
                // plano" is the reverse of the phrase — must NOT match the
                // exact-order phrase. Plain-term V1 would have matched.
                assertThat(matches(pt, "carreira de plano", "\"plano carreira\"")).isFalse();
                // Right order still matches.
                assertThat(matches(pt, "meu plano carreira", "\"plano carreira\"")).isTrue();
        }

        @Test
        void v2_phraseWithSlop_allowsInterveningStopwords() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                // Stopwords get dropped during indexing too, so "plano de carreira"
                // indexes positions {plan@0, carreir@2} (the "de" position is
                // skipped). A slop-2 phrase "plano carreira"~2 catches that gap.
                assertThat(matches(pt, "quero discutir plano de carreira hoje", "\"plano carreira\"~2")).isTrue();
        }

        @Test
        void v2_fuzzyQuery_matchesTypos() {
                // QueryParser does NOT run the stemmer over the FuzzyQuery
                // operand (multi-term queries skip token filters that aren't
                // multi-term-aware). So the spotlight fuzzy term has to be
                // close to the INDEXED stem, not the raw user word.
                //
                // PortugueseAnalyzer stems "carros"/"carro" → "carr"; we use
                // EnglishAnalyzer here because it doesn't stem these (so the
                // fuzzy comparison happens on the raw words and the test
                // intuition holds 1:1 with what an admin would write).
                org.apache.lucene.analysis.Analyzer en = new org.apache.lucene.analysis.en.EnglishAnalyzer();
                // 1-edit typo (insert): "carrera" vs indexed "carrera" (same).
                // For genuine 1-edit, query the indexed form with a fuzzy
                // search around it. "carerra~1" reorders one letter pair
                // and still matches "carrera" (distance 1 via transposition).
                assertThat(matches(en, "where is carrera street?", "carerra~1")).isTrue();
                // 3-edit typo doesn't match fuzzy~1: "carrosselagem" needs
                // more than one edit to reach "carro" so the match must fail.
                assertThat(matches(en, "carrosselagem industry", "carro~1")).isFalse();
        }

        @Test
        void v2_booleanOr_explicitlySupported() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                // V1 would force AND of all stems. V2 with explicit OR lets
                // admins write a synonym list inline.
                assertThat(matches(pt, "preciso de um veículo", "carro OR veiculo OR automovel")).isTrue();
                assertThat(matches(pt, "preciso de um automovel", "carro OR veiculo OR automovel")).isTrue();
                assertThat(matches(pt, "preciso de uma bicicleta", "carro OR veiculo OR automovel")).isFalse();
        }

        @Test
        void v2_malformedTerm_fallsBackToEscapedLiteral() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                // Bare "~" with no number after triggers ParseException on the
                // raw parse path. The fallback escapes ALL special chars and
                // parses again — at which point "carro~" becomes a literal
                // term containing just "carro" (after stemming). It should
                // still match a query that contains "carro".
                assertThat(matches(pt, "tenho um carro novo", "carro~")).isTrue();
                // Truly nonsense input (only special chars) still gracefully
                // returns false instead of crashing.
                assertThat(matches(pt, "qualquer coisa", "((((")).isFalse();
        }

        @Test
        void v2_blankOrNullTerm_returnsFalse() {
                org.apache.lucene.analysis.Analyzer pt = new org.apache.lucene.analysis.pt.PortugueseAnalyzer();
                assertThat(matches(pt, "anything", null)).isFalse();
                assertThat(matches(pt, "anything", "")).isFalse();
                assertThat(matches(pt, "anything", "   ")).isFalse();
        }

        /**
         * T21 test helper — builds the MemoryIndex over {@code userQuery} once
         * and runs {@code TurSNSpotlightProcess#matchesByLuceneQuery} against
         * the supplied {@code spotlightTerm}. One-line per assertion.
         */
        private static boolean matches(org.apache.lucene.analysis.Analyzer analyzer,
                        String userQuery, String spotlightTerm) {
                org.apache.lucene.index.memory.MemoryIndex idx = new org.apache.lucene.index.memory.MemoryIndex();
                if (userQuery != null && !userQuery.isBlank()) {
                        idx.addField(TurSNSpotlightProcess.LUCENE_QUERY_FIELD, userQuery, analyzer);
                }
                return TurSNSpotlightProcess.matchesByLuceneQuery(idx, spotlightTerm, analyzer);
        }
}
