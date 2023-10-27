package com.viglet.turing.properties;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties("turing")
public class TurConfigProperties {

	private boolean multiTenant;
	private boolean keycloak;
	private String allowedOrigins;
	
	private TurSolrProperty solr;

	public void setAllowedOrigins(String allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public void setSolr(TurSolrProperty solr) {
		this.solr = solr;
	}

	public void setMultiTenant(boolean multiTenant) {
		this.multiTenant = multiTenant;
	}

	public void setKeycloak(boolean keycloak) {
		this.keycloak = keycloak;
	}
}
