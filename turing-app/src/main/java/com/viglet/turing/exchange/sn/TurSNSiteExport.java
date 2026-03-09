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

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
public class TurSNSiteExport {

	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	private final TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository;
	private final TurSNSiteExportFileService exportFileService;

	public TurSNSiteExport(TurSNSiteRepository turSNSiteRepository,
			TurSNSiteSpotlightRepository turSNSiteSpotlightRepository,
			TurSNSiteMergeProvidersRepository turSNSiteMergeProvidersRepository,
			TurSNSiteExportFileService exportFileService) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNSiteSpotlightRepository = turSNSiteSpotlightRepository;
		this.turSNSiteMergeProvidersRepository = turSNSiteMergeProvidersRepository;
		this.exportFileService = exportFileService;
	}

	public StreamingResponseBody exportAll(HttpServletResponse response) {
		List<TurSNSite> turSNSites = turSNSiteRepository.findAll();
		turSNSites.forEach(this::hydrateExportCollections);

		return createSNSiteZipResponse(response, turSNSites, "sn-sites-all");
	}

	public StreamingResponseBody exportBySiteId(String siteId, HttpServletResponse response) {
		List<TurSNSite> turSNSites = turSNSiteRepository.findById(siteId).map(List::of).orElse(List.of());
		if (turSNSites.isEmpty()) {
			return null;
		}
		turSNSites.forEach(this::hydrateExportCollections);
		return createSNSiteZipResponse(response, turSNSites, "sn-site-" + turSNSites.get(0).getName());
	}

	private void hydrateExportCollections(TurSNSite turSNSite) {
		var spotlights = turSNSiteSpotlightRepository
				.findByTurSNSite(Sort.by(Sort.Order.asc("name").ignoreCase()), turSNSite);
		turSNSite.setTurSNSiteSpotlights(new HashSet<>(spotlights));

		var mergeProviders = turSNSiteMergeProvidersRepository.findByTurSNSite(turSNSite);
		mergeProviders.forEach(mergeProvider -> mergeProvider.getOverwrittenFields().size());
		turSNSite.setTurSNSiteMergeProviders(new HashSet<>(mergeProviders));
	}

	private StreamingResponseBody createSNSiteZipResponse(HttpServletResponse response, List<TurSNSite> turSNSites,
			String prefixZipFileName) {
		try {
			String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
			String zipFileName = prefixZipFileName + "_" + strDate + ".zip";
			Path zipFilePath = exportFileService.exportSNSitesToZip(turSNSites);

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

}
