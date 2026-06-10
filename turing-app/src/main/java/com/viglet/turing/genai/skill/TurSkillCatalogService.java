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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T116 / §IX.4 — loads the bundled platform skills from the classpath at boot
 * and exposes them as the v1 catalog. The three skills shipped in v1 are the
 * helpers extracted in §VII.4: {@code scaffold-markdown},
 * {@code scaffold-quote-extraction} and {@code scaffold-mindmap}.
 *
 * <p>Manifests are JSON files under
 * {@code turing-app/src/main/resources/skills/*.skill.manifest.json} whose
 * shape mirrors {@link TurSkillManifest} 1-to-1. Adding a platform skill is
 * "write a JSON" — no Java change. Mirrors the
 * {@link com.viglet.turing.service.chatflow.TurChatFlowRecipeService} loader so
 * the two bundled-asset catalogs behave identically.
 *
 * <p>Catalog manifests never change at runtime, so they are parsed once at boot
 * and cached by name in an insertion-ordered map. A broken manifest is logged
 * and skipped — one bad file must not stall startup.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSkillCatalogService {

    /**
     * Classpath glob scanned on boot. Matches both the Maven-packaged jar
     * layout ({@code target/classes/skills/}) and a dev IDE pointing at
     * {@code src/main/resources/skills/}.
     */
    static final String SKILL_CLASSPATH_PATTERN = "classpath:/skills/*.skill.manifest.json";

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final TurSkillManifestService manifestService;

    /** Insertion-ordered (after sort) so the catalog returns the same order on every call. */
    private final Map<String, TurSkillManifest> catalogByName = new LinkedHashMap<>();

    public TurSkillCatalogService(TurSkillManifestService manifestService) {
        this.manifestService = manifestService;
    }

    @PostConstruct
    void loadFromClasspath() {
        loadFrom(new PathMatchingResourcePatternResolver());
    }

    /** Package-private hook so a test can substitute the resource resolver. */
    void loadFrom(PathMatchingResourcePatternResolver resolver) {
        catalogByName.clear();
        Resource[] resources;
        try {
            resources = resolver.getResources(SKILL_CLASSPATH_PATTERN);
        } catch (IOException e) {
            log.warn("[Skills] No platform skills loaded — classpath scan failed: {}", e.getMessage());
            return;
        }
        List<TurSkillManifest> ordered = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            try (InputStream stream = resource.getInputStream()) {
                TurSkillManifest manifest = OBJECT_MAPPER.readValue(stream, TurSkillManifest.class);
                TurSkillManifestService.ValidationResult validation = manifestService.validate(manifest);
                if (!validation.valid()) {
                    log.warn("[Skills] Skipping invalid platform skill at {}: {}",
                            resource.getDescription(), validation.errors());
                    continue;
                }
                ordered.add(manifest);
            } catch (IOException | RuntimeException e) {
                log.warn("[Skills] Failed to parse platform skill at {}: {}",
                        resource.getDescription(), e.getMessage());
            }
        }
        ordered.sort(Comparator.comparing(m -> m.name() == null ? "" : m.name()));
        for (TurSkillManifest manifest : ordered) {
            catalogByName.put(manifest.name(), manifest);
        }
        log.info("[Skills] Loaded {} platform skill(s) from {}: {}",
                catalogByName.size(), SKILL_CLASSPATH_PATTERN, catalogByName.keySet());
    }

    /** Every bundled platform skill, in catalog order. */
    public List<TurSkillManifest> list() {
        return new ArrayList<>(catalogByName.values());
    }

    /** Look up a bundled skill by manifest name. */
    public Optional<TurSkillManifest> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(catalogByName.get(name));
    }
}
