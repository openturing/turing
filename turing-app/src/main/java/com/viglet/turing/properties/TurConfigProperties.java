package com.viglet.turing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("turing")
public class TurConfigProperties {

	private boolean multiTenant;
	private boolean keycloak;
	private String allowedOrigins;
	
	private TurSolrProperty solr;

	public String getAllowedOrigins() {
		return allowedOrigins;
	}

	public TurSolrProperty getSolr() {
		return solr;
	}

	public void setAllowedOrigins(String allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public void setSolr(TurSolrProperty solr) {
		this.solr = solr;
	}

	public boolean isMultiTenant() {
		return multiTenant;
	}

	public void setMultiTenant(boolean multiTenant) {
		this.multiTenant = multiTenant;
	}

	public boolean isKeycloak() {
		return keycloak;
	}

	public void setKeycloak(boolean keycloak) {
		this.keycloak = keycloak;
	}
}
