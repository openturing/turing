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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.api.sn.queue.TurSpotlightContent;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDocumentBean;
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
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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
	private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	private final TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
	private final TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;
	private final TurSolr turSolr;
	private final TurSpotlightCache turSpotlightCache;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	@Inject
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
				&& turSNJobItem.getAttributes().containsKey(TurSNConstants.TYPE_ATTRIBUTE)
				&& turSNJobItem.getAttributes().get(TurSNConstants.TYPE_ATTRIBUTE).equals(TYPE_VALUE);
	}

	@CacheEvict(value = { "spotlight", "spotlight_term" }, allEntries = true)
	public boolean deleteUnmanagedSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
		if (turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
			TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite,
					turSNJobItem.getLocale());
			Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
					.findByUnmanagedIdAndTurSNSiteAndLanguage(
							(String) turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE), turSNSite,
							turSNSiteLocale.getLanguage());
			turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
			logger.warn("Spotlight ID '{}' of '{}' SN Site ({}) was deleted.",
					turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE), turSNSite.getName(),
					turSNJobItem.getLocale());
		} else if (turSNJobItem.getAttributes().containsKey(TurSNConstants.PROVIDER_ATTRIBUTE)) {
			String provider = (String) turSNJobItem.getAttributes().get(TurSNConstants.PROVIDER_ATTRIBUTE);
			Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository.findByProvider(provider);
			turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
			logger.warn("Spotlight by '{}' provider was deleted.", provider);
		}

		return true;
	}

	@CacheEvict(value = { "spotlight", "spotlight_term" }, allEntries = true)
	public boolean createUnmanagedSpotlight(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
		String id = (String) turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE);
		TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite,
				turSNJobItem.getLocale());
		if (turSNSiteLocale == null) {
			logger.warn("Spotlight ID '{}' of '{}' SN Site was not processed, because {} locale did not found.",
					turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE), turSNSite.getName(),
					turSNJobItem.getLocale());
			return false;
		} else {
			Set<TurSNSiteSpotlight> turSNSiteSpotlights = turSNSiteSpotlightRepository
					.findByUnmanagedIdAndTurSNSiteAndLanguage(id, turSNSite, turSNSiteLocale.getLanguage());
			TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
			if (!turSNSiteSpotlights.isEmpty()) {
				turSNSiteSpotlights.forEach(this::ifExistsDeleteSpotlightDependencies);
				if (turSNSiteSpotlights.size() > 1) {
					turSNSiteSpotlightRepository.deleteAllInBatch(turSNSiteSpotlights);
				} else {
					turSNSiteSpotlight = turSNSiteSpotlights.iterator().next();
				}
			}
			try {
				String jsonContent = (String) turSNJobItem.getAttributes().get(CONTENT_ATTRIBUTE);
				String name = (String) turSNJobItem.getAttributes().get(NAME_ATTRIBUTE);
				String provider = (String) turSNJobItem.getAttributes().get(TurSNConstants.PROVIDER_ATTRIBUTE);
				String[] terms = ((String) turSNJobItem.getAttributes().get(TERMS_ATTRIBUTE)).split(",");
				SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
				simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
				Date date = simpleDateFormat
						.parse((String) turSNJobItem.getAttributes().get(TurSNConstants.MODIFICATION_DATE_ATTRIBUTE));
				List<TurSpotlightContent> turSpotlightContents = new ObjectMapper().readValue(jsonContent,
						new TypeReference<>() {
						});

				turSNSiteSpotlight.setUnmanagedId(id);
				turSNSiteSpotlight.setDescription(name);
				turSNSiteSpotlight.setName(name);
				turSNSiteSpotlight.setModificationDate(date);
				turSNSiteSpotlight.setTurSNSite(turSNSite);
				turSNSiteSpotlight.setLanguage(turSNSiteLocale.getLanguage());
				turSNSiteSpotlight.setManaged(0);
				turSNSiteSpotlight.setProvider(provider);
				turSNSiteSpotlightRepository.save(turSNSiteSpotlight);

				for (String term : terms) {
					TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
					turSNSiteSpotlightTerm.setName(term.trim());
					turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);
					turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm);
				}

				for (TurSpotlightContent turSpotlightContent : turSpotlightContents) {
					final TurSNSiteSpotlightDocument turSNSiteSpotlightDocument =
							getTurSNSiteSpotlightDocument(turSpotlightContent, turSNSiteSpotlight);
					turSNSiteSpotlightDocumentRepository.save(turSNSiteSpotlightDocument);
				}
				logger.warn("Spotlight ID '{}' of '{}' SN Site ({}) was created.",
						turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE), turSNSite.getName(),
						turSNJobItem.getLocale());
			} catch (ParseException | JsonProcessingException e) {
				logger.error(e.getMessage(), e);
			}
			return true;
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
									  TurSNSite turSNSite, Map<String, TurSNSiteFieldExtDto> facetMap, Map<String, TurSNSiteFieldExtDto> fieldExtMap,
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
					TurSEResult turSEResult = turSolr.findById(turSolrInstance, turSNSite, document.getReferenceId());
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
		turSpotlightCache.findTermsBySNSiteAndLanguage(turSNSite.getName(), context.getLocale())
				.forEach(turSNSiteSpotlightTerm -> {
					if (context.getTurSEParameters().getQuery().toLowerCase()
							.contains(turSNSiteSpotlightTerm.getTerm().toLowerCase())
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

}