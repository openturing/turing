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
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.auth.TurPrivilege;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;

@Service("customUserDetailsService")
public class TurCustomUserDetailsService implements UserDetailsService {

    private final TurUserRepository turUserRepository;
    private final TurRoleRepository turRoleRepository;
    private final TurGroupRepository turGroupRepository;

    @Inject
    public TurCustomUserDetailsService(TurUserRepository turUserRepository,
                                       TurRoleRepository turRoleRepository,
                                       TurGroupRepository turGroupRepository) {
        this.turUserRepository = turUserRepository;
        this.turRoleRepository = turRoleRepository;
        this.turGroupRepository = turGroupRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        TurUser turUser = turUserRepository.findByUsername(username);
        if (null == turUser) {
            throw new UsernameNotFoundException("No user present with username: " + username);
        } else {
            List<TurUser> users = new ArrayList<>();
            users.add(turUser);
            Set<TurGroup> turGroups = turGroupRepository.findByTurUsersIn(users);
            Set<TurRole> turRoles = turRoleRepository.findByTurGroupsIn(turGroups);

            List<String> roles = new ArrayList<>();
            for (TurRole turRole : turRoles) {
                roles.add(turRole.getName());
            }
            return new TurCustomUserDetails(turUser, roles);
        }
    }

    private Collection<? extends GrantedAuthority> getAuthorities(
            Collection<TurRole> turRoles) {

        return getGrantedAuthorities(getPrivileges(turRoles));
    }

    private List<String> getPrivileges(Collection<TurRole> turRoles) {

        List<String> privileges = new ArrayList<>();
        List<TurPrivilege> collection = new ArrayList<>();
        for (TurRole turRole : turRoles) {
            privileges.add(turRole.getName());
            collection.addAll(turRole.getTurPrivileges());
        }
        for (TurPrivilege item : collection) {
            privileges.add(item.getName());
        }
        return privileges;
    }

    private List<GrantedAuthority> getGrantedAuthorities(List<String> privileges) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String privilege : privileges) {
            authorities.add(new SimpleGrantedAuthority(privilege));
        }
        return authorities;
    }

}
