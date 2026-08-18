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
package com.viglet.turing.profile;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.api.profile.bean.TurProfileDto;
import com.viglet.turing.api.profile.bean.TurProfileRequest;
import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;

/**
 * Manages the generic (global) profiles created under the console "Management"
 * section.
 * <p>
 * The profile is configuration-agnostic (spec 002): each configuration type —
 * the first being facet ordering per (profile × site) — lives in its own entity
 * referencing {@link TurProfile}. This service owns the CRUD invariants:
 * exactly one system-wide default profile ("Padrão"), which is protected — it
 * cannot be renamed nor deleted and is used as the runtime fallback.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@Service
public class TurProfileService {

    public static final String DEFAULT_PROFILE_NAME = "Padrão";

    private final TurProfileRepository turProfileRepository;

    public TurProfileService(TurProfileRepository turProfileRepository) {
        this.turProfileRepository = turProfileRepository;
    }

    // ---------------------------------------------------------------- bootstrap

    /** Idempotently ensures the single global default profile ("Padrão") exists. */
    @Transactional
    public TurProfile ensureDefaultProfile() {
        Optional<TurProfile> current = turProfileRepository.findByDefaultProfileTrue();
        if (current.isPresent()) {
            return current.get();
        }
        TurProfile padrao = turProfileRepository.findByNameIgnoreCase(DEFAULT_PROFILE_NAME)
                .orElseGet(() -> TurProfile.builder().name(DEFAULT_PROFILE_NAME).build());
        padrao.setDefaultProfile(true);
        return turProfileRepository.save(padrao);
    }

    // --------------------------------------------------------------------- CRUD

    @Transactional
    public List<TurProfileDto> list() {
        // Guarantees the default profile always exists, so the console never shows an
        // empty list and "no profile selected" always maps to a real (default) profile.
        ensureDefaultProfile();
        return turProfileRepository.findAll().stream()
                .sorted(Comparator.comparing(TurProfile::isDefaultProfile).reversed()
                        .thenComparing(TurProfile::getName,
                                Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(TurProfileDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TurProfileDto get(String profileId) {
        return TurProfileDto.from(requireProfile(profileId));
    }

    @Transactional
    public TurProfileDto create(TurProfileRequest request) {
        String name = requireName(request);
        if (turProfileRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A profile named '%s' already exists".formatted(name));
        }
        boolean firstProfile = turProfileRepository.findByDefaultProfileTrue().isEmpty();
        TurProfile profile = TurProfile.builder()
                .name(name)
                .description(request.description())
                .defaultProfile(firstProfile)
                .build();
        return TurProfileDto.from(turProfileRepository.save(profile));
    }

    @Transactional
    public TurProfileDto update(String profileId, TurProfileRequest request) {
        TurProfile profile = requireProfile(profileId);
        String name = requireName(request);
        // The default profile is a fixed, protected profile: it cannot be renamed
        // (nor deleted). Its description remains editable.
        if (profile.isDefaultProfile() && !name.equals(profile.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The default profile cannot be renamed");
        }
        turProfileRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(profile.getId()))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A profile named '%s' already exists".formatted(name));
                });
        profile.setName(name);
        profile.setDescription(request.description());
        return TurProfileDto.from(turProfileRepository.save(profile));
    }

    @Transactional
    public List<TurProfileDto> setDefault(String profileId) {
        TurProfile newDefault = requireProfile(profileId);
        turProfileRepository.findAll().forEach(profile -> {
            boolean shouldBeDefault = profile.getId().equals(newDefault.getId());
            if (profile.isDefaultProfile() != shouldBeDefault) {
                profile.setDefaultProfile(shouldBeDefault);
                turProfileRepository.save(profile);
            }
        });
        return list();
    }

    @Transactional
    public void delete(String profileId) {
        TurProfile profile = requireProfile(profileId);
        if (profile.isDefaultProfile()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The default profile cannot be deleted; set another profile as default first");
        }
        // Its facet orderings (per site) are removed by the FK ON DELETE CASCADE.
        turProfileRepository.delete(profile);
    }

    // ------------------------------------------------------------------ helpers

    private TurProfile requireProfile(String profileId) {
        return turProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Profile not found"));
    }

    private static String requireName(TurProfileRequest request) {
        String name = request != null && request.name() != null ? request.name().trim() : "";
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile name is required");
        }
        return name;
    }
}
