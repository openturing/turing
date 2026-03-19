package com.viglet.turing.api.system;

import com.viglet.turing.service.asset.TurMinioService;
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

    private final TurMinioService minioService;
    private final TurGlobalSettingsService globalSettingsService;

    public TurFeaturesAPI(TurMinioService minioService, TurGlobalSettingsService globalSettingsService) {
        this.minioService = minioService;
        this.globalSettingsService = globalSettingsService;
    }

    public record FeaturesResponse(boolean minioEnabled, boolean ragEnabled) {}

    @GetMapping
    public FeaturesResponse getFeatures() {
        return new FeaturesResponse(minioService.isEnabled(), globalSettingsService.isRagEnabled());
    }
}
