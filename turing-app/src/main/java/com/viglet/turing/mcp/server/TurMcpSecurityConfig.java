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

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.tenant.TurTenantResolutionFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * T246 / §XIII.6 — the OAuth 2.1 resource-server trust boundary for {@code /mcp}.
 *
 * <p>A dedicated, high-priority {@link SecurityFilterChain} that owns the
 * {@code /mcp} path exclusively (via {@code securityMatcher}), so the MCP
 * endpoint's auth posture is decided here rather than tangled into the main
 * application chain. It exists only when the MCP server is enabled
 * ({@code spring.ai.mcp.server.enabled=true}).
 *
 * <p>Behaviour is driven by {@code turing.mcp-server.require-auth}:
 * <ul>
 *   <li><b>false</b> (default, the T245 dev posture): the path is permitted and
 *       the only gate is {@link TurMcpLoopbackFilter}. Suitable for a local
 *       Claude Desktop/Code pointed at loopback.</li>
 *   <li><b>true</b> (remote posture): every request must present a valid
 *       Keycloak-issued JWT bearer token, validated by the configured
 *       {@link JwtDecoder} and mapped to authorities by
 *       {@link TurMcpJwtAuthenticationConverter}. <b>Fails closed</b>: if no
 *       {@code JwtDecoder} is configured (and API-key auth is off), every request
 *       is denied so a mis-provisioned deployment can never serve the catalog
 *       unauthenticated.</li>
 * </ul>
 *
 * <p>T146 / §X.5.c — the <b>public consumption surface</b>. When
 * {@code turing.mcp-server.api-key-enabled=true} (with {@code require-auth=true}),
 * a Turing API key ({@code TurDevToken}) in the {@code Key} header is accepted
 * <em>alongside</em> OAuth via {@link TurMcpApiKeyAuthenticationFilter}, so an
 * external MCP client (Claude Desktop, Cursor, an OpenAI Responses agent) can use
 * a static key. API-key principals get read-only access; the gated write tools
 * still require an OAuth token holding {@code write-scope}. API key and OAuth
 * coexist because each uses its own header ({@code Key} vs
 * {@code Authorization: Bearer}).
 *
 * <p>The chain is {@code STATELESS} (MCP is not a browser/cookie surface, so CSRF
 * is disabled) and re-runs {@link TurTenantResolutionFilter} so that — per T282 —
 * an MCP call inherits the same tenant binding as the rest of the platform once
 * the principal is known.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Configuration
@Profile("production")
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class TurMcpSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http,
            TurConfigProperties configProperties,
            TurTenantResolutionFilter turTenantResolutionFilter,
            TurMcpJwtAuthenticationConverter jwtAuthenticationConverter,
            ObjectProvider<JwtDecoder> jwtDecoderProvider,
            TurDevTokenRepository turDevTokenRepository) throws Exception {

        http.securityMatcher("/mcp", "/mcp/**");
        http.cors(Customizer.withDefaults());
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        // Keep tenant binding on the MCP path (T282). Runs after the
        // authentication filters (which precede AuthorizationFilter), so the
        // principal is resolved by the time the tenant is bound.
        http.addFilterBefore(turTenantResolutionFilter, AuthorizationFilter.class);

        boolean requireAuth = configProperties.getMcpServer().isRequireAuth();
        if (!requireAuth) {
            log.info("[MCP] /mcp security: require-auth=false — relying on the loopback gate (T245)");
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        // T146 / §X.5.c — the public consumption surface. An API key (TurDevToken
        // in the `Key` header) is accepted alongside OAuth so external MCP clients
        // can use a static credential; the filter runs before AuthorizationFilter
        // so a key-authenticated request satisfies anyRequest().authenticated().
        // It grants read-only access only — write tools stay OAuth-scope-gated.
        boolean apiKeyEnabled = configProperties.getMcpServer().isApiKeyEnabled();
        if (apiKeyEnabled) {
            http.addFilterBefore(new TurMcpApiKeyAuthenticationFilter(turDevTokenRepository),
                    AuthorizationFilter.class);
        }

        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
        if (jwtDecoder == null) {
            if (!apiKeyEnabled) {
                log.error("[MCP] /mcp security: require-auth=true but no JwtDecoder is configured "
                        + "(set spring.security.oauth2.resourceserver.jwt.*) and api-key-enabled=false. "
                        + "Failing closed — denying all /mcp requests.");
                http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
                return http.build();
            }
            log.info("[MCP] /mcp security: require-auth=true, api-key-enabled=true, no JwtDecoder — "
                    + "API-key (Key header) is the only accepted credential");
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
            return http.build();
        }

        log.info("[MCP] /mcp security: require-auth=true — OAuth 2.1 resource server (JWT bearer){} enabled",
                apiKeyEnabled ? " + API-key (Key header)" : "");
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        return http.build();
    }
}
