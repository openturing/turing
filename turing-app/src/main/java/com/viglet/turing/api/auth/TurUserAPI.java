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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.bean.TurCurrentUser;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.dto.auth.TurUserDto;
import com.viglet.turing.persistence.mapper.auth.TurUserMapper;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
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

    private static final String AVATARS_DIR = "avatars";

    private static final String ADMIN = "admin";
    private static final String ADMINISTRATOR = "Administrator";
    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String GIVEN_NAME = "given_name";
    private static final String FAMILY_NAME = "family_name";
    private static final String EMAIL = "email";
    private final PasswordEncoder passwordEncoder;
    private final TurUserRepository turUserRepository;
    private final TurGroupRepository turGroupRepository;
    private final TurConfigProperties turConfigProperties;
    private final TurUserMapper turUserMapper;

    public TurUserAPI(PasswordEncoder passwordEncoder, TurUserRepository turUserRepository,
            TurGroupRepository turGroupRepository,
            TurConfigProperties turConfigProperties,
            TurUserMapper turUserMapper) {
        this.passwordEncoder = passwordEncoder;
        this.turUserRepository = turUserRepository;
        this.turGroupRepository = turGroupRepository;
        this.turConfigProperties = turConfigProperties;
        this.turUserMapper = turUserMapper;
    }

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
        File avatarsDir = TurCommonsUtils.addSubDirToStoreDir(AVATARS_DIR);
        turCurrentUser.setHasAvatar(turUser.getAvatarUrl() != null
                || findAvatarFile(avatarsDir, currentUserName).isPresent());
        return turCurrentUser;
    }

    private static TurCurrentUser keycloakUser() {
        OAuth2User user = ((OAuth2User) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        TurCurrentUser turCurrentUser = new TurCurrentUser();
        turCurrentUser.setUsername(user.getAttribute(PREFERRED_USERNAME));
        turCurrentUser.setFirstName(user.getAttribute(GIVEN_NAME));
        turCurrentUser.setLastName(user.getAttribute(FAMILY_NAME));
        turCurrentUser.setEmail(user.getAttribute(EMAIL));
        turCurrentUser.setAdmin(true);
        return turCurrentUser;
    }

    @GetMapping("/{username}")
    public TurUserDto turUserEdit(@PathVariable String username) {
        TurUser turUser = turUserRepository.findByUsername(username);
        TurUser user = Optional.ofNullable(turUser).map(currentUser -> {
            currentUser.setPassword(null);
            currentUser.setTurGroups(turGroupRepository.findByTurUsersContaining(currentUser));
            return currentUser;
        }).orElseGet(TurUser::new);
        return turUserMapper.toDto(user);
    }

    @PutMapping("/{username}")
    public TurUserDto turUserUpdate(@PathVariable String username, @RequestBody TurUserDto turUserDto) {
        TurUser turUser = turUserMapper.toEntity(turUserDto);
        TurUser user = Optional.ofNullable(turUserRepository.findByUsername(username)).map(userEdit -> {
            userEdit.setFirstName(turUser.getFirstName());
            userEdit.setLastName(turUser.getLastName());
            userEdit.setEmail(turUser.getEmail());
            userEdit.setAvatarUrl(turUser.getAvatarUrl());
            if (StringUtils.hasText(turUser.getPassword())) {
                userEdit.setPassword(passwordEncoder.encode(turUser.getPassword()));
            }
            userEdit.setTurGroups(turUser.getTurGroups());
            turUserRepository.save(userEdit);
            return userEdit;
        }).orElseGet(TurUser::new);
        return turUserMapper.toDto(user);
    }

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

    @PostMapping
    public TurUserDto turUserAdd(@RequestBody TurUserDto turUserDto) {
        TurUser turUser = turUserMapper.toEntity(turUserDto);
        if (StringUtils.hasText(turUser.getPassword())) {
            turUser.setPassword(passwordEncoder.encode(turUser.getPassword()));
            turUserRepository.save(turUser);
        }
        return turUserMapper.toDto(turUser);
    }

    @GetMapping("/model")
    public TurUserDto turUserStructure() {
        return new TurUserDto();
    }

    @PutMapping("/{username}/avatar-url")
    public ResponseEntity<Void> updateAvatarUrl(@PathVariable String username,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        TurUser turUser = turUserRepository.findByUsername(username);
        if (turUser == null) {
            return ResponseEntity.notFound().build();
        }
        String avatarUrl = (body != null) ? body.get("avatarUrl") : null;
        turUser.setAvatarUrl(avatarUrl);
        turUserRepository.save(turUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/avatar")
    public ResponseEntity<Void> uploadAvatar(@PathVariable String username,
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }
        TurUser turUser = turUserRepository.findByUsername(username);
        if (turUser == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            File avatarsDir = TurCommonsUtils.addSubDirToStoreDir(AVATARS_DIR);
            String extension = getFileExtension(file.getOriginalFilename(), contentType);
            deleteExistingAvatars(avatarsDir, username);
            Path target = Path.of(avatarsDir.getAbsolutePath(), username + "." + extension);
            Files.copy(file.getInputStream(), target);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Failed to save avatar for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{username}/avatar")
    public ResponseEntity<Resource> getAvatar(@PathVariable String username) {
        File avatarsDir = TurCommonsUtils.addSubDirToStoreDir(AVATARS_DIR);
        Optional<File> avatarFile = findAvatarFile(avatarsDir, username);
        if (avatarFile.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        File file = avatarFile.get();
        try {
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) {
                mimeType = "image/png";
            }
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(resource);
        } catch (IOException e) {
            log.error("Failed to read avatar for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{username}/avatar")
    public ResponseEntity<Void> deleteAvatar(@PathVariable String username) {
        File avatarsDir = TurCommonsUtils.addSubDirToStoreDir(AVATARS_DIR);
        deleteExistingAvatars(avatarsDir, username);
        return ResponseEntity.ok().build();
    }

    private void deleteExistingAvatars(File avatarsDir, String username) {
        File[] existing = avatarsDir.listFiles((dir, name) -> name.startsWith(username + "."));
        if (existing != null) {
            for (File f : existing) {
                try {
                    Files.delete(f.toPath());
                } catch (IOException e) {
                    log.warn("Failed to delete existing avatar file {}: {}", f.getName(), e.getMessage());
                }
            }
        }
    }

    private Optional<File> findAvatarFile(File avatarsDir, String username) {
        File[] matches = avatarsDir.listFiles((dir, name) -> name.startsWith(username + "."));
        if (matches != null && matches.length > 0) {
            return Optional.of(matches[0]);
        }
        return Optional.empty();
    }

    private String getFileExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }
}
