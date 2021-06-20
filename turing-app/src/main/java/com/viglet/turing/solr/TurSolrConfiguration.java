package com.viglet.turing.solr;

import java.util.concurrent.TimeUnit;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpClientUtil.SchemaRegistryProvider;
import org.apache.solr.client.solrj.impl.SolrHttpRequestRetryHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TurSolrConfiguration {

	@Bean
	public CloseableHttpClient closeableHttpClient() {
		SchemaRegistryProvider schemaRegistry = HttpClientUtil.getSchemaRegisteryProvider();
		
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(schemaRegistry.getSchemaRegistry());
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
