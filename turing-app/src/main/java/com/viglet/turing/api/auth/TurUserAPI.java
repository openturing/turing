/*
 * Copyright (C) 2016-2024 the original author or authors.
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

package com.viglet.turing.api.auth;

import com.google.inject.Inject;
import com.viglet.turing.bean.converse.auth.TurCurrentUser;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Alexandre Oliveira
 *
 * @since 0.3.2
 */
@RestController
@RequestMapping("/api/v2/user")
@Tag(name = "User", description = "User API")
public class TurUserAPI {

    public static final String ADMIN = "admin";
    public static final String ADMINISTRATOR = "Administrator";
    public static final String PREFERRED_USERNAME = "preferred_username";
    public static final String GIVEN_NAME = "given_name";
    public static final String FAMILY_NAME = "family_name";
    private final PasswordEncoder passwordEncoder;
    private final TurUserRepository turUserRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurConfigProperties turConfigProperties;

    @Inject
    public TurUserAPI(PasswordEncoder passwordEncoder, TurUserRepository turUserRepository,
                      TurGroupRepository turGroupRepository,
                      TurConfigProperties turConfigProperties) {
        this.passwordEncoder = passwordEncoder;
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turConfigProperties = turConfigProperties;
    }

    @GetMapping
    public List<TurUser> turUserList() {
        return turUserRepository.findAll();
    }

    @GetMapping("/current")
    public TurCurrentUser turUserCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AnonymousAuthenticationToken)) {
            if (turConfigProperties.isKeycloak()) {
                return keycloakUser();
            } else {
                return regularUser(authentication.getName());
            }
        }
        return null;
    }

    private TurCurrentUser regularUser(String currentUserName) {
        boolean isAdmin = false;
        TurUser turUser = turUserRepository.findByUsername(currentUserName);
        turUser.setPassword(null);
        if (turUser.getTurGroups() != null) {
            for (TurGroup turGroup : turUser.getTurGroups()) {
                if (turGroup.getName().equals(ADMINISTRATOR)) {
                    isAdmin = true;
                    break;
                }
            }
        }
        TurCurrentUser turCurrentUser = new TurCurrentUser();
        turCurrentUser.setUsername(turUser.getUsername());
        turCurrentUser.setFirstName(turUser.getFirstName());
        turCurrentUser.setLastName(turUser.getLastName());
        turCurrentUser.setAdmin(isAdmin);
        return turCurrentUser;
    }

    private static TurCurrentUser keycloakUser() {
        OAuth2User user = ((OAuth2User) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        TurCurrentUser turCurrentUser = new TurCurrentUser();
        turCurrentUser.setUsername(user.getAttribute(PREFERRED_USERNAME));
        turCurrentUser.setFirstName(user.getAttribute(GIVEN_NAME));
        turCurrentUser.setLastName(user.getAttribute(FAMILY_NAME));
        turCurrentUser.setAdmin(true);
        return turCurrentUser;
    }

    @GetMapping("/{username}")
    public TurUser turUserEdit(@PathVariable String username) {
        TurUser turUser = turUserRepository.findByUsername(username);
        return Optional.ofNullable(turUser).map(user -> {
            user.setPassword(null);
            List<TurUser> turUsers = new ArrayList<>();
            turUsers.add(user);
            user.setTurGroups(turGroupRepository.findByTurUsersIn(turUsers));
            return user;
        }).orElseGet(TurUser::new);
    }

    @PutMapping("/{username}")
    public TurUser turUserUpdate(@PathVariable String username, @RequestBody TurUser turUser) {
        TurUser turUserEdit = turUserRepository.findByUsername(username);
        return Optional.ofNullable(turUserEdit).map(userEdit -> {
            userEdit.setFirstName(turUser.getFirstName());
            userEdit.setLastName(turUser.getLastName());
            userEdit.setEmail(turUser.getEmail());
            if (turUser.getPassword() != null && !turUser.getPassword().trim().isEmpty()) {
                userEdit.setPassword(passwordEncoder.encode(turUser.getPassword()));
            }
            userEdit.setTurGroups(turUser.getTurGroups());
            turUserRepository.save(turUserEdit);
            return userEdit;
        }).orElseGet(TurUser::new);
    }

    @Transactional
    @DeleteMapping("/{username}")
    public boolean turUserDelete(@PathVariable String username) {
        if (!username.equalsIgnoreCase(ADMIN)) {
            turUserRepository.deleteByUsername(username);
            return true;
        }
        else {
            return false;
        }
    }

    @PostMapping
    public TurUser turUserAdd(@RequestBody TurUser turUser) {
        if (turUser.getPassword() != null && !turUser.getPassword().trim().isEmpty()) {
            turUser.setPassword(passwordEncoder.encode(turUser.getPassword()));
            turUserRepository.save(turUser);
        }
        return turUser;
    }

    @GetMapping("/model")
    public TurUser turUserStructure() {
        return new TurUser();

    }

}
