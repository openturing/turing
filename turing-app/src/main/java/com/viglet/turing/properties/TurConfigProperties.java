package com.viglet.turing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("turing")
public class TurConfigProperties {

	private boolean cloud;
	
	private String allowedOrigins;
	
	private TurSolrProperty solr;

	public boolean isCloud() {
		return cloud;
	}

	public String getAllowedOrigins() {
		return allowedOrigins;
	}

	public TurSolrProperty getSolr() {
		return solr;
	}

	public void setCloud(boolean cloud) {
		this.cloud = cloud;
	}

	public void setAllowedOrigins(String allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public void setSolr(TurSolrProperty solr) {
		this.solr = solr;
	}
	
	
	
}
