package com.viglet.turing.properties;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties("turing")
public class TurConfigProperties {
	/**
	 * When {@code false}, ANY authenticated principal is granted {@code ROLE_ADMIN}
	 * + every privilege, nullifying all method-level authorization (T642 /
	 * §XXXVII.4). The secure default is {@code true} (enforce real per-user
	 * authorities). Only disable for a deliberately trusted single-user deploy.
	 */
	private boolean permissions = true;
	private boolean keycloak;
	private String keycloakAdminId;
	private String allowedOrigins;
	/**
	 * Client Silos isolation (Viglet Cloud C051). When set (e.g. {@code acme}),
	 * this instance is a dedicated per-client silo and only admits tokens whose
	 * {@code viglet_client} claim equals this value — a login/token for any other
	 * client (or with no client claim) is rejected. Blank/unset (the default) =
	 * shared instance, no restriction. Env: {@code TURING_REQUIRED_VIGLET_CLIENT}.
	 */
	private String requiredVigletClient;
	/**
	 * Client Silos (Viglet Cloud). Comma-separated list of usernames/e-mails
	 * granted full admin (the {@code Administrator} group → {@code ROLE_ADMIN} +
	 * all privileges) on this instance, seeded on startup exactly like
	 * {@link #keycloakAdminId}. Lets a dedicated silo give its client's operators
	 * total autonomy without hand-editing the DB. Matches the login username
	 * (which is the e-mail when {@code registrationEmailAsUsername} is on).
	 * Env: {@code TURING_ADMIN_EMAILS}.
	 */
	private String adminEmails;
	private TurSolrProperty solr;
	private TurStorageProperty storage;
	private TurGitProperty git;
	private TurMarketplaceProperty marketplace;
	private TurAuthenticationProperty authentication;
	private TurGenAiProperty genai = new TurGenAiProperty();
	private TurChatProperty chat = new TurChatProperty();
	private TurTenancyProperty tenancy = new TurTenancyProperty();
	private TurMcpServerProperty mcpServer = new TurMcpServerProperty();
	private TurObservabilityProperty observability = new TurObservabilityProperty();
	private TurBatchProperty batch = new TurBatchProperty();
	private TurEvalsProperty evals = new TurEvalsProperty();
	private TurDistillationProperty distillation = new TurDistillationProperty();
	private TurOcrProperty ocr = new TurOcrProperty();
	private TurTranscriptionProperty transcription = new TurTranscriptionProperty();
	private TurUrlFetchProperty urlFetch = new TurUrlFetchProperty();

	/**
	 * T642 / §XXXVII.4 — loudly flag the permissive mode at startup so an operator
	 * who disabled permissions knows every authenticated user is an admin.
	 */
	@PostConstruct
	void warnIfPermissionsDisabled() {
		if (!permissions) {
			log.warn("SECURITY: turing.permissions=false — every authenticated principal is granted "
					+ "ROLE_ADMIN and all privileges (method-level authorization is NOT enforced). "
					+ "Set turing.permissions=true unless this is a deliberately trusted single-user deployment.");
		}
	}

}
