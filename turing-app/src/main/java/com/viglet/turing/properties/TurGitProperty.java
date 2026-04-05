package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Git server configuration properties.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
public class TurGitProperty {
    /** Whether the embedded Git HTTP server is enabled. */
    private boolean server = false;
}
