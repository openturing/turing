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
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
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
import com.viglet.turing.persistence.repository.auth.TurPrivilegeRepository;
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

    // --- S1192: extracted duplicated literals ---
    private static final String ERROR = "error";


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
    private final TurPrivilegeRepository turPrivilegeRepository;

    public TurUserAPI(PasswordEncoder passwordEncoder, TurUserRepository turUserRepository,
            TurGroupRepository turGroupRepository,
            TurRoleRepository turRoleRepository,
            TurConfigProperties turConfigProperties,
            TurUserMapper turUserMapper,
            TurPrivilegeRepository turPrivilegeRepository) {
        this.passwordEncoder = passwordEncoder;
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turRoleRepository = turRoleRepository;
        this.turConfigProperties = turConfigProperties;
        this.turUserMapper = turUserMapper;
        this.turPrivilegeRepository = turPrivilegeRepository;
    }

    private String firstNonNull(OAuth2User user, String... keys) {
        for (String key : keys) {
            String value = user.getAttribute(key);
            if (value != null) return value;
        }
        return null;
    }

    private List<String> resolvePrivileges(TurUser turUser) {
        if (!turConfigProperties.isPermissions()) {
            return turPrivilegeRepository.findAll().stream()
                    .map(com.viglet.turing.persistence.model.auth.TurPrivilege::getName)
                    .toList();
        }
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isAdminOrSelf(String username) {
        return isAdmin() || Objects.equals(currentUsername(), username);
    }

    @Secured("ROLE_ADMIN")
    @GetMapping
    public List<TurUserDto> turUserList() {
        return turUserMapper.toDtoList(turUserRepository.findAll());
    }

    @GetMapping("/current")
    public TurCurrentUser turUserCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (authentication.getPrincipal() instanceof OAuth2User oauthUser) {
                return oauth2User(authentication, oauthUser);
            } else {
                return regularUser(authentication.getName());
            }
        }
        return null;
    }

    private TurCurrentUser regularUser(String currentUserName) {
        boolean isAdmin = !turConfigProperties.isPermissions();
        TurUser turUser = turUserRepository.findByUsername(currentUserName);

        turUser.setPassword(null);
        if (!isAdmin && turUser.getTurGroups() != null) {
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
        turCurrentUser.setRealm(turUser.getRealm());
        turCurrentUser.setPrivileges(resolvePrivileges(turUser));
        return turCurrentUser;
    }

    /** First/last name resolved from an OAuth2 principal. */
    private record OauthNames(String firstName, String lastName) {
    }

    /** Resolves given/family name, falling back to splitting a single "name" attribute. */
    private OauthNames resolveNames(OAuth2User user) {
        String firstName = user.getAttribute(GIVEN_NAME);
        String lastName = user.getAttribute(FAMILY_NAME);
        if (firstName == null && lastName == null) {
            String fullName = firstNonNull(user, "name");
            if (fullName != null) {
                int space = fullName.indexOf(' ');
                firstName = space > 0 ? fullName.substring(0, space) : fullName;
                lastName = space > 0 ? fullName.substring(space + 1) : null;
            }
        }
        return new OauthNames(firstName, lastName);
    }

    /** Creates or updates the local {@link TurUser} mirror from the OAuth2 attributes. */
    private TurUser upsertOauthUser(String username, OauthNames names, String email,
            String picture, String realm) {
        TurUser turUser = turUserRepository.findByUsername(username);
        if (turUser == null) {
            turUser = TurUser.builder()
                    .username(username)
                    .firstName(names.firstName())
                    .lastName(names.lastName())
                    .email(email)
                    .avatarUrl(picture)
                    .realm(realm)
                    .enabled(1)
                    .build();
        } else {
            turUser.setFirstName(names.firstName());
            turUser.setLastName(names.lastName());
            turUser.setEmail(email);
            turUser.setRealm(realm);
            if (picture != null && turUser.getAvatarUrl() == null) {
                turUser.setAvatarUrl(picture);
            }
        }
        turUserRepository.save(turUser);
        return turUser;
    }

    /** Admin when permissions are disabled, or the user belongs to the ADMINISTRATOR group. */
    private boolean resolveIsAdmin(TurUser turUser) {
        if (!turConfigProperties.isPermissions()) {
            return true;
        }
        var groups = turGroupRepository.findByTurUsersContaining(turUser);
        if (groups != null) {
            for (TurGroup turGroup : groups) {
                if (ADMINISTRATOR.equals(turGroup.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private TurCurrentUser oauth2User(Authentication authentication, OAuth2User user) {
        String realm = (authentication instanceof OAuth2AuthenticationToken oauthToken)
                ? oauthToken.getAuthorizedClientRegistrationId()
                : "oauth2";
        String username = firstNonNull(user, PREFERRED_USERNAME, "login", EMAIL);
        String email = firstNonNull(user, EMAIL);
        String picture = firstNonNull(user, "picture", "avatar_url");

        OauthNames names = resolveNames(user);
        TurUser turUser = upsertOauthUser(username, names, email, picture, realm);
        boolean isAdmin = resolveIsAdmin(turUser);

        TurCurrentUser turCurrentUser = new TurCurrentUser();
        turCurrentUser.setUsername(username);
        turCurrentUser.setFirstName(names.firstName());
        turCurrentUser.setLastName(names.lastName());
        turCurrentUser.setEmail(email);
        turCurrentUser.setAdmin(isAdmin);
        turCurrentUser.setAvatarUrl(turUser.getAvatarUrl());
        turCurrentUser.setHasAvatar(turUser.getAvatarUrl() != null);
        turCurrentUser.setRealm(turUser.getRealm());
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

    public record RegisterRequest(String username, String password, String firstName, String lastName, String email) {}

    /** T652 / §XXXVII.14 — safe self-registration username: 3-100 chars, letters/digits/._-@. */
    private static final java.util.regex.Pattern REGISTRATION_USERNAME =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._@-]{3,100}$");

    static boolean isValidRegistrationUsername(String username) {
        return username != null && REGISTRATION_USERNAME.matcher(username).matches();
    }

    @PostMapping("/register")
    public ResponseEntity<Object> turUserRegister(@RequestBody RegisterRequest request) {
        if (turConfigProperties.getAuthentication() == null
                || !turConfigProperties.getAuthentication().isNewUser()) {
            return ResponseEntity.status(403).body(java.util.Map.of(ERROR, "Self-registration is disabled"));
        }
        if (!StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            return ResponseEntity.badRequest().body(java.util.Map.of(ERROR, "Username and password are required"));
        }
        // T652 / §XXXVII.14 — constrain the self-registration username to a safe
        // charset + length instead of accepting an arbitrary string, and enforce
        // a minimum password length. Self-registered users only get the User
        // group (never admin), but a bounded, well-formed username avoids
        // surprising log/display/lookup behaviour from exotic input.
        if (!isValidRegistrationUsername(request.username())) {
            return ResponseEntity.badRequest().body(java.util.Map.of(ERROR,
                    "Username must be 3-100 characters using letters, digits, and . _ - @ only"));
        }
        if (request.password().length() < 8) {
            return ResponseEntity.badRequest().body(java.util.Map.of(ERROR,
                    "Password must be at least 8 characters"));
        }
        if (turUserRepository.findByUsername(request.username()) != null) {
            return ResponseEntity.badRequest().body(java.util.Map.of(ERROR, "Username already exists"));
        }
        TurUser turUser = TurUser.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .enabled(1)
                .build();
        TurGroup userGroup = turGroupRepository.findByName("User");
        if (userGroup != null) {
            turUser.setTurGroups(java.util.Collections.singletonList(userGroup));
        }
        turUserRepository.save(turUser);
        return ResponseEntity.ok(turUserMapper.toDto(turUser));
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
