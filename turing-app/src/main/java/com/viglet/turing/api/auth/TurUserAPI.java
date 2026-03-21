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

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.bean.TurCurrentUser;
import com.viglet.turing.persistence.dto.auth.TurUserDto;
import com.viglet.turing.persistence.mapper.auth.TurUserMapper;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import com.viglet.turing.properties.TurConfigProperties;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 *
 * @since 0.3.2
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/user")
@Tag(name = "User", description = "User API")
public class TurUserAPI {

    private static final String ADMIN = "admin";
    private static final String ADMINISTRATOR = "Administrator";
    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String GIVEN_NAME = "given_name";
    private static final String FAMILY_NAME = "family_name";
    private static final String EMAIL = "email";
    private final PasswordEncoder passwordEncoder;
    private final TurUserRepository turUserRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurRoleRepository turRoleRepository;
    private final TurConfigProperties turConfigProperties;
    private final TurUserMapper turUserMapper;

    public TurUserAPI(PasswordEncoder passwordEncoder, TurUserRepository turUserRepository,
            TurGroupRepository turGroupRepository,
            TurRoleRepository turRoleRepository,
            TurConfigProperties turConfigProperties,
            TurUserMapper turUserMapper) {
        this.passwordEncoder = passwordEncoder;
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turRoleRepository = turRoleRepository;
        this.turConfigProperties = turConfigProperties;
        this.turUserMapper = turUserMapper;
    }

    private List<String> resolvePrivileges(TurUser turUser) {
        var groups = turGroupRepository.findByTurUsersContaining(turUser);
        var privileges = new java.util.LinkedHashSet<String>();
        for (var group : groups) {
            for (var role : turRoleRepository.findByTurGroupsContaining(group)) {
                for (var privilege : role.getTurPrivileges()) {
                    privileges.add(privilege.getName());
                }
            }
        }
        return new java.util.ArrayList<>(privileges);
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isAdminOrSelf(String username) {
        return isAdmin() || currentUsername().equals(username);
    }

    @Secured("ROLE_ADMIN")
    @GetMapping
    public List<TurUserDto> turUserList() {
        return turUserMapper.toDtoList(turUserRepository.findAll());
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
        turCurrentUser.setEmail(turUser.getEmail());
        turCurrentUser.setAvatarUrl(turUser.getAvatarUrl());
        turCurrentUser.setHasAvatar(turUser.getAvatarUrl() != null);
        turCurrentUser.setPrivileges(resolvePrivileges(turUser));
        return turCurrentUser;
    }

    private TurCurrentUser keycloakUser() {
        OAuth2User user = ((OAuth2User) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        String username = user.getAttribute(PREFERRED_USERNAME);
        String firstName = user.getAttribute(GIVEN_NAME);
        String lastName = user.getAttribute(FAMILY_NAME);
        String email = user.getAttribute(EMAIL);

        TurUser turUser = turUserRepository.findByUsername(username);
        if (turUser == null) {
            turUser = TurUser.builder()
                    .username(username)
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .realm("keycloak")
                    .enabled(1)
                    .build();
            turUserRepository.save(turUser);
        } else {
            turUser.setFirstName(firstName);
            turUser.setLastName(lastName);
            turUser.setEmail(email);
            turUserRepository.save(turUser);
        }

        boolean isKeycloakAdmin = false;
        var groups = turGroupRepository.findByTurUsersContaining(turUser);
        if (groups != null) {
            for (TurGroup turGroup : groups) {
                if (ADMINISTRATOR.equals(turGroup.getName())) {
                    isKeycloakAdmin = true;
                    break;
                }
            }
        }

        TurCurrentUser turCurrentUser = new TurCurrentUser();
        turCurrentUser.setUsername(username);
        turCurrentUser.setFirstName(firstName);
        turCurrentUser.setLastName(lastName);
        turCurrentUser.setEmail(email);
        turCurrentUser.setAdmin(isKeycloakAdmin);
        turCurrentUser.setAvatarUrl(turUser.getAvatarUrl());
        turCurrentUser.setHasAvatar(turUser.getAvatarUrl() != null);
        turCurrentUser.setPrivileges(resolvePrivileges(turUser));
        return turCurrentUser;
    }

    @GetMapping("/{username}")
    public ResponseEntity<TurUserDto> turUserEdit(@PathVariable String username) {
        if (!isAdminOrSelf(username)) {
            return ResponseEntity.status(403).build();
        }
        TurUser turUser = turUserRepository.findByUsername(username);
        TurUser user = Optional.ofNullable(turUser).map(currentUser -> {
            currentUser.setPassword(null);
            currentUser.setTurGroups(turGroupRepository.findByTurUsersContaining(currentUser));
            return currentUser;
        }).orElseGet(TurUser::new);
        return ResponseEntity.ok(turUserMapper.toDto(user));
    }

    @PutMapping("/{username}")
    public ResponseEntity<TurUserDto> turUserUpdate(@PathVariable String username, @RequestBody TurUserDto turUserDto) {
        if (!isAdminOrSelf(username)) {
            return ResponseEntity.status(403).build();
        }
        TurUser turUser = turUserMapper.toEntity(turUserDto);
        TurUser user = Optional.ofNullable(turUserRepository.findByUsername(username)).map(userEdit -> {
            userEdit.setFirstName(turUser.getFirstName());
            userEdit.setLastName(turUser.getLastName());
            userEdit.setEmail(turUser.getEmail());
            userEdit.setAvatarUrl(turUser.getAvatarUrl());
            if (StringUtils.hasText(turUser.getPassword())) {
                userEdit.setPassword(passwordEncoder.encode(turUser.getPassword()));
            }
            // Only admins can change group assignments
            if (isAdmin()) {
                userEdit.setTurGroups(turUser.getTurGroups());
            }
            turUserRepository.save(userEdit);
            return userEdit;
        }).orElseGet(TurUser::new);
        return ResponseEntity.ok(turUserMapper.toDto(user));
    }

    @Secured("ROLE_ADMIN")
    @Transactional
    @DeleteMapping("/{username}")
    public boolean turUserDelete(@PathVariable String username) {
        if (!username.equalsIgnoreCase(ADMIN)) {
            turUserRepository.deleteByUsername(username);
            return true;
        } else {
            return false;
        }
    }

    @Secured("ROLE_ADMIN")
    @PostMapping
    public TurUserDto turUserAdd(@RequestBody TurUserDto turUserDto) {
        TurUser turUser = turUserMapper.toEntity(turUserDto);
        if (StringUtils.hasText(turUser.getPassword())) {
            turUser.setPassword(passwordEncoder.encode(turUser.getPassword()));
            turUserRepository.save(turUser);
        }
        return turUserMapper.toDto(turUser);
    }

    @Secured("ROLE_ADMIN")
    @GetMapping("/model")
    public TurUserDto turUserStructure() {
        return new TurUserDto();
    }

    @PutMapping("/{username}/avatar-url")
    public ResponseEntity<Void> updateAvatarUrl(@PathVariable String username,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        if (!isAdminOrSelf(username)) {
            return ResponseEntity.status(403).build();
        }
        TurUser turUser = turUserRepository.findByUsername(username);
        if (turUser == null) {
            return ResponseEntity.notFound().build();
        }
        String avatarUrl = (body != null) ? body.get("avatarUrl") : null;
        turUser.setAvatarUrl(avatarUrl);
        turUserRepository.save(turUser);
        return ResponseEntity.ok().build();
    }
}
