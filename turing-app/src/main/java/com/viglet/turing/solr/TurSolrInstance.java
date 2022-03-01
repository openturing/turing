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

import java.io.IOException;
import java.net.URL;

import javax.annotation.PreDestroy;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;

public class TurSolrInstance {
	private static final Logger logger = LogManager.getLogger(TurSolrInstance.class);

	private CloseableHttpClient closeableHttpClient;

	private SolrClient solrClient = null;

	private String core = null;

	private URL solrUrl = null;
	@PreDestroy
	public void destroy() {
		if (logger.isDebugEnabled()) {
			logger.debug("TurSolrInstance destroyed");
		}
		this.close();
	}

	public void close() {
		try {
			if (solrClient != null) {
				solrClient.close();
				solrClient = null;
			}
			if (closeableHttpClient != null) {
				closeableHttpClient.close();
				closeableHttpClient = null;
			}
		} catch (IOException e) {
			logger.error(e);
		}
	}

	public TurSolrInstance(CloseableHttpClient closeableHttpClient, SolrClient solrClient, URL solrUrl,
			String core) {
		super();
		this.closeableHttpClient = closeableHttpClient;
		this.solrClient = solrClient;
		this.solrUrl = solrUrl;
		this.core = core;
	}

	public SolrClient getSolrClient() {
		return solrClient;
	}

	public void setSolrClient(SolrClient solrClient) {
		this.solrClient = solrClient;
	}

	public String getCore() {
		return core;
	}

	public void setCore(String core) {
		this.core = core;
	}

	public URL getSolrUrl() {
		return solrUrl;
	}

	public void setSolrUrl(URL solrUrl) {
		this.solrUrl = solrUrl;
	}


	
}
