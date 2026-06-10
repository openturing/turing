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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.skill.TurSkillRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T317 / §IX.4.d — keeps the thin {@link TurSkill} catalog index in sync with
 * the skill <em>folders</em> that live in object storage.
 *
 * <p>A skill is a storage folder under the configured
 * {@code turing.storage.skillsPath} (default {@code skills/}) containing a
 * {@code SKILL.md} at its root. {@link #reindex()} walks the immediate child
 * folders of that prefix, reads each {@code SKILL.md}, parses its YAML
 * frontmatter via {@link TurSkillFrontmatterParser}, and reconciles the index:
 * new folders are inserted, existing folders are refreshed (preserving the
 * {@code enabled} flag and id), and rows whose folder no longer carries a valid
 * {@code SKILL.md} are removed.
 *
 * <p>Gated on storage: when {@link TurStorageService#isEnabled()} is false,
 * reads return empty and {@link #reindex()} is a no-op — there is nowhere for
 * skill folders to live.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurSkillCatalogService {

    private static final Logger log = LoggerFactory.getLogger(TurSkillCatalogService.class);
    private static final String SKILL_FILE = "SKILL.md";
    private static final String DEFAULT_SKILLS_PATH = "skills";

    private final TurStorageService storageService;
    private final TurSkillRepository skillRepository;
    private final TurSkillFrontmatterParser frontmatterParser;
    private final TurConfigProperties configProperties;

    public TurSkillCatalogService(TurStorageService storageService, TurSkillRepository skillRepository,
            TurSkillFrontmatterParser frontmatterParser, TurConfigProperties configProperties) {
        this.storageService = storageService;
        this.skillRepository = skillRepository;
        this.frontmatterParser = frontmatterParser;
        this.configProperties = configProperties;
    }

    /** Whether the skills feature is usable (storage must be configured). */
    public boolean isEnabled() {
        return storageService.isEnabled();
    }

    /** All indexed skills, or an empty list when storage is disabled. */
    public List<TurSkill> listAll() {
        return isEnabled() ? skillRepository.findAll() : List.of();
    }

    public Optional<TurSkill> findById(String id) {
        return isEnabled() ? skillRepository.findById(id) : Optional.empty();
    }

    /** Enable/disable an indexed skill without touching its folder. */
    public Optional<TurSkill> setEnabled(String id, boolean enabled) {
        return findById(id).map(skill -> {
            skill.setEnabled(enabled ? 1 : 0);
            return skillRepository.save(skill);
        });
    }

    /** Raw {@code SKILL.md} contents for an indexed skill (for preview / editor). */
    public Optional<String> getSkillMarkdown(String id) {
        return findById(id).map(skill -> readSkillFile(skill.getPath()));
    }

    /**
     * Walk the skills storage prefix, (re)index every folder with a valid
     * {@code SKILL.md}, and prune index rows whose folder no longer qualifies.
     *
     * @return the number of skill folders indexed after reconciliation
     */
    public int reindex() {
        if (!isEnabled()) {
            log.debug("[Skills] Reindex skipped — storage is disabled.");
            return 0;
        }
        String prefix = skillsPrefix();
        Set<String> seenPaths = new HashSet<>();
        for (String folderPath : listSkillFolders(prefix)) {
            String markdown = readSkillFile(folderPath);
            if (markdown == null) {
                continue;
            }
            TurSkillFrontmatter frontmatter = frontmatterParser.parse(markdown);
            if (!frontmatter.isValid()) {
                log.warn("[Skills] Folder '{}' has a SKILL.md without a valid 'name' — skipping.", folderPath);
                continue;
            }
            upsert(folderPath, frontmatter);
            seenPaths.add(folderPath);
        }
        pruneMissing(prefix, seenPaths);
        log.info("[Skills] Reindexed {} skill folder(s) under '{}'.", seenPaths.size(), prefix);
        return seenPaths.size();
    }

    private void upsert(String folderPath, TurSkillFrontmatter frontmatter) {
        TurSkill skill = skillRepository.findByPath(folderPath).orElseGet(() -> {
            TurSkill created = new TurSkill();
            created.setPath(folderPath);
            return created;
        });
        skill.setName(frontmatter.name());
        skill.setDescription(frontmatter.description());
        skill.setVersion(frontmatter.version());
        skill.setAuthor(frontmatter.author());
        skillRepository.save(skill);
    }

    /** Drop index rows under {@code prefix} whose folder is no longer a valid skill. */
    private void pruneMissing(String prefix, Set<String> seenPaths) {
        for (TurSkill skill : skillRepository.findAll()) {
            String path = skill.getPath();
            if (path != null && path.startsWith(prefix) && !seenPaths.contains(path)) {
                skillRepository.delete(skill.getId());
            }
        }
    }

    /** Immediate child folders of the skills prefix (no trailing slash). */
    private List<String> listSkillFolders(String prefix) {
        List<String> folders = new ArrayList<>();
        for (TurAssetItem item : storageService.listObjects(prefix)) {
            if (item.directory()) {
                folders.add(stripTrailingSlash(item.name()));
            }
        }
        return folders;
    }

    /** Read {@code <folderPath>/SKILL.md}, or {@code null} when it is absent/unreadable. */
    private String readSkillFile(String folderPath) {
        String objectName = folderPath + "/" + SKILL_FILE;
        try (InputStream in = storageService.downloadObject(objectName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("[Skills] No readable {} in '{}': {}", SKILL_FILE, folderPath, e.getMessage());
            return null;
        }
    }

    /** Configured skills prefix, normalised to end with a single slash. */
    private String skillsPrefix() {
        TurStorageProperty storage = configProperties.getStorage();
        String path = (storage != null && storage.getSkillsPath() != null && !storage.getSkillsPath().isBlank())
                ? storage.getSkillsPath()
                : DEFAULT_SKILLS_PATH;
        path = stripTrailingSlash(path.trim());
        return path + "/";
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
