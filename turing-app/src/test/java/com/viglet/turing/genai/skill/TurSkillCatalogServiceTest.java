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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T116 / §IX.4 — verifies the three bundled platform skills load from the
 * classpath and validate cleanly. Acts as the CI guard against a broken
 * {@code skills/*.skill.manifest.json} shipping unnoticed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSkillCatalogServiceTest {

    private TurSkillCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = new TurSkillCatalogService(new TurSkillManifestService());
        catalog.loadFromClasspath();
    }

    @Test
    void loadsTheThreeV1PlatformSkills() {
        List<String> names = catalog.list().stream().map(TurSkillManifest::name).toList();
        assertThat(names).containsExactlyInAnyOrder(
                "scaffold-markdown", "scaffold-quote-extraction", "scaffold-mindmap");
    }

    @Test
    void everyBundledSkillIsValidAndUnsigned() {
        TurSkillManifestService service = new TurSkillManifestService();
        for (TurSkillManifest manifest : catalog.list()) {
            TurSkillManifestService.ValidationResult result = service.validate(manifest);
            assertThat(result.valid())
                    .as("manifest %s must be valid: %s", manifest.name(), result.errors())
                    .isTrue();
            assertThat(manifest.author()).isEqualTo("viglet");
            assertThat(manifest.tools()).isNotEmpty();
        }
    }

    @Test
    void getByNameReturnsTheBundledManifest() {
        assertThat(catalog.get("scaffold-markdown")).isPresent();
        assertThat(catalog.get("does-not-exist")).isEmpty();
        assertThat(catalog.get(null)).isEmpty();
    }
}
