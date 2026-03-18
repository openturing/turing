package com.viglet.turing.api.system;

import com.viglet.turing.service.asset.TurMinioService;
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

    public TurFeaturesAPI(TurMinioService minioService) {
        this.minioService = minioService;
    }

    public record FeaturesResponse(boolean minioEnabled) {}

    @GetMapping
    public FeaturesResponse getFeatures() {
        return new FeaturesResponse(minioService.isEnabled());
    }
}
