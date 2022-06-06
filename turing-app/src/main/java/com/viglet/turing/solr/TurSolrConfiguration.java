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

import java.util.concurrent.TimeUnit;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpClientUtil.SocketFactoryRegistryProvider;
import org.apache.solr.client.solrj.impl.SolrHttpRequestRetryHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TurSolrConfiguration {

	@Bean
	public CloseableHttpClient closeableHttpClient() {
		SocketFactoryRegistryProvider socketFactoryRegistryProvider = HttpClientUtil.getSocketFactoryRegistryProvider();

		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
				socketFactoryRegistryProvider.getSocketFactoryRegistry());
		cm.setMaxTotal(10000);
		cm.setDefaultMaxPerRoute(10000);
		cm.setValidateAfterInactivity(3000);

		RequestConfig.Builder requestConfigBuilder = RequestConfig.custom().setConnectTimeout(30000)
				.setSocketTimeout(30000);

		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().setKeepAliveStrategy((response, context) -> -1)
				.evictIdleConnections(50000, TimeUnit.MILLISECONDS)
				.setDefaultRequestConfig(requestConfigBuilder.build())
				.setRetryHandler(new SolrHttpRequestRetryHandler(0)).disableContentCompression().useSystemProperties()
				.setConnectionManager(cm);

		return httpClientBuilder.build();

	}
}
