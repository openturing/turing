package com.viglet.turing.api.system;

import com.viglet.turing.service.git.TurGitServerService;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public TurFeaturesAPI(TurStorageService storageService, TurGlobalSettingsService globalSettingsService,
                          TurGitServerService gitServerService) {
        this.storageService = storageService;
        this.globalSettingsService = globalSettingsService;
        this.gitServerService = gitServerService;
    }

    public record FeaturesResponse(boolean storageEnabled, boolean ragEnabled, boolean gitServerEnabled) {}

    @GetMapping
    public FeaturesResponse getFeatures() {
        return new FeaturesResponse(
                storageService.isEnabled(),
                globalSettingsService.isRagEnabled(),
                gitServerService.isEnabled());
    }
}
