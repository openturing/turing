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
package com.viglet.turing.onstartup.auth;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ensures the Keycloak admin user (configured via turing.keycloak-admin-id) has the
 * Administrator group on every startup, plus any users listed in
 * turing.admin-emails (Client Silos — a dedicated silo grants its client's
 * operators full admin without hand-editing the DB).
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Slf4j
@Component
@Transactional
public class TurKeycloakAdminOnStartup implements ApplicationRunner {

    private static final String ADMINISTRATOR = "Administrator";

    private final TurConfigProperties turConfigProperties;
    private final TurUserRepository turUserRepository;
    private final TurGroupRepository turGroupRepository;

    public TurKeycloakAdminOnStartup(TurConfigProperties turConfigProperties,
                                     TurUserRepository turUserRepository,
                                     TurGroupRepository turGroupRepository) {
        this.turConfigProperties = turConfigProperties;
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!turConfigProperties.isKeycloak()) return;

        TurGroup adminGroup = turGroupRepository.findByName(ADMINISTRATOR);
        if (adminGroup == null) {
            log.warn("Administrator group not found. Run initial setup first.");
            return;
        }

        // Every admin identity to seed: the bootstrap admin + the Client Silos
        // admin-emails (deduped, order-preserving; blanks ignored).
        Set<String> adminIds = new LinkedHashSet<>();
        if (StringUtils.hasText(turConfigProperties.getKeycloakAdminId())) {
            adminIds.add(turConfigProperties.getKeycloakAdminId());
        }
        if (StringUtils.hasText(turConfigProperties.getAdminEmails())) {
            Arrays.stream(turConfigProperties.getAdminEmails().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(adminIds::add);
        }

        for (String adminId : adminIds) {
            ensureAdministrator(adminId, adminGroup);
        }
    }

    /** Idempotently ensure {@code username} exists and is in the Administrator group. */
    private void ensureAdministrator(String username, TurGroup adminGroup) {
        TurUser user = turUserRepository.findByUsername(username);
        if (user == null) {
            user = TurUser.builder()
                    .username(username)
                    .firstName("Keycloak")
                    .lastName("Admin")
                    .realm("keycloak")
                    .enabled(1)
                    .turGroups(Collections.singletonList(adminGroup))
                    .build();
            turUserRepository.save(user);
            log.info("Created Keycloak admin user '{}' with Administrator group.", username);
        } else {
            var groups = turGroupRepository.findByTurUsersContaining(user);
            boolean hasAdmin = groups.stream().anyMatch(g -> ADMINISTRATOR.equals(g.getName()));
            if (!hasAdmin) {
                var groupList = new ArrayList<>(groups);
                groupList.add(adminGroup);
                user.setTurGroups(groupList);
                turUserRepository.save(user);
                log.info("Added Administrator group to Keycloak user '{}'.", username);
            }
        }
    }
}
