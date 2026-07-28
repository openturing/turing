package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Local {@code admin} account bootstrap properties.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurAdminProperty {
    /**
     * Headless bootstrap for the local {@code admin} password (Docker/CI), so a
     * fresh container is usable without the first-access setup screen. Env:
     * {@code TURING_ADMIN_PASSWORD}. Applied on startup ONLY while the password
     * is still unset — it never overwrites a password already configured. Same
     * rule as the setup screen: minimum 6 characters (shorter values are ignored
     * with a warning). Blank/unset (the default) keeps the interactive setup.
     */
    private String password;
}
