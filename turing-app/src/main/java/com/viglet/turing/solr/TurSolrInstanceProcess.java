/*
 * Copyright (C) 2016-2021 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.solr;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 *
 */
@Component
public class TurSolrInstanceProcess {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	private CloseableHttpClient closeableHttpClient;
	@Autowired
	private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSolrCache turSolrCache;

	private Optional<TurSolrInstance> getSolrClient(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {
		return getSolrClient(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());
	}

	private Optional<TurSolrInstance> getSolrClient(TurSEInstance turSEInstance, String core) {
		String urlString = String.format("http://%s:%s/solr/%s", turSEInstance.getHost(), turSEInstance.getPort(),
				core);
		if (turSolrCache.isSolrCoreExists(urlString)) {
			HttpSolrClient httpSolrClient = new HttpSolrClient.Builder(urlString).withHttpClient(closeableHttpClient)
					.withConnectionTimeout(30000).withSocketTimeout(30000).build();
			try {
				return Optional.of(new TurSolrInstance(closeableHttpClient, httpSolrClient, new URL(urlString), core));
			} catch (MalformedURLException e) {
				logger.error(e.getMessage(), e);
			}
		}
		else {
			logger.warn("{} is not available", urlString);
		}
		return Optional.empty();
	}

	public Optional<TurSolrInstance> initSolrInstance(String siteName, String locale) {
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		if (turSNSite != null) {
			return this.initSolrInstance(turSNSite, locale);
		} else {
			logger.warn("{} site not found", siteName);
			return Optional.empty();
		}
	}

	private Optional<TurSolrInstance> initSolrInstance(TurSNSite turSNSite, String locale) {
		TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
		if (turSNSiteLocale != null) {
			return this.initSolrInstance(turSNSiteLocale);
		} else {
			logger.warn("{} site with {} locale not found", turSNSite.getName(), locale);
			return Optional.empty();
		}
	}

	public Optional<TurSolrInstance> initSolrInstance(TurSEInstance turSEInstance, String core) {
		return this.getSolrClient(turSEInstance, core);
	}

	public Optional<TurSolrInstance> initSolrInstance(TurSNSiteLocale turSNSiteLocale) {
		return this.getSolrClient(turSNSiteLocale.getTurSNSite(), turSNSiteLocale);

	}

	public Optional<TurSolrInstance> initSolrInstance() {
		return turConfigVarRepository.findById("DEFAULT_SE")
				.map(turConfigVar -> turSEInstanceRepository.findById(turConfigVar.getValue())
						.map(turSEInstance -> getSolrClient(turSEInstance, "turing")).orElse(null))
				.orElse(null);
	}

}
