/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

import java.lang.invoke.MethodHandles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import com.viglet.turing.api.sn.queue.TurSpotlightContent;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrUtils;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
public class TurSNSpotlightProcess {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final String NAME_ATTRIBUTE = "name";
	private static final String CONTENT_ATTRIBUTE = "content";
	private static final String TERMS_ATTRIBUTE = "terms";
	private static final String DOCUMENT_TYPE = "Page";
	private static final String TYPE_VALUE = "TUR_SPOTLIGHT";

	/**
	 * T20 / §III.4 V1 — per-language Lucene analyzers used to tokenize both
	 * the user's query and each configured spotlight term before comparing
	 * for match. Replaces the legacy {@code userQuery.toLowerCase().contains(term)}
	 * check, which missed every morphological variant:
	 * {@code "planos para carreira"} did not match {@code "plano de carreira"}
	 * because {@code "de"} ≠ {@code "para"} as substrings, even though both
	 * phrases share the same content words.
	 *
	 * <p>Each analyzer applies lowercase + diacritic folding + language-
	 * specific stopword filter + light stemming, collapsing
	 * {@code "planos"}/{@code "plano"}/{@code "planejar"} to one stem and
	 * dropping connectives ({@code de}/{@code para}/{@code com}) entirely.
	 * The match then becomes {@code userStems.containsAll(termStems)} —
	 * insensitive to filler words, plural / verb conjugation differences,
	 * and accent typos.
	 *
	 * <p>Map keyed by ISO 639-1 language code (the 2-letter prefix of
	 * {@link Locale#getLanguage()}). Locales outside the map fall back to
	 * {@link #DEFAULT_ANALYZER}, which is also Portuguese — the platform's
	 * primary audience — so an unmapped or null locale degrades to the
	 * "best guess" rather than to {@code StandardAnalyzer} (no stemming,
	 * no stopwords, no morphology benefit at all).
	 *
	 * <p>Lucene {@link Analyzer} instances are documented as thread-safe;
	 * a single static map is shared across all matching calls.
	 *
	 * @since 2026.2.7
	 */
	private static final Map<String, Analyzer> SPOTLIGHT_ANALYZERS = Map.of(
			"pt", new PortugueseAnalyzer(),
			"en", new EnglishAnalyzer(),
			"es", new SpanishAnalyzer());
	private static final Analyzer DEFAULT_ANALYZER = new PortugueseAnalyzer();
	/**
	 * Last-resort analyzer when even the Portuguese default isn't appropriate
	 * (currently only referenced by tests pinning the "unknown locale →
	 * still works" contract via reflection on the analyzer map). Reserved
	 * for future hook points.
	 */
	@SuppressWarnings("unused")
	private static final Analyzer STANDARD_FALLBACK = new StandardAnalyzer();
	private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	private final TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
	private final TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;
	private final TurSolr turSolr;
	private final TurSpotlightCache turSpotlightCache;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;

	public TurSNSpotlightProcess(TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
			TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository,
			TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository,
			TurSolr turSolr,
			TurSpotlightCache turSpotlightCache,
			TurSNSiteLocaleRepository turSNSiteLocaleRepository) {
		this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
		this.turSNSiteSpotlightTermRepository = turSNSiteSpotlightTermRepository;
		this.turSNSiteSpotlightDocumentRepository = turSNSiteSpotlightDocumentRepository;
		this.turSolr = turSolr;
		this.turSpotlightCache = turSpotlightCache;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
	}

	private void ifExistsDeleteSpotlightDependencies(TurSNSiteSpotlight turSNSiteSpotlight) {
		if (turSNSiteSpotlight != null) {
			Set<TurSNSiteSpotlightTerm> turSNSiteSpotlightTerms = turSNSiteSpotlightTermRepository
					.findByTurSNSiteSpotlight(turSNSiteSpotlight);
			turSNSiteSpotlightTermRepository.deleteAllInBatch(turSNSiteSpotlightTerms);

			Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentRepository
					.findByTurSNSiteSpotlight(turSNSiteSpotlight);
			turSNSiteSpotlightDocumentRepository.deleteAllInBatch(turSNSiteSpotlightDocuments);
		}
	}

	public boolean isSpotlightJob(TurSNJobItem turSNJobItem) {
		return turSNJobItem != null && turSNJobItem.getAttributes() != null
				&& turSNJobItem.getAttributes().containsKey(TurSNFieldName.TYPE)
				&& turSNJobItem.getAttributes().get(TurSNFieldName.TYPE).equals(TYPE_VALUE);
	}

	@CacheEvict(value = { "spotlight", "spotlight_term" }, allEntries = true)
	public boolean deleteUnmanagedSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
		if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.ID)) {
			TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite,
					turSNJobItem.getLocale());
			Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
					.findByUnmanagedIdAndTurSNSiteAndLanguage(
							(String) turSNJobItem.getAttributes().get(TurSNFieldName.ID), turSNSite,
							turSNSiteLocale.getLanguage());
			turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
			logger.warn("Spotlight ID '{}' of '{}' SN Site ({}) was deleted.",
					turSNJobItem.getAttributes().get(TurSNFieldName.ID), turSNSite.getName(),
					turSNJobItem.getLocale());
		} else if (turSNJobItem.getAttributes().containsKey(TurSNFieldName.SOURCE_APPS)) {
			String provider = (String) turSNJobItem.getAttributes().get(TurSNFieldName.SOURCE_APPS);
			Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository.findByProvider(provider);
			turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
			logger.warn("Spotlight by '{}' provider was deleted.", provider);
		}

		return true;
	}

	@CacheEvict(value = { "spotlight", "spotlight_term" }, allEntries = true)
	public boolean createUnmanagedSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
		String id = (String) turSNJobItem.getAttributes().get(TurSNFieldName.ID);
		TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite,
				turSNJobItem.getLocale());

		if (turSNSiteLocale == null) {
			logger.warn("Spotlight ID '{}' of '{}' SN Site was not processed, because {} locale did not found.",
					id, turSNSite.getName(), turSNJobItem.getLocale());
			return false;
		}

		Set<TurSNSiteSpotlight> existingSpotlights = turSNSiteSpotlightRepository
				.findByUnmanagedIdAndTurSNSiteAndLanguage(id, turSNSite, turSNSiteLocale.getLanguage());
		TurSNSiteSpotlight turSNSiteSpotlight = handleExistingSpotlights(existingSpotlights);

		try {
			populateSpotlightFromJobItem(turSNSiteSpotlight, turSNJobItem, turSNSite, turSNSiteLocale);
			turSNSiteSpotlightRepository.save(turSNSiteSpotlight);

			saveSpotlightTerms(turSNJobItem, turSNSiteSpotlight);
			saveSpotlightDocuments(turSNJobItem, turSNSiteSpotlight);

			logger.warn("Spotlight ID '{}' of '{}' SN Site ({}) was created.",
					id, turSNSite.getName(), turSNJobItem.getLocale());
			return true;
		} catch (Exception e) {
			logger.error("Error creating unmanaged spotlight: {}", e.getMessage(), e);
			return false;
		}
	}

	private TurSNSiteSpotlight handleExistingSpotlights(Set<TurSNSiteSpotlight> spotlights) {
		TurSNSiteSpotlight spotlight = new TurSNSiteSpotlight();
		if (!spotlights.isEmpty()) {
			spotlights.forEach(this::ifExistsDeleteSpotlightDependencies);
			if (spotlights.size() > 1) {
				turSNSiteSpotlightRepository.deleteAllInBatch(spotlights);
			} else {
				spotlight = spotlights.iterator().next();
			}
		}
		return spotlight;
	}

	private void populateSpotlightFromJobItem(TurSNSiteSpotlight spotlight, TurSNJobItem jobItem, TurSNSite site,
			TurSNSiteLocale locale) {
		String id = (String) jobItem.getAttributes().get(TurSNFieldName.ID);
		String name = (String) jobItem.getAttributes().get(NAME_ATTRIBUTE);
		String provider = (String) jobItem.getAttributes().get(TurSNFieldName.SOURCE_APPS);
		LocalDateTime date = LocalDateTime.parse(
				(String) jobItem.getAttributes().get(TurSNFieldName.MODIFICATION_DATE),
				DateTimeFormatter.ISO_DATE_TIME);

		spotlight.setUnmanagedId(id);
		spotlight.setDescription(name);
		spotlight.setName(name);
		spotlight.setModificationDate(date);
		spotlight.setTurSNSite(site);
		spotlight.setLanguage(locale.getLanguage());
		spotlight.setManaged(0);
		spotlight.setProvider(provider);
	}

	private void saveSpotlightTerms(TurSNJobItem jobItem, TurSNSiteSpotlight spotlight) {
		String[] terms = ((String) jobItem.getAttributes().get(TERMS_ATTRIBUTE)).split(",");
		for (String term : terms) {
			TurSNSiteSpotlightTerm spotlightTerm = new TurSNSiteSpotlightTerm();
			spotlightTerm.setName(term.trim());
			spotlightTerm.setTurSNSiteSpotlight(spotlight);
			turSNSiteSpotlightTermRepository.save(spotlightTerm);
		}
	}

	private void saveSpotlightDocuments(TurSNJobItem jobItem, TurSNSiteSpotlight spotlight) {
		String jsonContent = (String) jobItem.getAttributes().get(CONTENT_ATTRIBUTE);
		List<TurSpotlightContent> contents = JsonMapper.builder().build()
				.readValue(jsonContent, new TypeReference<List<TurSpotlightContent>>() {
				});
		for (TurSpotlightContent content : contents) {
			TurSNSiteSpotlightDocument document = getTurSNSiteSpotlightDocument(content, spotlight);
			turSNSiteSpotlightDocumentRepository.save(document);
		}
	}

	@NotNull
	private static TurSNSiteSpotlightDocument getTurSNSiteSpotlightDocument(TurSpotlightContent turSpotlightContent,
			TurSNSiteSpotlight turSNSiteSpotlight) {
		TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
		turSNSiteSpotlightDocument.setPosition(turSpotlightContent.getPosition());
		turSNSiteSpotlightDocument.setTitle(turSpotlightContent.getTitle());
		turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightDocument.setContent(turSpotlightContent.getContent());
		turSNSiteSpotlightDocument.setLink(turSpotlightContent.getLink());
		turSNSiteSpotlightDocument.setType(DOCUMENT_TYPE);
		return turSNSiteSpotlightDocument;
	}

	public void addSpotlightToResults(TurSNSiteSearchContext context, TurSolrInstance turSolrInstance,
			TurSNSite turSNSite, Map<String, TurSNSiteFieldExtDto> facetMap,
			Map<String, TurSNSiteFieldExtDto> fieldExtMap,
			List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean) {

		Map<Integer, List<TurSNSiteSpotlightDocument>> turSNSiteSpotlightDocumentMap = getSpotlightsFromQuery(context,
				turSNSite);

		int firstRowPositionFromCurrentPage = TurSolrUtils.firstRowPositionFromCurrentPage(context.getTurSEParameters())
				+ 1;
		int lastRowPositionFromCurrentPage = TurSolrUtils.lastRowPositionFromCurrentPage(context.getTurSEParameters())
				- 1;

		if (lastRowPositionFromCurrentPage > firstRowPositionFromCurrentPage + turSNSiteSearchDocumentsBean.size()) {
			lastRowPositionFromCurrentPage = firstRowPositionFromCurrentPage + turSNSiteSearchDocumentsBean.size();
		}

		int maxPositionFromList = turSNSiteSearchDocumentsBean.size();

		for (int currentPositionFromList = 0; currentPositionFromList < maxPositionFromList; currentPositionFromList++) {

			int currentPositionFromCurrentPage = currentPositionFromList + firstRowPositionFromCurrentPage;

			if (turSNSiteSpotlightDocumentMap.containsKey(currentPositionFromCurrentPage)
					&& currentPositionFromCurrentPage < lastRowPositionFromCurrentPage) {
				lastRowPositionFromCurrentPage++;
				List<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentMap
						.get(currentPositionFromCurrentPage);
				for (TurSNSiteSpotlightDocument document : turSNSiteSpotlightDocuments) {
					TurSEResult turSEResult = turSolr.findById(turSolrInstance, turSNSite, document.getReferenceId(),
							context);
					if (turSEResult != null) {
						TurSNUtils.addSNDocumentWithPosition(context.getUri(), fieldExtMap, facetMap,
								turSNSiteSearchDocumentsBean, turSEResult, true, currentPositionFromList);
					} else {
						Map<String, Object> fields = new HashMap<>();
						fields.put("id", document.getId());
						fields.put(turSNSite.getDefaultDescriptionField(), document.getContent());
						fields.put(turSNSite.getDefaultURLField(), document.getLink());
						fields.put("referenceId", document.getReferenceId());
						fields.put(turSNSite.getDefaultTitleField(), document.getTitle());
						fields.put("type", document.getType());
						TurSNUtils.addSNDocumentWithPosition(context.getUri(), fieldExtMap, facetMap,
								turSNSiteSearchDocumentsBean, TurSEResult.builder().fields(fields).build(),
								true, currentPositionFromList);
					}

				}
			}
		}
	}

	public Map<Integer, List<TurSNSiteSpotlightDocument>> getSpotlightsFromQuery(TurSNSiteSearchContext context,
			TurSNSite turSNSite) {
		List<TurSNSiteSpotlight> turSNSiteSpotlights = new ArrayList<>();
		// T21 / §III.4 V2 — per-query MemoryIndex + QueryParser. Builds an
		// in-memory single-document index over the user's query ONCE per
		// request, then parses each spotlight term as a Lucene query and
		// scores it against the index. Scoring > 0 ⇒ match.
		//
		// Subsumes T20 V1's stem-overlap behavior (plain terms become a
		// BooleanQuery AND'd over their stems — equivalent to containsAll)
		// AND adds phrase + fuzzy + boolean syntax for admin-power users:
		//   "plano de carreira"~2   — phrase with edit-distance 2 on order
		//   carreira~1              — fuzzy with 1-char edit distance
		//   (carro OR veiculo)      — explicit boolean
		String userQuery = context.getTurSEParameters().getQuery();
		Analyzer analyzer = analyzerFor(context.getLocale());
		MemoryIndex userQueryIndex = buildUserQueryIndex(analyzer, userQuery);
		turSpotlightCache.findTermsBySNSiteAndLanguage(turSNSite.getName(), context.getLocale())
				.forEach(turSNSiteSpotlightTerm -> {
					if (matchesByLuceneQuery(userQueryIndex, turSNSiteSpotlightTerm.getTerm(), analyzer)
							&& !turSNSiteSpotlights.contains(turSNSiteSpotlightTerm.getSpotlight())) {
						turSNSiteSpotlights.add(turSNSiteSpotlightTerm.getSpotlight());
					}
				});

		Map<Integer, List<TurSNSiteSpotlightDocument>> turSNSiteSpotlightDocumentMap = new HashMap<>();
		turSNSiteSpotlights.forEach(spotlight -> {
			Set<TurSNSiteSpotlightDocument> turSNSiteSpotlightDocuments = turSNSiteSpotlightDocumentRepository
					.findByTurSNSiteSpotlight(spotlight);
			if (turSNSiteSpotlightDocuments != null && !turSNSiteSpotlightDocuments.isEmpty()) {
				turSNSiteSpotlightDocuments.forEach(document -> {
					if (turSNSiteSpotlightDocumentMap.containsKey(document.getPosition())) {
						turSNSiteSpotlightDocumentMap.get(document.getPosition()).add(document);
					} else {
						turSNSiteSpotlightDocumentMap.put(document.getPosition(),
								new ArrayList<>(List.of(document)));
					}
				});
			}
		});
		return turSNSiteSpotlightDocumentMap;
	}

	/**
	 * T20 / §III.4 V1 — selects the analyzer for the given {@link Locale}.
	 * Looks up by ISO 639-1 language code; null or unmapped locales fall
	 * through to {@link #DEFAULT_ANALYZER} (Portuguese) so the match logic
	 * never throws and always benefits from at least one language's
	 * stemming.
	 */
	private static Analyzer analyzerFor(Locale locale) {
		if (locale == null) {
			return DEFAULT_ANALYZER;
		}
		String language = locale.getLanguage();
		if (language == null || language.isBlank()) {
			return DEFAULT_ANALYZER;
		}
		return SPOTLIGHT_ANALYZERS.getOrDefault(language.toLowerCase(Locale.ROOT), DEFAULT_ANALYZER);
	}

	/**
	 * T21 / §III.4 V2 — name of the Lucene field the user query is indexed
	 * under in the per-request {@link MemoryIndex}. Also the default field
	 * for the {@link QueryParser} parsing each spotlight term, so admins
	 * can write bare-word terms like {@code "carreira"} without a field
	 * prefix.
	 *
	 * <p>Visible at class scope so the package-private test helper can
	 * reuse it instead of hard-coding {@code "q"}.
	 */
	static final String LUCENE_QUERY_FIELD = "q";

	/**
	 * T21 / §III.4 V2 — builds an in-memory single-document Lucene index
	 * over the user's query. {@link MemoryIndex} is purpose-built for this
	 * one-doc + many-queries pattern (orders of magnitude cheaper than
	 * {@link org.apache.lucene.index.IndexWriter}). One instance per
	 * request is reused across every spotlight term lookup.
	 */
	private static MemoryIndex buildUserQueryIndex(Analyzer analyzer, String userQuery) {
		MemoryIndex idx = new MemoryIndex();
		if (userQuery != null && !userQuery.isBlank()) {
			idx.addField(LUCENE_QUERY_FIELD, userQuery, analyzer);
		}
		return idx;
	}

	/**
	 * T21 / §III.4 V2 — parses {@code spotlightTerm} as a Lucene query
	 * (default operator {@code AND}, default field {@link #LUCENE_QUERY_FIELD},
	 * {@code analyzer} for tokenization) and scores it against
	 * {@code userQueryIndex}. Returns true iff the score is strictly
	 * positive, meaning at least one parsed clause matched the user query.
	 *
	 * <p><b>Query syntax admins can write</b>:
	 * <ul>
	 *   <li><b>Plain terms</b>: {@code plano de carreira} → AND of stems
	 *       (V1-equivalent: matches when ALL stems appear, filler "de"
	 *       drops as stopword).</li>
	 *   <li><b>Phrase</b>: {@code "plano de carreira"} → words in order,
	 *       no intervening other tokens.</li>
	 *   <li><b>Phrase with slop</b>: {@code "plano carreira"~2} → words
	 *       within a 2-position window, any order; matches "plano de
	 *       carreira" because "de" is a stopword and the proximity is 1.</li>
	 *   <li><b>Fuzzy</b>: {@code carreira~1} → matches "carrera",
	 *       "carreyra", "carriera" (1-edit-distance typos).</li>
	 *   <li><b>Boolean</b>: {@code (carro OR veiculo) AND novo} →
	 *       explicit operators. Default operator is AND so adjacent words
	 *       behave like the V1 stem-overlap contract.</li>
	 * </ul>
	 *
	 * <p><b>Malformed-input handling</b>: a typo like {@code carro~} or
	 * unbalanced quotes throws {@link ParseException}. We catch it, log
	 * at WARN, and fall back to escaping the term (treating special
	 * characters as literal) — that preserves V1's behavior so a single
	 * misconfigured spotlight can't take down the search path for the
	 * whole site.
	 *
	 * <p>Package-private + static so the unit test pins the contract
	 * without spinning up a Spring context.
	 */
	static boolean matchesByLuceneQuery(MemoryIndex userQueryIndex, String spotlightTerm, Analyzer analyzer) {
		if (spotlightTerm == null || spotlightTerm.isBlank()) {
			return false;
		}
		Query query = parseSpotlightTerm(spotlightTerm, analyzer);
		if (query == null) {
			return false;
		}
		// MemoryIndex.search returns the raw BM25 score; >0 means at
		// least one clause matched. No need to compare against a
		// threshold — the AND default operator already enforces
		// "all required clauses must match".
		return userQueryIndex.search(query) > 0f;
	}

	/**
	 * Parses a spotlight term into a Lucene {@link Query}. Tries the raw
	 * term first so admins keep access to phrase/fuzzy/boolean syntax;
	 * on {@link ParseException} falls back to {@link QueryParser#escape}
	 * which treats every character as literal — that recovers the V1
	 * behavior for terms that don't use special syntax (and that
	 * accidentally contain a {@code ~} or {@code "} the admin didn't
	 * intend as syntax). Returns {@code null} only when even the escaped
	 * fallback fails, which shouldn't happen for any non-empty string.
	 */
	private static Query parseSpotlightTerm(String spotlightTerm, Analyzer analyzer) {
		QueryParser parser = new QueryParser(LUCENE_QUERY_FIELD, analyzer);
		// AND default — keeps "plano carreira" requiring BOTH stems, like
		// V1's containsAll. OR would lower precision below what admins
		// expect when migrating from V1.
		parser.setDefaultOperator(QueryParser.Operator.AND);
		try {
			return parser.parse(spotlightTerm);
		} catch (ParseException raw) {
			logger.warn("Spotlight term '{}' failed to parse as Lucene query (falling back to literal): {}",
					spotlightTerm, raw.getMessage());
			try {
				return parser.parse(QueryParser.escape(spotlightTerm));
			} catch (ParseException escaped) {
				// Escaped input should never fail — log at error so a
				// future Lucene parser regression surfaces fast.
				logger.error("Spotlight term '{}' failed even after escape; spotlight will not match: {}",
						spotlightTerm, escaped.getMessage());
				return null;
			}
		}
	}

}
