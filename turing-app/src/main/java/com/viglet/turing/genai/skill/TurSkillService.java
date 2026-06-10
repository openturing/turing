/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.genai.skill.TurSkillManifestService.ValidationResult;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.skill.TurSkillRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T116 / §IX.4 — install and manage {@link TurSkill} bundles. Shared by the
 * REST API ({@link com.viglet.turing.api.skill.TurSkillAPI}) and the boot-time
 * built-in seeder ({@code TurSkillOnStartup}).
 *
 * <p>Installing is idempotent per {@code (name, version)}: re-installing the
 * same coordinates updates the existing row in place rather than creating a
 * duplicate, which is what lets the marketplace pin versions and lets the
 * built-in seeder safely re-run on every boot.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSkillService {

    private final TurSkillRepository repository;
    private final TurSkillManifestService manifestService;

    public TurSkillService(TurSkillRepository repository, TurSkillManifestService manifestService) {
        this.repository = repository;
        this.manifestService = manifestService;
    }

    /**
     * Validate then persist a manifest as a {@link TurSkill}, upserting by
     * {@code (name, version)}.
     *
     * @param manifest the parsed manifest
     * @param builtIn  whether this is a platform-shipped skill (protected from
     *                 API deletion, re-synced from the classpath on boot)
     * @return the persisted entity
     * @throws IllegalArgumentException if the manifest fails validation
     */
    public TurSkill install(TurSkillManifest manifest, boolean builtIn) {
        ValidationResult validation = manifestService.validate(manifest);
        if (!validation.valid()) {
            throw new IllegalArgumentException(String.join("; ", validation.errors()));
        }
        TurSkill entity = manifestService.toEntity(manifest, builtIn);
        Optional<TurSkill> existing = repository.findByNameAndVersion(manifest.name(), manifest.version());
        existing.ifPresent(skill -> entity.setId(skill.getId()));
        TurSkill saved = repository.save(entity);
        log.info("[Skills] Installed skill {}@{} (builtIn={}, signature={})",
                saved.getName(), saved.getVersion(), builtIn, validation.signatureStatus());
        return saved;
    }

    public List<TurSkill> listAll() {
        return repository.findAll();
    }

    public Optional<TurSkill> findById(String id) {
        return repository.findById(id);
    }

    /**
     * Delete a user-installed skill. Built-in (platform) skills are protected —
     * deleting one returns {@code false} without touching the row.
     *
     * @return {@code true} if a deletable skill existed and was removed
     */
    public boolean delete(String id) {
        Optional<TurSkill> existing = repository.findById(id);
        if (existing.isEmpty() || existing.get().isBuiltIn()) {
            return false;
        }
        repository.delete(id);
        return true;
    }
}
