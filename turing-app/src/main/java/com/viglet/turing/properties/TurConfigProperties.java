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
	private boolean permissions = true;
	private boolean keycloak;
	private String keycloakAdminId;
	private String allowedOrigins;
	private TurSolrProperty solr;
	private TurStorageProperty storage;
	private TurGitProperty git;
	private TurMarketplaceProperty marketplace;
	private TurAuthenticationProperty authentication;
	private TurGenAiProperty genai = new TurGenAiProperty();
	private TurChatProperty chat = new TurChatProperty();
	private TurTenancyProperty tenancy = new TurTenancyProperty();
	private TurMcpServerProperty mcpServer = new TurMcpServerProperty();

}
