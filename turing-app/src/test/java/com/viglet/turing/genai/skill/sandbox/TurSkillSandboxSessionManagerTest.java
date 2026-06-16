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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.TurSkillFrontmatterParser;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.skill.TurSkillRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import com.viglet.turing.service.storage.TurFilesystemStorageService;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T321 — verifies {@link TurSkillSandboxSessionManager} materializes a skill
 * folder into a stable host dir, reuses the session for the same key, keeps
 * different conversations isolated, and gates on storage.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSkillSandboxSessionManagerTest {

    @TempDir
    Path storageDir;

    private TurSkillSandboxSessionManager manager;
    private TurSkillCatalogService catalog;
    private Map<String, TurSkill> store;
    private Path sandboxRoot;

    @BeforeEach
    void setUp() throws IOException {
        // Session dirs land under the real ./store/skill-sandbox (like the
        // Code Interpreter tests); clean that subtree before and after.
        sandboxRoot = Path.of(TurCommonsUtils.addSubDirToStoreDir(
                TurSkillSandboxSessionManager.SANDBOX_DIR).getPath());
        deleteRecursively(sandboxRoot);

        TurConfigProperties configProperties = new TurConfigProperties();
        TurStorageProperty storageProperty = new TurStorageProperty();
        storageProperty.setSkillsPath("skills");
        TurStorageProperty.TurFilesystemProperty fs = new TurStorageProperty.TurFilesystemProperty();
        fs.setPath(storageDir.toAbsolutePath().normalize().toString());
        storageProperty.setFilesystem(fs);
        configProperties.setStorage(storageProperty);

        TurFilesystemStorageService storage = new TurFilesystemStorageService(configProperties);
        storage.init();

        store = new LinkedHashMap<>();
        TurSkillRepository repository = inMemoryRepository(store);
        catalog = new TurSkillCatalogService(storage, repository, new TurSkillFrontmatterParser(), configProperties);
        manager = new TurSkillSandboxSessionManager(storage, catalog);
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(sandboxRoot);
    }

    @Test
    void materializesTheSkillFolderAndCreatesAPersistentWorkspace() throws IOException {
        String id = seedSkill("brand-studio");

        TurSkillSandboxSession session = manager.openSession("agentA", "conv1", id);

        assertThat(session.skillId()).isEqualTo(id);
        assertThat(session.agentId()).isEqualTo("agentA");
        assertThat(session.conversationId()).isEqualTo("conv1");
        assertThat(session.skillDir()).isDirectory();
        assertThat(session.workspaceDir()).isDirectory();
        // The SKILL.md and a reference file were copied in.
        assertThat(session.skillDir().toPath().resolve("SKILL.md")).exists();
        assertThat(session.skillDir().toPath().resolve("references/guide.md")).exists();
        assertThat(Files.readString(session.skillDir().toPath().resolve("references/guide.md")))
                .contains("reference body");
    }

    @Test
    void reusesTheSameDirectoriesForTheSameKey() throws IOException {
        String id = seedSkill("reuse-me");

        TurSkillSandboxSession first = manager.openSession("agentA", "conv1", id);
        // Simulate a file written by a previous turn's command.
        Files.writeString(first.workspaceDir().toPath().resolve("draft.txt"), "turn-1");

        TurSkillSandboxSession second = manager.openSession("agentA", "conv1", id);

        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.workspaceDir()).isEqualTo(first.workspaceDir());
        assertThat(Files.readString(second.workspaceDir().toPath().resolve("draft.txt"))).isEqualTo("turn-1");
    }

    @Test
    void keepsDifferentConversationsIsolated() throws IOException {
        String id = seedSkill("isolated");

        TurSkillSandboxSession a = manager.openSession("agentA", "conv1", id);
        TurSkillSandboxSession b = manager.openSession("agentA", "conv2", id);

        assertThat(a.sessionDir()).isNotEqualTo(b.sessionDir());
        assertThat(a.sessionId()).isNotEqualTo(b.sessionId());
    }

    @Test
    void blankAgentAndConversationFallBackToTheDefaultBucket() throws IOException {
        String id = seedSkill("debug");

        TurSkillSandboxSession session = manager.openSession(null, "  ", id);

        assertThat(session.agentId()).isEqualTo("default");
        assertThat(session.conversationId()).isEqualTo("default");
    }

    @Test
    void throwsForAnUnknownSkill() {
        assertThatThrownBy(() -> manager.openSession("a", "c", "does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown skill");
    }

    @Test
    void staysInertWhenStorageIsDisabled() {
        TurStorageService disabled = mock(TurStorageService.class);
        when(disabled.isEnabled()).thenReturn(false);
        TurSkillSandboxSessionManager inert = new TurSkillSandboxSessionManager(disabled, catalog);

        assertThat(inert.isStorageEnabled()).isFalse();
        assertThatThrownBy(() -> inert.openSession("a", "c", "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("object storage");
    }

    // ---- helpers -------------------------------------------------------------

    /** Write a minimal Anthropic-compatible skill folder and reindex it. */
    private String seedSkill(String folder) throws IOException {
        Path dir = storageDir.resolve("skills").resolve(folder);
        Files.createDirectories(dir.resolve("references"));
        Files.writeString(dir.resolve("SKILL.md"), """
                ---
                name: %s
                description: A test skill.
                ---
                # %s
                """.formatted(folder, folder));
        Files.writeString(dir.resolve("references").resolve("guide.md"), "reference body");
        catalog.reindex();
        return store.values().stream()
                .filter(s -> ("skills/" + folder).equals(s.getPath()))
                .findFirst().orElseThrow().getId();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static TurSkillRepository inMemoryRepository(Map<String, TurSkill> backing) {
        TurSkillRepository repository = mock(TurSkillRepository.class);
        when(repository.findAll()).thenAnswer(inv -> List.copyOf(backing.values()));
        when(repository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(backing.get(inv.getArgument(0))));
        when(repository.findByPath(anyString())).thenAnswer(inv -> backing.values().stream()
                .filter(s -> inv.getArgument(0).equals(s.getPath())).findFirst());
        when(repository.save(any(TurSkill.class))).thenAnswer(inv -> {
            TurSkill skill = inv.getArgument(0);
            if (skill.getId() == null) {
                skill.setId(UUID.randomUUID().toString());
            }
            backing.put(skill.getId(), skill);
            return skill;
        });
        org.mockito.Mockito.doAnswer(inv -> {
            backing.remove(inv.getArgument(0, String.class));
            return null;
        }).when(repository).delete(anyString());
        return repository;
    }
}
