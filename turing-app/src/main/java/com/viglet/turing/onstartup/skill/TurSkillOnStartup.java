/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.onstartup.skill;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.TurSkillManifest;
import com.viglet.turing.genai.skill.TurSkillService;

import lombok.extern.slf4j.Slf4j;

/**
 * T116 / §IX.4 — re-syncs the bundled platform skills into the {@code skill}
 * table on every boot. Unlike the first-time-only seeders driven by
 * {@link com.viglet.turing.onstartup.TurOnStartup}, this runs unconditionally:
 * install is idempotent per {@code (name, version)}, so a version bump to a
 * bundled manifest propagates on the next restart without creating duplicates,
 * and a fresh install gets the v1 catalog without any manual step.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Component
@Transactional
@Order(2)
public class TurSkillOnStartup implements ApplicationRunner {

    private final TurSkillCatalogService catalogService;
    private final TurSkillService skillService;

    public TurSkillOnStartup(TurSkillCatalogService catalogService, TurSkillService skillService) {
        this.catalogService = catalogService;
        this.skillService = skillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int synced = 0;
        for (TurSkillManifest manifest : catalogService.list()) {
            try {
                skillService.install(manifest, true);
                synced++;
            } catch (RuntimeException e) {
                log.warn("[Skills] Failed to sync built-in skill {}@{}: {}",
                        manifest.name(), manifest.version(), e.getMessage());
            }
        }
        log.info("[Skills] Synced {} built-in skill(s) into the catalog.", synced);
    }
}
