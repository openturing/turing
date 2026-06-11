/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.spring.security;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

@Service("customUserDetailsService")
public class TurCustomUserDetailsService implements UserDetailsService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private final TurUserRepository turUserRepository;
    private final TurRoleRepository turRoleRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurPrivilegeRepository turPrivilegeRepository;
    private final TurAuthorityResolver turAuthorityResolver;
    private final TurConfigProperties turConfigProperties;

    public TurCustomUserDetailsService(TurUserRepository turUserRepository,
            TurRoleRepository turRoleRepository,
            TurGroupRepository turGroupRepository,
            TurPrivilegeRepository turPrivilegeRepository,
            TurAuthorityResolver turAuthorityResolver,
            TurConfigProperties turConfigProperties) {
        this.turUserRepository = turUserRepository;
        this.turRoleRepository = turRoleRepository;
        this.turGroupRepository = turGroupRepository;
        this.turPrivilegeRepository = turPrivilegeRepository;
        this.turAuthorityResolver = turAuthorityResolver;
        this.turConfigProperties = turConfigProperties;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        TurUser turUser = turUserRepository.findByUsername(username);
        if (null == turUser) {
            throw new UsernameNotFoundException("No user present with username: " + username);
        } else {
            List<String> authorities;
            if (turConfigProperties.isPermissions()) {
                authorities = resolveAuthorities(turUser);
            } else {
                authorities = allAuthorities();
            }
            // T263 / §XIV.3.1 — append per-tenant membership authorities (no-op
            // when tenancy is off) so the session path mirrors the OIDC path.
            authorities.addAll(turAuthorityResolver.tenantAuthorities(username));
            return new TurCustomUserDetails(turUser, authorities);
        }
    }

    private List<String> resolveAuthorities(TurUser turUser) {
        Set<TurGroup> turGroups = turGroupRepository.findByTurUsersContaining(turUser);
        Set<TurRole> turRoles = new HashSet<>();
        for (TurGroup turGroup : turGroups) {
            turRoles.addAll(turRoleRepository.findByTurGroupsContaining(turGroup));
        }
        List<String> authorities = new ArrayList<>();
        for (TurRole turRole : turRoles) {
            authorities.add(turRole.getName());
            for (var privilege : turRole.getTurPrivileges()) {
                authorities.add(privilege.getName());
            }
        }
        return authorities;
    }

    private List<String> allAuthorities() {
        List<String> authorities = new ArrayList<>();
        authorities.add(ROLE_ADMIN);
        turPrivilegeRepository.findAll()
                .forEach(p -> authorities.add(p.getName()));
        return authorities;
    }
}
