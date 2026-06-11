/*
 * Copyright (C) 2016-2023 the original author or authors.
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
package com.viglet.turing.api.discovery;

import org.json.JSONException;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.TurAPIBean;
import com.viglet.turing.properties.TurConfigProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/discovery")
public class TurDiscoveryAPI {
    private final TurAPIBean turAPIBean;
    private final TurConfigProperties turConfigProperties;
    private final Optional<ClientRegistrationRepository> clientRegistrationRepository;

    public TurDiscoveryAPI(TurAPIBean turAPIBean, TurConfigProperties turConfigProperties,
                           Optional<ClientRegistrationRepository> clientRegistrationRepository) {
        this.turAPIBean = turAPIBean;
        this.turConfigProperties = turConfigProperties;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @GetMapping
    public TurAPIBean info() throws JSONException {
        turAPIBean.setProduct("Viglet Turing");
        turAPIBean.setKeycloak(turConfigProperties.isKeycloak());
        // T262: legacy multi-tenant flag retired — discovery now reflects tenancy.enabled.
        turAPIBean.setMultiTenant(turConfigProperties.getTenancy().isEnabled());
        turAPIBean.setAuthThirdparty(turConfigProperties.getAuthentication() == null
                || turConfigProperties.getAuthentication().isThirdparty());
        turAPIBean.setSelfRegistration(turConfigProperties.getAuthentication() != null
                && turConfigProperties.getAuthentication().isNewUser());
        turAPIBean.setOauth2Providers(resolveOAuth2Providers());
        return turAPIBean;
    }

    private List<String> resolveOAuth2Providers() {
        if (clientRegistrationRepository.isEmpty()) {
            return List.of();
        }
        var repo = clientRegistrationRepository.get();
        if (repo instanceof Iterable<?> iterable) {
            List<String> providers = new ArrayList<>();
            for (Object obj : iterable) {
                if (obj instanceof ClientRegistration registration) {
                    providers.add(registration.getRegistrationId());
                }
            }
            return providers;
        }
        return List.of();
    }
}
