package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties("turing")
public class TurConfigProperties {
	private boolean multiTenant;
	private boolean keycloak;
	private String allowedOrigins;
	private TurSolrProperty solr;

}
