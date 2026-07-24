package com.viglet.turing.api.system;

import com.viglet.turing.genai.TurDefaultAgentResolver;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.git.TurGitServerService;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.solr.source.TurSolrInstanceSource;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.tenant.TurPlatformAdminService;
import com.viglet.turing.tenant.TurTenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes feature flags to the frontend so the UI can conditionally show/hide sections.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@RestController
@RequestMapping("/api/features")
@Tag(name = "Features", description = "Feature flags API")
public class TurFeaturesAPI {

    private final TurStorageService storageService;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurGitServerService gitServerService;
    private final TurConfigProperties configProperties;
    private final TurSolrInstanceSource solrInstanceSource;
    private final TurTenantContext tenantContext;
    private final TurPlatformAdminService platformAdminService;
    private final TurDefaultAgentResolver defaultAgentResolver;
    private final String loggingEngine;

    public TurFeaturesAPI(TurStorageService storageService, TurGlobalSettingsService globalSettingsService,
                          TurGitServerService gitServerService, TurConfigProperties configProperties,
                          TurSolrInstanceSource solrInstanceSource,
                          TurTenantContext tenantContext,
                          TurPlatformAdminService platformAdminService,
                          TurDefaultAgentResolver defaultAgentResolver,
                          @Value("${turing.logging.engine:none}") String loggingEngine) {
        this.storageService = storageService;
        this.globalSettingsService = globalSettingsService;
        this.gitServerService = gitServerService;
        this.configProperties = configProperties;
        this.solrInstanceSource = solrInstanceSource;
        this.tenantContext = tenantContext;
        this.platformAdminService = platformAdminService;
        this.defaultAgentResolver = defaultAgentResolver;
        this.loggingEngine = loggingEngine;
    }

    public record FeaturesResponse(boolean storageEnabled, boolean ragEnabled, boolean gitServerEnabled,
                                   boolean marketplaceEnabled, boolean seInstanceReadOnly,
                                   boolean skillsEnabled,
                                   boolean tenancyEnabled, boolean platformAdmin,
                                   boolean defaultAiAgentRagEnabled,
                                   String loggingEngine) {}

    @GetMapping
    public FeaturesResponse getFeatures() {
        var marketplace = configProperties.getMarketplace();
        boolean marketplaceEnabled = marketplace != null && marketplace.isEnabled();
        // T317 — skills live as folders in object storage, so the feature is
        // gated on storage being configured (same gate as /admin/assets).
        boolean skillsEnabled = storageService.isEnabled();
        return new FeaturesResponse(
                storageService.isEnabled(),
                globalSettingsService.isRagEnabled(),
                gitServerService.isEnabled(),
                marketplaceEnabled,
                solrInstanceSource != null && solrInstanceSource.isReadOnly(),
                skillsEnabled,
                // T372 — the frontend's useInfraReadOnly hook freezes GLOBAL
                // BYO-infra rows when tenancy is on and the caller is not a
                // platform admin (mirrors the backend assertWritable rule).
                tenantContext.isTenancyEnabled(),
                platformAdminService.isPlatformAdmin(),
                // T622 — a global Default AI Agent that is RAG-ready lets SN
                // sites with no agent of their own still offer ANN search; the
                // admin launch-bar chip reads this to decide visibility.
                defaultAgentResolver.isDefaultRagReady(),
                loggingEngine);
    }
}
