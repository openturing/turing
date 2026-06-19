/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.tenant;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurTenantDeprovisionProperty;
import com.viglet.turing.service.keycloak.TurKeycloakUserDto;

import lombok.extern.slf4j.Slf4j;

/**
 * T336 / §XIV.8.3 — {@link TurTenantOwnerIdentityProvider} backed by the
 * Keycloak Admin REST API using a <strong>service-account</strong>
 * (client-credentials) token.
 *
 * <p>The existing {@code TurKeycloakAdminService} delegates to the
 * <em>logged-in user's</em> OIDC token, which is unavailable in a scheduled
 * background sweep. This provider instead mints its own token from the realm's
 * token endpoint with the {@code client_credentials} grant
 * ({@code turing.tenancy.deprovision.keycloak-client-id} /
 * {@code -secret}), caching it for its lifetime. The configured client must
 * carry a {@code realm-management} role that can read users (e.g.
 * {@code view-users}).
 *
 * <p>Every failure path returns {@link TurIdentityStatus#UNKNOWN} so the sweep
 * never deletes on a guess.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurKeycloakServiceAccountIdentityProvider implements TurTenantOwnerIdentityProvider {

    // --- S1192: extracted duplicated literals ---
    private static final String REALMS = "/realms/";


    /** Refresh the cached token this far before it actually expires. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final TurConfigProperties turConfigProperties;
    private final String issuerUri;
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiry = Instant.EPOCH;

    public TurKeycloakServiceAccountIdentityProvider(TurConfigProperties turConfigProperties,
            @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:}") String issuerUri) {
        this.turConfigProperties = turConfigProperties;
        this.issuerUri = issuerUri;
    }

    @Override
    public boolean isEnabled() {
        TurTenantDeprovisionProperty cfg = turConfigProperties.getTenancy().getDeprovision();
        return turConfigProperties.isKeycloak()
                && StringUtils.hasText(issuerUri)
                && StringUtils.hasText(cfg.getKeycloakClientId())
                && StringUtils.hasText(cfg.getKeycloakClientSecret());
    }

    @Override
    public TurIdentityStatus statusOf(String username) {
        if (!StringUtils.hasText(username) || !isEnabled()) {
            return TurIdentityStatus.UNKNOWN;
        }
        try {
            String token = accessToken();
            URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                    .path("/users")
                    .queryParam("username", username)
                    .queryParam("exact", true)
                    .build()
                    .encode()
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            TurKeycloakUserDto[] users = restTemplate
                    .exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), TurKeycloakUserDto[].class)
                    .getBody();
            if (users == null || users.length == 0) {
                return TurIdentityStatus.ABSENT;
            }
            Boolean enabled = users[0].enabled();
            return Boolean.FALSE.equals(enabled) ? TurIdentityStatus.DISABLED : TurIdentityStatus.ACTIVE;
        } catch (RuntimeException e) {
            log.warn("[TenantDeprovision] Keycloak lookup failed for '{}': {}", username, e.getMessage());
            return TurIdentityStatus.UNKNOWN;
        }
    }

    /** A valid service-account token, fetched (and cached) via client-credentials. */
    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }
        TurTenantDeprovisionProperty cfg = turConfigProperties.getTenancy().getDeprovision();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", cfg.getKeycloakClientId());
        form.add("client_secret", cfg.getKeycloakClientSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = restTemplate.postForObject(tokenUrl(),
                new HttpEntity<>(form, headers), Map.class);
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("Keycloak client-credentials token response had no access_token");
        }
        cachedToken = String.valueOf(body.get("access_token"));
        long expiresIn = body.get("expires_in") instanceof Number n ? n.longValue() : 60L;
        cachedTokenExpiry = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
        return cachedToken;
    }

    private String tokenUrl() {
        return baseRealmUrl() + "/protocol/openid-connect/token";
    }

    /**
     * Keycloak Admin REST base for the realm, derived from {@code issuer-uri},
     * preserving any reverse-proxy context path before {@code /realms/}.
     * E.g. {@code http://host/kc/realms/viglet} →
     * {@code http://host/kc/admin/realms/viglet}.
     */
    private String adminBaseUrl() {
        URI uri = URI.create(issuerUri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        int realmsIdx = path.indexOf(REALMS);
        String basePath = path.substring(0, realmsIdx);
        return uri.getScheme() + "://" + uri.getAuthority() + basePath + "/admin/realms/" + realmName();
    }

    /** The realm's public base URL ({@code .../realms/<name>}), context path preserved. */
    private String baseRealmUrl() {
        if (issuerUri.endsWith("/")) {
            return issuerUri.substring(0, issuerUri.length() - 1);
        }
        return issuerUri;
    }

    private String realmName() {
        URI uri = URI.create(issuerUri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        int realmsIdx = path.indexOf(REALMS);
        String realm = path.substring(realmsIdx + REALMS.length());
        return realm.endsWith("/") ? realm.substring(0, realm.length() - 1) : realm;
    }
}
