/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * T246 / §XIII.6 — maps a Keycloak-issued JWT to Spring Security authorities for
 * the {@code /mcp} resource-server gate.
 *
 * <p>Two authority sources are merged:
 * <ul>
 *   <li><b>OAuth2 scopes</b> — the standard {@code scope}/{@code scp} claim, via
 *       {@link JwtGrantedAuthoritiesConverter}, mapped to {@code SCOPE_*}
 *       authorities. The write-tool gate ({@code SCOPE_mcp:write}) keys off
 *       these.</li>
 *   <li><b>Keycloak realm roles</b> — {@code realm_access.roles}, mapped to
 *       {@code ROLE_*} authorities, so the same roles the rest of Turing uses
 *       are visible to MCP scoping and to {@code TurTenantResolutionFilter}.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurMcpJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(scopesConverter.convert(jwt));
        authorities.addAll(realmRoles(jwt));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    private List<GrantedAuthority> realmRoles(Jwt jwt) {
        List<GrantedAuthority> roles = new ArrayList<>();
        Object realmAccess = jwt.getClaim(REALM_ACCESS);
        if (realmAccess instanceof Map<?, ?> map && map.get(ROLES) instanceof Collection<?> roleNames) {
            for (Object role : roleNames) {
                if (role != null) {
                    roles.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
                }
            }
        }
        return roles;
    }
}
