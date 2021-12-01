package com.viglet.turing.solr;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Component
public class TurSolrInstanceProcess {
	private static final Logger logger = LogManager.getLogger(TurSolrInstanceProcess.class);

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

	private Optional<TurSolrInstance> getSolrClient(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {

		return getSolrClient(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());

	}

	private Optional<TurSolrInstance> getSolrClient(TurSEInstance turSEInstance, String core) {
		int responseCode = 0;
		String urlString = "http://" + turSEInstance.getHost() + ":" + turSEInstance.getPort() + "/solr/" + core;
		URL url;
		try {
			url = new URL(urlString.concat("/select"));
			HttpURLConnection huc = (HttpURLConnection) url.openConnection();
			responseCode = huc.getResponseCode();
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		if (responseCode == 200) {
			HttpSolrClient httpSolrClient = new HttpSolrClient.Builder(urlString).withHttpClient(closeableHttpClient)
					.withConnectionTimeout(30000).withSocketTimeout(30000).build();

			return Optional.of(new TurSolrInstance(closeableHttpClient, httpSolrClient, core));
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

	public Optional<TurSolrInstance> initSolrInstance(TurSNSite turSNSite, String locale) {
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
