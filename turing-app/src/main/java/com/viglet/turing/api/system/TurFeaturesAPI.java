package com.viglet.turing.api.system;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.git.TurGitServerService;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.solr.source.TurSolrInstanceSource;
import com.viglet.turing.system.TurGlobalSettingsService;
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
    private final String loggingEngine;

    public TurFeaturesAPI(TurStorageService storageService, TurGlobalSettingsService globalSettingsService,
                          TurGitServerService gitServerService, TurConfigProperties configProperties,
                          TurSolrInstanceSource solrInstanceSource,
                          @Value("${turing.logging.engine:none}") String loggingEngine) {
        this.storageService = storageService;
        this.globalSettingsService = globalSettingsService;
        this.gitServerService = gitServerService;
        this.configProperties = configProperties;
        this.solrInstanceSource = solrInstanceSource;
        this.loggingEngine = loggingEngine;
    }

    public record FeaturesResponse(boolean storageEnabled, boolean ragEnabled, boolean gitServerEnabled,
                                   boolean marketplaceEnabled, boolean seInstanceReadOnly,
                                   boolean skillsEnabled,
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
                loggingEngine);
    }
}
