package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Marketplace configuration properties.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
public class TurMarketplaceProperty {
    /** Whether the Marketplace feature is enabled. */
    private boolean enabled = false;
    /** External URL that serves the marketplace catalog JSON. */
    private String url = "https://turing.viglet.org/marketplace/marketplace.json";
}
