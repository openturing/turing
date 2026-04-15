/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.service.keycloak;

import com.viglet.turing.properties.TurConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Calls the Keycloak Admin REST API to list users and groups for the
 * realm configured under
 * {@code spring.security.oauth2.client.provider.keycloak.issuer-uri}.
 *
 * <p>Authentication is delegated to the currently logged-in user's OIDC
 * access token, which must carry sufficient permissions in Keycloak
 * (typically a {@code realm-management} role such as {@code view-users} /
 * {@code query-groups}). When the user is not authenticated via Keycloak
 * this service throws {@link IllegalStateException}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Service
public class TurKeycloakAdminService {

    private final TurConfigProperties turConfigProperties;
    private final Optional<OAuth2AuthorizedClientService> authorizedClientService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String issuerUri;

    public TurKeycloakAdminService(TurConfigProperties turConfigProperties,
                                   Optional<OAuth2AuthorizedClientService> authorizedClientService,
                                   @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:}") String issuerUri) {
        this.turConfigProperties = turConfigProperties;
        this.authorizedClientService = authorizedClientService;
        this.issuerUri = issuerUri;
    }

    /**
     * @return {@code true} when Keycloak is the active authentication
     *         provider and a usable issuer URI is configured.
     */
    public boolean isEnabled() {
        return turConfigProperties.isKeycloak() && StringUtils.hasText(issuerUri);
    }

    public List<TurKeycloakUserDto> listUsers() {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/users")
                .queryParam("max", 200)
                .queryParam("briefRepresentation", true)
                .build()
                .encode()
                .toUri();
        TurKeycloakUserDto[] users = exchange(uri, HttpMethod.GET, TurKeycloakUserDto[].class);
        return users == null ? Collections.emptyList() : Arrays.asList(users);
    }

    public Optional<TurKeycloakUserDto> findUserByUsername(String username) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/users")
                .queryParam("username", username)
                .queryParam("exact", true)
                .build()
                .encode()
                .toUri();
        TurKeycloakUserDto[] users = exchange(uri, HttpMethod.GET, TurKeycloakUserDto[].class);
        if (users == null || users.length == 0) {
            return Optional.empty();
        }
        TurKeycloakUserDto base = users[0];
        List<String> groups = listUserGroupNames(base.id());
        return Optional.of(new TurKeycloakUserDto(
                base.id(), base.username(), base.firstName(), base.lastName(), base.email(),
                base.enabled(), base.emailVerified(), base.createdTimestamp(),
                groups, base.realmRoles()));
    }

    public List<TurKeycloakGroupDto> listGroups() {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/groups")
                .queryParam("max", 200)
                .build()
                .encode()
                .toUri();
        TurKeycloakGroupDto[] groups = exchange(uri, HttpMethod.GET, TurKeycloakGroupDto[].class);
        return groups == null ? Collections.emptyList() : Arrays.asList(groups);
    }

    public Optional<TurKeycloakGroupDto> findGroupById(String id) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/groups/{id}")
                .build()
                .expand(id)
                .encode()
                .toUri();
        try {
            TurKeycloakGroupDto group = exchange(uri, HttpMethod.GET, TurKeycloakGroupDto.class);
            if (group == null) {
                return Optional.empty();
            }
            return Optional.of(group.withMembers(listGroupMembers(id)));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private List<TurKeycloakUserDto> listGroupMembers(String groupId) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/groups/{id}/members")
                .queryParam("max", 200)
                .build()
                .expand(groupId)
                .encode()
                .toUri();
        TurKeycloakUserDto[] members = exchange(uri, HttpMethod.GET, TurKeycloakUserDto[].class);
        return members == null ? Collections.emptyList() : Arrays.asList(members);
    }

    private List<String> listUserGroupNames(String userId) {
        URI uri = UriComponentsBuilder.fromUriString(adminBaseUrl())
                .path("/users/{id}/groups")
                .build()
                .expand(userId)
                .encode()
                .toUri();
        TurKeycloakGroupDto[] groups = exchange(uri, HttpMethod.GET, TurKeycloakGroupDto[].class);
        if (groups == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(groups).map(TurKeycloakGroupDto::name).toList();
    }

    private <T> T exchange(URI uri, HttpMethod method, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(currentAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<T> response = restTemplate.exchange(uri, method, new HttpEntity<>(headers), responseType);
        return response.getBody();
    }

    private String currentAccessToken() {
        if (authorizedClientService.isEmpty()) {
            throw new IllegalStateException("OAuth2 client is not configured");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof OAuth2AuthenticationToken oauthToken)) {
            throw new IllegalStateException("Current user is not authenticated via OAuth2");
        }
        OAuth2AuthorizedClient client = authorizedClientService.get()
                .loadAuthorizedClient(oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No OAuth2 access token available for current user");
        }
        return client.getAccessToken().getTokenValue();
    }

    /**
     * Derives the Keycloak Admin REST base URL from the configured
     * {@code issuer-uri}. Preserves any context path that precedes the
     * {@code /realms/} segment so Keycloak deployments behind a reverse
     * proxy with a relative path (e.g. {@code KC_HTTP_RELATIVE_PATH=/kc})
     * resolve correctly. Examples:
     * <ul>
     *   <li>{@code http://host:8080/realms/demo} → {@code http://host:8080/admin/realms/demo}</li>
     *   <li>{@code http://host/kc/realms/viglet} → {@code http://host/kc/admin/realms/viglet}</li>
     * </ul>
     */
    private String adminBaseUrl() {
        if (!StringUtils.hasText(issuerUri)) {
            throw new IllegalStateException("spring.security.oauth2.client.provider.keycloak.issuer-uri is not configured");
        }
        URI uri = URI.create(issuerUri);
        String path = uri.getPath() == null ? "" : uri.getPath();
        int realmsIdx = path.indexOf("/realms/");
        if (realmsIdx < 0) {
            throw new IllegalStateException("issuer-uri does not contain '/realms/<name>': " + issuerUri);
        }
        String basePath = path.substring(0, realmsIdx);
        String realm = path.substring(realmsIdx + "/realms/".length());
        if (realm.endsWith("/")) {
            realm = realm.substring(0, realm.length() - 1);
        }
        return uri.getScheme() + "://" + uri.getAuthority() + basePath + "/admin/realms/" + realm;
    }
}
