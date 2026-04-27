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

package com.viglet.turing.exchange.sn;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurSNSiteImport {
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSEInstanceRepository turSEInstanceRepository;
	private final TurSEVendorRepository turSEVendorRepository;
	private final TurSNSiteFieldRepository turSNSiteFieldRepository;
	private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
	private final TurSNRankingConditionRepository turSNRankingConditionRepository;
	private final TurSNSiteGenAiRepository turSNSiteGenAiRepository;
	private final TurLLMInstanceRepository turLLMInstanceRepository;
	private final TurLLMVendorRepository turLLMVendorRepository;
	private final TurStoreInstanceRepository turStoreInstanceRepository;
	private final TurStoreVendorRepository turStoreVendorRepository;
	private final TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;
	private final TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
	private final TurEmbeddingModelRepository turEmbeddingModelRepository;
	private final com.viglet.turing.persistence.repository.agent.TurAIAgentRepository turAIAgentRepository;
	private final TurSearchEnginePluginFactory turSearchEnginePluginFactory;
	private final CacheManager cacheManager;

	public TurSNSiteImport(TurSNSiteRepository turSNSiteRepository,
			TurSEInstanceRepository turSEInstanceRepository,
			TurSEVendorRepository turSEVendorRepository,
			TurSNSiteFieldRepository turSNSiteFieldRepository,
			TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
			TurSNSiteLocaleRepository turSNSiteLocaleRepository,
			TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
			TurSNRankingExpressionRepository turSNRankingExpressionRepository,
			TurSNRankingConditionRepository turSNRankingConditionRepository,
			TurSNSiteGenAiRepository turSNSiteGenAiRepository,
			TurLLMInstanceRepository turLLMInstanceRepository,
			TurLLMVendorRepository turLLMVendorRepository,
			TurStoreInstanceRepository turStoreInstanceRepository,
			TurStoreVendorRepository turStoreVendorRepository,
			TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository,
			TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
			TurEmbeddingModelRepository turEmbeddingModelRepository,
			com.viglet.turing.persistence.repository.agent.TurAIAgentRepository turAIAgentRepository,
			TurSearchEnginePluginFactory turSearchEnginePluginFactory,
			CacheManager cacheManager) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSEInstanceRepository = turSEInstanceRepository;
		this.turSEVendorRepository = turSEVendorRepository;
		this.turSNSiteFieldRepository = turSNSiteFieldRepository;
		this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
		this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
		this.turSNRankingConditionRepository = turSNRankingConditionRepository;
		this.turSNSiteGenAiRepository = turSNSiteGenAiRepository;
		this.turLLMInstanceRepository = turLLMInstanceRepository;
		this.turLLMVendorRepository = turLLMVendorRepository;
		this.turStoreInstanceRepository = turStoreInstanceRepository;
		this.turStoreVendorRepository = turStoreVendorRepository;
		this.turSNSiteMergeProvidersRepository = turSNSiteMergeProvidersRepository;
		this.turSNSiteCustomSortRepository = turSNSiteCustomSortRepository;
		this.turEmbeddingModelRepository = turEmbeddingModelRepository;
		this.turAIAgentRepository = turAIAgentRepository;
		this.turSearchEnginePluginFactory = turSearchEnginePluginFactory;
		this.cacheManager = cacheManager;
	}

	@Transactional
	public void importSNSite(TurExchange turExchange) {
		importReferencedInstances(turExchange);

		for (TurSNSiteExchange turSNSiteExchange : turExchange.getSnSites()) {
			// Delete existing site if present (handles re-import with same ID)
			turSNSiteRepository.findById(turSNSiteExchange.getId()).ifPresent(existing -> {
				log.info("SN Site already exists, deleting for re-import: {} ({})",
						existing.getName(), existing.getId());
				turSNSiteRepository.delete(existing);
				turSNSiteRepository.flush();
			});

			TurSNSite turSNSite = createSNSiteFromExchange(turSNSiteExchange);

			if (turSNSite.getTurSEInstance() == null) {
				log.error("Cannot import SN Site '{}' ({}): no SE Instance available. " +
						"Please create a Search Engine instance first.",
						turSNSiteExchange.getName(), turSNSiteExchange.getId());
				continue;
			}

			// Save GenAi configuration (preserves original ID)
			saveGenAi(turSNSiteExchange, turSNSite, turExchange);

			TurSNSite savedSite = turSNSiteRepository.saveAndFlush(turSNSite);
			log.info("Saved SN Site: {} ({})", savedSite.getName(), savedSite.getId());

			log.info("Exchange fields: {}, fieldExts: {}, locales: {}, spotlights: {}, rankings: {}",
					turSNSiteExchange.getTurSNSiteFields() != null ? turSNSiteExchange.getTurSNSiteFields().size()
							: "null",
					turSNSiteExchange.getTurSNSiteFieldExts() != null ? turSNSiteExchange.getTurSNSiteFieldExts().size()
							: "null",
					turSNSiteExchange.getTurSNSiteLocales() != null ? turSNSiteExchange.getTurSNSiteLocales().size()
							: "null",
					turSNSiteExchange.getTurSNSiteSpotlights() != null
							? turSNSiteExchange.getTurSNSiteSpotlights().size()
							: "null",
					turSNSiteExchange.getTurSNRankingExpressions() != null
							? turSNSiteExchange.getTurSNRankingExpressions().size()
							: "null");

			saveSNSiteFields(turSNSiteExchange, savedSite);
			saveSNSiteFieldExts(turSNSiteExchange, savedSite);
			saveSNSiteLocales(turSNSiteExchange, savedSite);
			saveSNSiteSpotlights(turSNSiteExchange, savedSite);
			saveSNRankingExpressions(turSNSiteExchange, savedSite);
			saveSNSiteMergeProviders(turSNSiteExchange, savedSite);
			saveSNSiteCustomSorts(turSNSiteExchange, savedSite);

			log.info("Imported SN Site: {} ({})", savedSite.getName(), savedSite.getId());
		}
		clearSemanticNavigationCaches();
	}

	private void importReferencedInstances(TurExchange turExchange) {
		if (turExchange == null) {
			return;
		}
		importSEInstances(turExchange);
		importLLMInstances(turExchange);
		importStoreInstances(turExchange);
		importEmbeddingModels(turExchange);
	}

	private void importSEInstances(TurExchange turExchange) {
		if (turExchange.getSe() == null) {
			return;
		}
		for (TurSEInstance exportedSeInstance : turExchange.getSe()) {
			resolveOrCreateSEInstance(exportedSeInstance);
		}
	}

	private void importLLMInstances(TurExchange turExchange) {
		if (turExchange.getLlm() == null) {
			return;
		}
		for (TurLLMInstance exportedLlmInstance : turExchange.getLlm()) {
			resolveOrCreateLLMInstance(exportedLlmInstance, turExchange);
		}
	}

	private void importStoreInstances(TurExchange turExchange) {
		if (turExchange.getStore() == null) {
			return;
		}
		for (TurStoreInstance exportedStoreInstance : turExchange.getStore()) {
			resolveOrCreateStoreInstance(exportedStoreInstance, turExchange);
		}
	}

	private void importEmbeddingModels(TurExchange turExchange) {
		if (turExchange.getEmbeddingModels() == null) {
			return;
		}
		for (TurEmbeddingModel exportedModel : turExchange.getEmbeddingModels()) {
			if (exportedModel.getId() != null
					&& turEmbeddingModelRepository.findById(exportedModel.getId()).isPresent()) {
				log.info("Embedding Model '{}' already exists, skipping.", exportedModel.getId());
				continue;
			}
			if (exportedModel.getTurLLMInstance() != null && exportedModel.getTurLLMInstance().getId() != null) {
				turLLMInstanceRepository.findById(exportedModel.getTurLLMInstance().getId())
						.ifPresent(exportedModel::setTurLLMInstance);
			}
			turEmbeddingModelRepository.save(exportedModel);
			log.info("Imported Embedding Model: {} ({})", exportedModel.getModelName(), exportedModel.getId());
		}
	}

	private void clearSemanticNavigationCaches() {
		cacheManager.getCacheNames().stream()
				.filter(cacheName -> cacheName.startsWith("turSN") || cacheName.startsWith("spotlight"))
				.forEach(cacheName -> {
					var cache = cacheManager.getCache(cacheName);
					if (cache != null) {
						cache.clear();
					}
				});
		log.info("Semantic Navigation caches were cleared after import.");
	}

	private String resolveUniqueName(String originalName, String siteId) {
		String candidateName = originalName;
		int counter = 1;
		while (true) {
			var existing = turSNSiteRepository.findByNameIgnoreCase(candidateName);
			if (existing.isEmpty() || existing.get().getId().equals(siteId)) {
				break;
			}
			candidateName = originalName + " (" + counter + ")";
			counter++;
		}
		if (!candidateName.equals(originalName)) {
			log.info("SN Site name '{}' already exists with a different ID. Renamed to '{}'.",
					originalName, candidateName);
		}
		return candidateName;
	}

	private TurSNSite createSNSiteFromExchange(TurSNSiteExchange turSNSiteExchange) {
		TurSNSite turSNSite = new TurSNSite();
		turSNSite.setId(turSNSiteExchange.getId());
		turSNSite.setName(resolveUniqueName(turSNSiteExchange.getName(), turSNSiteExchange.getId()));
		turSNSite.setDescription(turSNSiteExchange.getDescription());
		turSNSite.setIcon(turSNSiteExchange.getIcon());
		turSNSite.setRowsPerPage(turSNSiteExchange.getRowsPerPage());
		turSNSite.setWildcardNoResults(turSNSiteExchange.getWildcardNoResults());
		turSNSite.setWildcardAlways(turSNSiteExchange.getWildcardAlways());
		turSNSite.setExactMatch(turSNSiteExchange.getExactMatch());
		turSNSite.setFacet(boolToInteger(turSNSiteExchange.isFacet()));
		turSNSite.setItemsPerFacet(turSNSiteExchange.getItemsPerFacet());
		turSNSite.setHl(boolToInteger(turSNSiteExchange.getHl()));
		turSNSite.setHlPre(turSNSiteExchange.getHlPre());
		turSNSite.setHlPost(turSNSiteExchange.getHlPost());
		turSNSite.setMlt(boolToInteger(turSNSiteExchange.isMlt()));
		turSNSite.setFacetType(turSNSiteExchange.getFacetType());
		turSNSite.setFacetItemType(turSNSiteExchange.getFacetItemType());
		turSNSite.setFacetSort(turSNSiteExchange.getFacetSort());
		turSNSite.setThesaurus(boolToInteger(turSNSiteExchange.isThesaurus()));
		turSNSite.setDefaultField(turSNSiteExchange.getDefaultField());
		turSNSite.setExactMatchField(turSNSiteExchange.getExactMatchField());
		turSNSite.setDefaultTitleField(turSNSiteExchange.getDefaultTitleField());
		turSNSite.setDefaultTextField(turSNSiteExchange.getDefaultTextField());
		turSNSite.setDefaultDescriptionField(turSNSiteExchange.getDefaultDescriptionField());
		turSNSite.setDefaultDateField(turSNSiteExchange.getDefaultDateField());
		turSNSite.setDefaultImageField(turSNSiteExchange.getDefaultImageField());
		turSNSite.setDefaultURLField(turSNSiteExchange.getDefaultURLField());
		turSNSite.setSpellCheck(turSNSiteExchange.getSpellCheck());
		turSNSite.setSpellCheckFixes(turSNSiteExchange.getSpellCheckFixes());
		turSNSite.setSpotlightWithResults(turSNSiteExchange.getSpotlightWithResults());
		turSNSite.setSearchTemplate(turSNSiteExchange.getSearchTemplate());

		// Resolve SE Instance: try the exported ID first, then fall back to any
		// available instance
		resolveSEInstance(turSNSiteExchange, turSNSite);

		return turSNSite;
	}

	private void resolveSEInstance(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSEInstance() != null) {
			turSEInstanceRepository.findById(turSNSiteExchange.getTurSEInstance())
					.ifPresentOrElse(
							turSNSite::setTurSEInstance,
							() -> {
								log.warn("SE Instance '{}' from export not found. " +
										"Falling back to first available SE Instance.",
										turSNSiteExchange.getTurSEInstance());
								assignFirstAvailableSEInstance(turSNSite);
							});
		} else {
			log.warn("No SE Instance specified in export for SN Site '{}'. " +
					"Falling back to first available SE Instance.",
					turSNSiteExchange.getName());
			assignFirstAvailableSEInstance(turSNSite);
		}
	}

	private void assignFirstAvailableSEInstance(TurSNSite turSNSite) {
		turSEInstanceRepository.findAll().stream().findFirst()
				.ifPresentOrElse(
						seInstance -> {
							log.info("Using SE Instance '{}' ({}) as fallback.",
									seInstance.getTitle(), seInstance.getId());
							turSNSite.setTurSEInstance(seInstance);
						},
						() -> {
							log.warn("No SE Instance available. Creating a default Solr instance.");
							TurSEInstance newInstance = createDefaultSEInstance();
							if (newInstance != null) {
								turSNSite.setTurSEInstance(newInstance);
							} else {
								log.error("Failed to create default SE Instance. " +
										"Cannot import SN Site without a Search Engine instance.");
							}
						});
	}

	private TurSEInstance createDefaultSEInstance() {
		return turSEVendorRepository.findById("SOLR")
				.or(() -> turSEVendorRepository.findAll().stream().findFirst())
				.map(vendor -> {
					TurSEInstance seInstance = new TurSEInstance();
					seInstance.setTitle(vendor.getTitle());
					seInstance.setDescription("Auto-created during import");
					seInstance.setTurSEVendor(vendor);
					seInstance.setEndpointUrl("http://localhost:8983/solr");
					seInstance.setEnabled(1);
					TurSEInstance saved = turSEInstanceRepository.save(seInstance);
					log.info("Created default SE Instance '{}' ({}) with vendor '{}'.",
							saved.getTitle(), saved.getId(), vendor.getTitle());
					return saved;
				})
				.orElseGet(() -> {
					log.error("No SE Vendor available in the system. Cannot create SE Instance.");
					return null;
				});
	}

	private void saveGenAi(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite, TurExchange turExchange) {
		TurSNSiteGenAiExchange genAiExchange = turSNSiteExchange.getTurSNSiteGenAi();
		if (genAiExchange != null) {
			TurSNSiteGenAi genAi = new TurSNSiteGenAi();
			genAi.setId(genAiExchange.getId());
			genAi.setSitePrompt(genAiExchange.getSitePrompt());
			if (genAiExchange.getTurAIAgent() != null) {
				turAIAgentRepository.findById(genAiExchange.getTurAIAgent())
						.ifPresent(genAi::setTurAIAgent);
			}

			TurSNSiteGenAi savedGenAi = turSNSiteGenAiRepository.save(genAi);
			turSNSite.setTurSNSiteGenAi(savedGenAi);
			log.info("Saved GenAi configuration with resolved AI Agent reference when available. (turExchange={})",
					turExchange != null ? "present" : "absent");
		}
	}

	private TurLLMInstance resolveOrCreateLLMInstance(TurLLMInstance exportedLlmInstance, TurExchange turExchange) {
		if (exportedLlmInstance == null) {
			return null;
		}

		exportedLlmInstance = resolveLlmDetailsFromRootReference(exportedLlmInstance, turExchange);

		if (exportedLlmInstance.getId() != null) {
			var existing = turLLMInstanceRepository.findById(exportedLlmInstance.getId());
			if (existing.isPresent()) {
				return existing.get();
			}
		}

		if (exportedLlmInstance.getId() == null || exportedLlmInstance.getTitle() == null
				|| exportedLlmInstance.getUrl() == null) {
			log.warn("Skipping creation of LLM Instance due to missing required data (id/title/url).");
			return null;
		}

		TurLLMVendor vendor = resolveLLMVendor(exportedLlmInstance);
		if (vendor == null) {
			log.warn("Could not resolve a LLM Vendor for instance '{}'. Keeping GenAI without LLM reference.",
					exportedLlmInstance.getId());
			return null;
		}

		TurLLMInstance llmToCreate = new TurLLMInstance();
		llmToCreate.setId(exportedLlmInstance.getId());
		llmToCreate.setTitle(exportedLlmInstance.getTitle());
		llmToCreate.setDescription(exportedLlmInstance.getDescription());
		llmToCreate.setEnabled(exportedLlmInstance.getEnabled());
		llmToCreate.setUrl(exportedLlmInstance.getUrl());
		llmToCreate.setTurLLMVendor(vendor);
		llmToCreate.setModelName(exportedLlmInstance.getModelName());
		llmToCreate.setTemperature(exportedLlmInstance.getTemperature());
		llmToCreate.setTopK(exportedLlmInstance.getTopK());
		llmToCreate.setTopP(exportedLlmInstance.getTopP());
		llmToCreate.setRepeatPenalty(exportedLlmInstance.getRepeatPenalty());
		llmToCreate.setSeed(exportedLlmInstance.getSeed());
		llmToCreate.setNumPredict(exportedLlmInstance.getNumPredict());
		llmToCreate.setStop(exportedLlmInstance.getStop());
		llmToCreate.setResponseFormat(exportedLlmInstance.getResponseFormat());
		llmToCreate.setSupportedCapabilities(exportedLlmInstance.getSupportedCapabilities());
		llmToCreate.setTimeout(exportedLlmInstance.getTimeout());
		llmToCreate.setMaxRetries(exportedLlmInstance.getMaxRetries());

		TurLLMInstance created = turLLMInstanceRepository.save(llmToCreate);
		log.info("Created missing LLM Instance '{}' ({}) during GenAI import.",
				created.getTitle(), created.getId());
		return created;
	}

	private TurStoreInstance resolveOrCreateStoreInstance(TurStoreInstance exportedStoreInstance,
			TurExchange turExchange) {
		if (exportedStoreInstance == null) {
			return null;
		}

		exportedStoreInstance = resolveStoreDetailsFromRootReference(exportedStoreInstance, turExchange);

		if (exportedStoreInstance.getId() != null) {
			var existing = turStoreInstanceRepository.findById(exportedStoreInstance.getId());
			if (existing.isPresent()) {
				return existing.get();
			}
		}

		if (exportedStoreInstance.getId() == null || exportedStoreInstance.getTitle() == null
				|| exportedStoreInstance.getUrl() == null) {
			log.warn("Skipping creation of Store Instance due to missing required data (id/title/url).");
			return null;
		}

		TurStoreVendor vendor = resolveStoreVendor(exportedStoreInstance);
		if (vendor == null) {
			log.warn("Could not resolve a Store Vendor for instance '{}'. Keeping GenAI without Store reference.",
					exportedStoreInstance.getId());
			return null;
		}

		TurStoreInstance storeToCreate = new TurStoreInstance();
		storeToCreate.setId(exportedStoreInstance.getId());
		storeToCreate.setTitle(exportedStoreInstance.getTitle());
		storeToCreate.setDescription(exportedStoreInstance.getDescription());
		storeToCreate.setEnabled(exportedStoreInstance.getEnabled());
		storeToCreate.setUrl(exportedStoreInstance.getUrl());
		storeToCreate.setTurStoreVendor(vendor);

		TurStoreInstance created = turStoreInstanceRepository.save(storeToCreate);
		log.info("Created missing Store Instance '{}' ({}) during GenAI import.",
				created.getTitle(), created.getId());
		return created;
	}

	private TurLLMVendor resolveLLMVendor(TurLLMInstance exportedLlmInstance) {
		if (exportedLlmInstance.getTurLLMVendor() != null
				&& exportedLlmInstance.getTurLLMVendor().getId() != null) {
			var fromId = turLLMVendorRepository.findById(exportedLlmInstance.getTurLLMVendor().getId());
			if (fromId.isPresent()) {
				return fromId.get();
			}
			log.warn("LLM Vendor '{}' from export not found. Falling back to first available vendor.",
					exportedLlmInstance.getTurLLMVendor().getId());
		}

		return turLLMVendorRepository.findAll().stream().findFirst().orElse(null);
	}

	private TurStoreVendor resolveStoreVendor(TurStoreInstance exportedStoreInstance) {
		if (exportedStoreInstance.getTurStoreVendor() != null
				&& exportedStoreInstance.getTurStoreVendor().getId() != null) {
			var fromId = turStoreVendorRepository.findById(exportedStoreInstance.getTurStoreVendor().getId());
			if (fromId.isPresent()) {
				return fromId.get();
			}
			log.warn("Store Vendor '{}' from export not found. Falling back to first available vendor.",
					exportedStoreInstance.getTurStoreVendor().getId());
		}

		return turStoreVendorRepository.findAll().stream().findFirst().orElse(null);
	}

	private TurLLMInstance resolveLlmDetailsFromRootReference(TurLLMInstance exportedLlmInstance,
			TurExchange turExchange) {
		if (exportedLlmInstance == null || exportedLlmInstance.getId() == null || turExchange == null
				|| turExchange.getLlm() == null) {
			return exportedLlmInstance;
		}

		if (exportedLlmInstance.getTitle() != null && exportedLlmInstance.getUrl() != null
				&& exportedLlmInstance.getTurLLMVendor() != null) {
			return exportedLlmInstance;
		}

		for (TurLLMInstance llmInstance : turExchange.getLlm()) {
			if (exportedLlmInstance.getId().equals(llmInstance.getId())) {
				return llmInstance;
			}
		}

		return exportedLlmInstance;
	}

	private TurStoreInstance resolveStoreDetailsFromRootReference(TurStoreInstance exportedStoreInstance,
			TurExchange turExchange) {
		if (exportedStoreInstance == null || exportedStoreInstance.getId() == null || turExchange == null
				|| turExchange.getStore() == null) {
			return exportedStoreInstance;
		}

		if (exportedStoreInstance.getTitle() != null && exportedStoreInstance.getUrl() != null
				&& exportedStoreInstance.getTurStoreVendor() != null) {
			return exportedStoreInstance;
		}

		for (TurStoreInstance storeInstance : turExchange.getStore()) {
			if (exportedStoreInstance.getId().equals(storeInstance.getId())) {
				return storeInstance;
			}
		}

		return exportedStoreInstance;
	}

	private TurSEInstance resolveOrCreateSEInstance(TurSEInstance exportedSeInstance) {
		if (exportedSeInstance == null || exportedSeInstance.getId() == null) {
			return null;
		}

		var existing = turSEInstanceRepository.findById(exportedSeInstance.getId());
		if (existing.isPresent()) {
			return existing.get();
		}

		if (exportedSeInstance.getTitle() == null || exportedSeInstance.getEndpointUrl() == null) {
			log.warn("Skipping creation of SE Instance due to missing required data (id/title/endpointUrl). id={}",
					exportedSeInstance.getId());
			return null;
		}

		if (exportedSeInstance.getTurSEVendor() == null || exportedSeInstance.getTurSEVendor().getId() == null) {
			log.warn("Skipping creation of SE Instance '{}' because se vendor is missing.",
					exportedSeInstance.getId());
			return null;
		}

		return turSEVendorRepository.findById(exportedSeInstance.getTurSEVendor().getId())
				.or(() -> turSEVendorRepository.findAll().stream().findFirst())
				.map(vendor -> {
					TurSEInstance seToCreate = new TurSEInstance();
					seToCreate.setId(exportedSeInstance.getId());
					seToCreate.setTitle(exportedSeInstance.getTitle());
					seToCreate.setDescription(exportedSeInstance.getDescription());
					seToCreate.setEnabled(exportedSeInstance.getEnabled());
					seToCreate.setEndpointUrl(exportedSeInstance.getEndpointUrl());
					seToCreate.setTurSEVendor(vendor);
					TurSEInstance created = turSEInstanceRepository.save(seToCreate);
					log.info("Created missing SE Instance '{}' ({}) from root exchange references.",
							created.getTitle(), created.getId());
					return created;
				})
				.orElseGet(() -> {
					log.warn("No SE Vendor available to create referenced SE Instance '{}' .",
							exportedSeInstance.getId());
					return null;
				});
	}

	private void saveSNSiteMergeProviders(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSNSiteMergeProviders() != null) {
			for (TurSNSiteMergeProviders mergeProvider : turSNSiteExchange.getTurSNSiteMergeProviders()) {
				mergeProvider.setTurSNSite(turSNSite);
				if (mergeProvider.getOverwrittenFields() != null) {
					for (TurSNSiteMergeProvidersField overwrittenField : mergeProvider.getOverwrittenFields()) {
						overwrittenField.setTurSNSiteMergeProviders(mergeProvider);
					}
				}
				turSNSiteMergeProvidersRepository.save(mergeProvider);
			}
		}
	}

	private void saveSNSiteFields(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSNSiteFields() != null) {
			for (TurSNSiteField field : turSNSiteExchange.getTurSNSiteFields()) {
				field.setTurSNSite(turSNSite);
				turSNSiteFieldRepository.save(field);
			}
		}
	}

	private void saveSNSiteFieldExts(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSNSiteFieldExts() != null) {
			for (TurSNSiteFieldExt fieldExt : turSNSiteExchange.getTurSNSiteFieldExts()) {
				fieldExt.setTurSNSite(turSNSite);
				if (fieldExt.getFacetLocales() != null) {
					for (TurSNSiteFieldExtFacet facet : fieldExt.getFacetLocales()) {
						facet.setTurSNSiteFieldExt(fieldExt);
					}
				}
				if (fieldExt.getCustomFacets() != null) {
					for (TurSNSiteCustomFacet customFacet : fieldExt.getCustomFacets()) {
						customFacet.setTurSNSiteFieldExt(fieldExt);
						if (customFacet.getItems() != null) {
							for (TurSNSiteCustomFacetItem item : customFacet.getItems()) {
								item.setTurSNSiteCustomFacet(customFacet);
							}
						}
					}
				}
				turSNSiteFieldExtRepository.save(fieldExt);
			}
		}
	}

	private void saveSNSiteLocales(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSNSiteLocales() != null) {
			for (TurSNSiteLocale locale : turSNSiteExchange.getTurSNSiteLocales()) {
				locale.setTurSNSite(turSNSite);
				turSNSiteLocaleRepository.save(locale);
				createIndexIfNotExists(turSNSite, locale);
			}
		}
	}

	private void createIndexIfNotExists(TurSNSite turSNSite, TurSNSiteLocale locale) {
		TurSEInstance seInstance = turSNSite.getTurSEInstance();
		if (seInstance == null || locale.getCore() == null) {
			return;
		}
		try {
			var plugin = turSearchEnginePluginFactory.getPluginForInstance(seInstance);
			if (!plugin.indexExists(seInstance, locale.getCore())) {
				log.info("Index '{}' does not exist in SE Instance '{}'. Creating it...",
						locale.getCore(), seInstance.getTitle());
				plugin.createIndex(seInstance, locale, locale.getCore(), Map.of());
				log.info("Index '{}' created successfully.", locale.getCore());
			} else {
				log.info("Index '{}' already exists in SE Instance '{}'.",
						locale.getCore(), seInstance.getTitle());
			}
		} catch (Exception e) {
			log.error("Failed to create index '{}' in SE Instance '{}': {}",
					locale.getCore(), seInstance.getTitle(), e.getMessage(), e);
		}
	}

	private void saveSNSiteSpotlights(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSNSiteSpotlights() != null) {
			for (TurSNSiteSpotlight spotlight : turSNSiteExchange.getTurSNSiteSpotlights()) {
				spotlight.setTurSNSite(turSNSite);
				saveSpotlightTerms(spotlight);
				saveSpotlightDocuments(spotlight);
				turSNSiteSpotlightRepository.save(spotlight);
			}
		}
	}

	private void saveSpotlightTerms(TurSNSiteSpotlight spotlight) {
		if (spotlight.getTurSNSiteSpotlightTerms() != null) {
			for (TurSNSiteSpotlightTerm term : spotlight.getTurSNSiteSpotlightTerms()) {
				term.setTurSNSiteSpotlight(spotlight);
			}
		}
	}

	private void saveSpotlightDocuments(TurSNSiteSpotlight spotlight) {
		if (spotlight.getTurSNSiteSpotlightDocuments() != null) {
			for (TurSNSiteSpotlightDocument doc : spotlight.getTurSNSiteSpotlightDocuments()) {
				doc.setTurSNSiteSpotlight(spotlight);
			}
		}
	}

	private void saveSNRankingExpressions(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSNRankingExpressions() != null) {
			for (TurSNRankingExpression expr : turSNSiteExchange.getTurSNRankingExpressions()) {
				expr.setTurSNSite(turSNSite);
				turSNRankingExpressionRepository.save(expr);
				if (expr.getTurSNRankingConditions() != null) {
					for (TurSNRankingCondition condition : expr.getTurSNRankingConditions()) {
						condition.setTurSNRankingExpression(expr);
						turSNRankingConditionRepository.save(condition);
					}
				}
			}
		}
	}

	private void saveSNSiteCustomSorts(TurSNSiteExchange turSNSiteExchange, TurSNSite turSNSite) {
		if (turSNSiteExchange.getTurSNSiteCustomSorts() != null) {
			for (TurSNSiteCustomSort customSort : turSNSiteExchange.getTurSNSiteCustomSorts()) {
				customSort.setTurSNSite(turSNSite);
				if (customSort.getItems() != null) {
					for (TurSNSiteCustomSortItem item : customSort.getItems()) {
						item.setTurSNSiteCustomSort(customSort);
					}
				}
				turSNSiteCustomSortRepository.save(customSort);
			}
		}
	}

	private Integer boolToInteger(boolean bool) {
		return bool ? 1 : 0;
	}
}
