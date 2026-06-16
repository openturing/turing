/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parses Keycloak/OIDC token claims that Spring Security does not map to
 * authorities out of the box.
 *
 * <p>The reusable kernel of the "JWT/authority resolution" duplication win
 * (Block Q / T369): how a product turns realm roles into application authorities
 * is product-specific (it binds to that product's role model), but the act of
 * <em>reading {@code realm_access.roles} out of the claims map</em> is identical
 * everywhere and carries no product knowledge.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletKeycloakClaims {

    private VigletKeycloakClaims() {
    }

    /**
     * Extract the Keycloak realm roles from a claims/attributes map: the
     * {@code realm_access.roles} array Keycloak puts in the token but which
     * Spring Security does not map to authorities by default. Returns an empty
     * set when the claim is absent or malformed. Works for both the OIDC
     * ({@code OidcUser.getClaims()}) and OAuth2 ({@code OAuth2User.getAttributes()})
     * paths.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> extractRealmRoles(Map<String, Object> claims) {
        Set<String> roles = new HashSet<>();
        if (claims == null) {
            return roles;
        }
        if (claims.get("realm_access") instanceof Map<?, ?> realmAccess
                && ((Map<String, Object>) realmAccess).get("roles") instanceof Collection<?> roleList) {
            for (Object role : roleList) {
                if (role != null) {
                    roles.add(role.toString());
                }
            }
        }
        return roles;
    }
}
