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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletResponse;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class TurSNSiteExport {

	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	private final TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;
	private final TurSNSiteCustomSortRepository turSNSiteCustomSortRepository;
	private final TurSNSiteExportFileService exportFileService;
	private final TurSNSiteContentExchangeService contentExchangeService;
	private final TransactionTemplate readOnlyTransactionTemplate;
	private final ConcurrentHashMap<String, Path> asyncExportFiles = new ConcurrentHashMap<>();

	public TurSNSiteExport(TurSNSiteRepository turSNSiteRepository,
			TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
			TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository,
			TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
			TurSNSiteExportFileService exportFileService,
			TurSNSiteContentExchangeService contentExchangeService,
			PlatformTransactionManager transactionManager) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
		this.turSNSiteMergeProvidersRepository = turSNSiteMergeProvidersRepository;
		this.turSNSiteCustomSortRepository = turSNSiteCustomSortRepository;
		this.exportFileService = exportFileService;
		this.contentExchangeService = contentExchangeService;
		this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
		this.readOnlyTransactionTemplate.setReadOnly(true);
	}

	public StreamingResponseBody exportAll(HttpServletResponse response) {
		List<TurSNSite> turSNSites = readOnlyTransactionTemplate.execute(status -> {
			List<TurSNSite> sites = turSNSiteRepository.findAll();
			sites.forEach(site -> {
				hydrateExportCollections(site);
				initializeLazyAssociations(site);
			});
			return sites;
		});

		return createSNSiteZipResponse(response, turSNSites, "sn-sites-all", null, false);
	}

	public StreamingResponseBody exportBySiteId(String siteId, HttpServletResponse response) {
		return exportBySiteId(siteId, false, false, null, response);
	}

	public StreamingResponseBody exportBySiteId(String siteId, boolean includeContent,
			boolean includeTemplate, String taskId, HttpServletResponse response) {
		List<TurSNSite> turSNSites = readOnlyTransactionTemplate.execute(status -> {
			// Loaded inside this read-only transaction so lazy associations
			// initialize through the open session (T488 / §XXVIII.3 — repositories
			// are uncached, so a plain findById returns a session-attached entity).
			List<TurSNSite> sites = turSNSiteRepository.findById(siteId).map(List::of).orElse(List.of());
			sites.forEach(site -> {
				hydrateExportCollections(site);
				initializeLazyAssociations(site);
			});
			return sites;
		});
		if (turSNSites.isEmpty()) {
			return null;
		}

		Map<String, Map<String, List<Map<String, Object>>>> contentMap = null;
		if (includeContent) {
			contentMap = contentExchangeService.exportContent(siteId, taskId != null ? taskId : siteId);
		}

		return createSNSiteZipResponse(response, turSNSites,
				"sn-site-" + turSNSites.get(0).getName(), contentMap, includeTemplate);
	}

	private void hydrateExportCollections(TurSNSite turSNSite) {
		var spotlights = turSNSiteSpotlightRepository
				.findByTurSNSite(Sort.by(Sort.Order.asc("name").ignoreCase()), turSNSite);
		turSNSite.setTurSNSiteSpotlights(new HashSet<>(spotlights));

		var mergeProviders = turSNSiteMergeProvidersRepository.findByTurSNSite(turSNSite);
		mergeProviders.forEach(mergeProvider -> mergeProvider.getOverwrittenFields().size());
		turSNSite.setTurSNSiteMergeProviders(new HashSet<>(mergeProviders));

		var customSorts = turSNSiteCustomSortRepository.findByTurSNSiteWithItems(turSNSite);
		turSNSite.setTurSNSiteCustomSorts(new HashSet<>(customSorts));
	}

	private void initializeLazyAssociations(TurSNSite site) {
		Hibernate.initialize(site.getTurSNSiteFields());
		Hibernate.initialize(site.getTurSNSiteFieldExts());
		Hibernate.initialize(site.getTurSNSiteLocales());
		Hibernate.initialize(site.getTurSNRankingExpressions());
		if (site.getTurSEInstance() != null) {
			Hibernate.initialize(site.getTurSEInstance());
		}
		TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
		if (genAi != null) {
			Hibernate.initialize(genAi);
			if (genAi.getTurAIAgent() != null) {
				Hibernate.initialize(genAi.getTurAIAgent());
			}
		}
	}

	private StreamingResponseBody createSNSiteZipResponse(HttpServletResponse response, List<TurSNSite> turSNSites,
			String prefixZipFileName, Map<String, Map<String, List<Map<String, Object>>>> contentMap,
			boolean includeTemplate) {
		try {
			String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
			String zipFileName = prefixZipFileName + "_" + strDate + ".zip";
			Path zipFilePath = exportFileService.exportSNSitesToZip(turSNSites, contentMap, includeTemplate);

			response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
			response.setContentType("application/octet-stream");
			response.setStatus(HttpServletResponse.SC_OK);

			return output -> {
				try {
					Files.copy(zipFilePath, output);
					output.flush();
				} catch (IOException e) {
					log.error("Error streaming SNSite export zip: {}", e.getMessage(), e);
				} finally {
					try {
						Files.deleteIfExists(zipFilePath);
					} catch (IOException e) {
						log.warn("Could not delete temporary export file: {}", zipFilePath);
					}
				}
			};
		} catch (Exception e) {
			log.error("Error exporting SNSites: {}", e.getMessage(), e);
			return null;
		}
	}

	public void exportBySiteIdAsync(String siteId, boolean includeContent, boolean includeTemplate, String taskId) {
		List<TurSNSite> turSNSites = readOnlyTransactionTemplate.execute(status -> {
			List<TurSNSite> sites = turSNSiteRepository.findById(siteId).map(List::of).orElse(List.of());
			sites.forEach(site -> {
				hydrateExportCollections(site);
				initializeLazyAssociations(site);
			});
			return sites;
		});
		if (turSNSites.isEmpty()) {
			return;
		}

		Map<String, Map<String, List<Map<String, Object>>>> contentMap = null;
		if (includeContent) {
			contentMap = contentExchangeService.exportContent(siteId, taskId);
		}

		contentExchangeService.updatePhase(taskId, "packaging");
		Path zipFilePath = exportFileService.exportSNSitesToZip(turSNSites, contentMap, includeTemplate);
		asyncExportFiles.put(taskId, zipFilePath);
		contentExchangeService.updatePhase(taskId, "completed");
	}

	public StreamingResponseBody downloadAsyncExport(String taskId, HttpServletResponse response) {
		Path zipFilePath = asyncExportFiles.remove(taskId);
		if (zipFilePath == null || !Files.exists(zipFilePath)) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return null;
		}

		String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
		response.addHeader("Content-disposition", "attachment;filename=sn-site-export_" + strDate + ".zip");
		response.setContentType("application/octet-stream");
		response.setStatus(HttpServletResponse.SC_OK);

		return output -> {
			try {
				Files.copy(zipFilePath, output);
				output.flush();
			} catch (IOException e) {
				log.error("Error streaming async export zip: {}", e.getMessage(), e);
			} finally {
				try {
					Files.deleteIfExists(zipFilePath);
				} catch (IOException e) {
					log.warn("Could not delete temporary export file: {}", zipFilePath);
				}
			}
		};
	}

}
