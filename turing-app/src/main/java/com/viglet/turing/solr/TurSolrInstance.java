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
package com.viglet.turing.solr;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;

import jakarta.annotation.PreDestroy;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;

public class TurSolrInstance {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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
