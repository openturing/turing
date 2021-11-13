package com.viglet.turing.solr;

import org.apache.http.impl.client.CloseableHttpClient;
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

	private TurSolrInstance getSolrClient(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {

		return getSolrClient(turSNSite.getTurSEInstance(), turSNSiteLocale.getCore());

	}

	private TurSolrInstance getSolrClient(TurSEInstance turSEInstance, String core) {
		String urlString = "http://" + turSEInstance.getHost() + ":" + turSEInstance.getPort() + "/solr/" + core;
		HttpSolrClient httpSolrClient = new HttpSolrClient.Builder(urlString).withHttpClient(closeableHttpClient)
				.withConnectionTimeout(30000).withSocketTimeout(30000).build();

		return new TurSolrInstance(closeableHttpClient, httpSolrClient, core);

	}

	public TurSolrInstance initSolrInstance(String siteName, String locale) {
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		return this.initSolrInstance(turSNSite, locale);
	}

	public TurSolrInstance initSolrInstance(TurSNSite turSNSite, String locale) {
		TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
		return this.initSolrInstance(turSNSiteLocale);
	}

	public TurSolrInstance initSolrInstance(TurSEInstance turSEInstance, String core) {
		return this.getSolrClient(turSEInstance, core);
	}

	public TurSolrInstance initSolrInstance(TurSNSiteLocale turSNSiteLocale) {
		return this.getSolrClient(turSNSiteLocale.getTurSNSite(), turSNSiteLocale);

	}

	public TurSolrInstance initSolrInstance() {
		return turConfigVarRepository.findById("DEFAULT_SE")
				.map(turConfigVar -> turSEInstanceRepository.findById(turConfigVar.getValue())
						.map(turSEInstance -> getSolrClient(turSEInstance, "turing")).orElse(null))
				.orElse(null);
	}

}
