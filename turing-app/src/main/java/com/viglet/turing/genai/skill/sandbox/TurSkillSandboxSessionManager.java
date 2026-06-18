/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.sandbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T321 / §IX.4.d — opens and tracks {@link TurSkillSandboxSession}s.
 *
 * <p>A session is keyed by {@code (agentId, conversationId, skillId)} and owns a
 * stable host directory under
 * {@code store/skill-sandbox/tenants/{agentId}/{conversationId}/{skillId}/}:
 * <ul>
 *   <li>{@code skill/} — the skill's Anthropic-compatible folder, materialized
 *       once from object storage (mounted read-only at {@code /skill});</li>
 *   <li>{@code workspace/} — a persistent scratch dir that survives across chat
 *       turns (mounted read-write at {@code /workspace}).</li>
 * </ul>
 *
 * <p>{@link #openSession} is idempotent for a given key: the first call
 * materializes the skill folder and creates the workspace; subsequent calls
 * reuse the same directories (so files written in one turn are visible in the
 * next). The skill folder is re-materialized only when its host dir is empty,
 * keeping the common per-turn path cheap.
 *
 * <p>Gated on object storage (a skill lives in storage). The Docker requirement
 * is enforced one layer up in {@link TurSkillSandboxService} — the manager only
 * prepares directories and does not itself run anything.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurSkillSandboxSessionManager {

    private static final Logger log = LoggerFactory.getLogger(TurSkillSandboxSessionManager.class);

    static final String SANDBOX_DIR = "skill-sandbox";
    static final String TENANTS_DIR = "tenants";
    static final String SKILL_SUBDIR = "skill";
    static final String WORKSPACE_SUBDIR = "workspace";
    private static final String DEFAULT_BUCKET = "default";
    private static final int MAX_DEPTH = 32;

    private final TurStorageService storageService;
    private final TurSkillCatalogService catalogService;

    /** Last-used wall-clock (epoch ms) per session key, for idle reaping. */
    private final ConcurrentHashMap<String, AtomicLong> lastUsed = new ConcurrentHashMap<>();

    public TurSkillSandboxSessionManager(TurStorageService storageService,
            TurSkillCatalogService catalogService) {
        this.storageService = storageService;
        this.catalogService = catalogService;
    }

    /** Whether sessions can be opened at all (object storage must be configured). */
    public boolean isStorageEnabled() {
        return storageService.isEnabled();
    }

    /**
     * Open (or reuse) the session for {@code (agentId, conversationId, skillId)}.
     * Materializes the skill folder on first open and ensures the persistent
     * workspace exists.
     *
     * @throws IllegalStateException    when storage is disabled
     * @throws IllegalArgumentException when the skill id is unknown
     */
    public TurSkillSandboxSession openSession(String agentId, String conversationId, String skillId) {
        if (!isStorageEnabled()) {
            throw new IllegalStateException("Skill sandbox requires object storage to be configured.");
        }
        TurSkill skill = catalogService.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));

        String agent = segment(agentId);
        String conv = segment(conversationId);
        TurSkillSandboxSession session = locate(agent, conv, skill);

        try {
            Files.createDirectories(session.workspaceDir().toPath());
            materializeSkillIfNeeded(skill, session.skillDir());
        } catch (IOException e) {
            throw new IllegalStateException("Could not prepare skill sandbox session for " + skillId, e);
        }
        lastUsed.computeIfAbsent(session.key(), k -> new AtomicLong())
                .set(System.currentTimeMillis());
        return session;
    }

    /** Build the (deterministic) session descriptor for a resolved skill. */
    private TurSkillSandboxSession locate(String agent, String conv, TurSkill skill) {
        File tenantsRoot = TurCommonsUtils.addSubDirToStoreDir(SANDBOX_DIR + "/" + TENANTS_DIR);
        File sessionDir = new File(new File(new File(tenantsRoot, agent), conv), segment(skill.getId()));
        File skillDir = new File(sessionDir, SKILL_SUBDIR);
        File workspaceDir = new File(sessionDir, WORKSPACE_SUBDIR);
        String sessionId = UUID.nameUUIDFromBytes(
                (agent + "/" + conv + "/" + skill.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().substring(0, 12);
        return new TurSkillSandboxSession(sessionId, skill.getId(), agent, conv,
                sessionDir, skillDir, workspaceDir);
    }

    /**
     * Copy the skill's storage folder into {@code skillDir} when it has not been
     * materialized yet (empty/absent). The skill folder is treated as immutable
     * truth — the editor mutates it in storage, and a session re-materializes on
     * its next cold open after the dir is reaped.
     */
    private void materializeSkillIfNeeded(TurSkill skill, File skillDir) throws IOException {
        if (skillDir.isDirectory()) {
            File[] existing = skillDir.listFiles();
            if (existing != null && existing.length > 0) {
                return; // already materialized
            }
        }
        Files.createDirectories(skillDir.toPath());
        String root = stripTrailingSlash(skill.getPath());
        int copied = copyFolder(root, root, skillDir.toPath(), 0);
        log.info("[SkillSandbox] Materialized {} file(s) of skill '{}' into {}",
                copied, skill.getName(), skillDir.getAbsolutePath());
    }

    /** Recursively download every file under {@code folder} into {@code destRoot}. */
    private int copyFolder(String root, String folder, Path destRoot, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            return 0;
        }
        int copied = 0;
        for (TurAssetItem item : storageService.listObjects(folder + "/")) {
            String objectName = stripTrailingSlash(item.name());
            String relative = relativize(root, objectName);
            if (relative.isEmpty()) {
                continue;
            }
            if (item.directory()) {
                Files.createDirectories(destRoot.resolve(relative));
                copied += copyFolder(root, objectName, destRoot, depth + 1);
            } else {
                Path target = destRoot.resolve(relative);
                Files.createDirectories(target.getParent());
                try (InputStream in = storageService.downloadObject(objectName)) {
                    Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                } catch (Exception e) {
                    log.warn("[SkillSandbox] Could not copy '{}': {}", objectName, e.getMessage());
                }
            }
        }
        return copied;
    }

    /** Mark a session touched (called by the runner on each command). */
    void touch(TurSkillSandboxSession session) {
        lastUsed.computeIfAbsent(session.key(), k -> new AtomicLong())
                .set(System.currentTimeMillis());
    }

    private static String segment(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BUCKET;
        }
        String safe = raw.replaceAll("[^a-zA-Z0-9._-]", "");
        if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) {
            return DEFAULT_BUCKET;
        }
        return safe;
    }

    private static String relativize(String root, String objectName) {
        if (root == null || objectName == null) {
            return "";
        }
        if (objectName.equals(root)) {
            return "";
        }
        return objectName.startsWith(root + "/") ? objectName.substring(root.length() + 1) : "";
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
