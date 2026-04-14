package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Authentication configuration properties.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
public class TurAuthenticationProperty {
    private boolean thirdparty = true;
    private boolean newUser = false;
}
