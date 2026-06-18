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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.skill.TurSkillRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import com.viglet.turing.service.storage.TurFilesystemStorageService;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T317 / §IX.4.d — verifies {@link TurSkillCatalogService} reconciles the thin
 * skill index against real skill folders in storage: it indexes folders with a
 * valid {@code SKILL.md}, refreshes them in place (preserving {@code enabled}),
 * skips folders whose {@code SKILL.md} has no name, prunes rows whose folder
 * disappeared, and stays inert when storage is disabled.
 *
 * <p>Backed by the real {@link TurFilesystemStorageService} over a {@code @TempDir}
 * (its filesystem path is set via config and {@code init()} is called directly)
 * and an in-memory {@link TurSkillRepository} stub.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSkillCatalogServiceTest {

    @TempDir
    Path tempDir;

    private TurSkillCatalogService catalog;
    private TurFilesystemStorageService storage;
    private Map<String, TurSkill> store;

    @BeforeEach
    void setUp() {
        TurConfigProperties configProperties = new TurConfigProperties();
        TurStorageProperty storageProperty = new TurStorageProperty();
        storageProperty.setSkillsPath("skills");
        TurStorageProperty.TurFilesystemProperty fs = new TurStorageProperty.TurFilesystemProperty();
        fs.setPath(tempDir.toAbsolutePath().normalize().toString());
        storageProperty.setFilesystem(fs);
        configProperties.setStorage(storageProperty);

        storage = new TurFilesystemStorageService(configProperties);
        storage.init();

        store = new LinkedHashMap<>();
        TurSkillRepository repository = inMemoryRepository(store);

        catalog = new TurSkillCatalogService(storage, repository, new TurSkillFrontmatterParser(), configProperties);
    }

    @Test
    void indexesAFolderThatHasAValidSkillMd() throws IOException {
        writeSkill("brand-content-studio", """
                ---
                name: brand-content-studio
                description: Compose on-brand content.
                metadata:
                  version: 2.3.0
                  authors:
                    - acme-content-team
                ---
                # body
                """);

        int indexed = catalog.reindex();

        assertThat(indexed).isEqualTo(1);
        assertThat(store).hasSize(1);
        TurSkill skill = store.values().iterator().next();
        assertThat(skill.getName()).isEqualTo("brand-content-studio");
        assertThat(skill.getPath()).isEqualTo("skills/brand-content-studio");
        assertThat(skill.getVersion()).isEqualTo("2.3.0");
        assertThat(skill.getAuthor()).isEqualTo("acme-content-team");
        assertThat(skill.getEnabled()).isEqualTo(1);
    }

    @Test
    void skipsAFolderWhoseSkillMdHasNoName() throws IOException {
        writeSkill("broken", """
                ---
                description: no name here
                ---
                """);

        assertThat(catalog.reindex()).isZero();
        assertThat(store).isEmpty();
    }

    @Test
    void skipsAFolderWithoutASkillMd() throws IOException {
        Files.createDirectories(tempDir.resolve("skills").resolve("not-a-skill"));
        Files.writeString(tempDir.resolve("skills").resolve("not-a-skill").resolve("README.md"), "hi");

        assertThat(catalog.reindex()).isZero();
        assertThat(store).isEmpty();
    }

    @Test
    void refreshesAnExistingRowInPlaceAndPreservesEnabledFlag() throws IOException {
        writeSkill("evolving", "---\nname: evolving\nmetadata:\n  version: 1.0.0\n---\n");
        catalog.reindex();
        TurSkill first = store.values().iterator().next();
        String idBefore = first.getId();
        first.setEnabled(0); // user disabled it

        // The folder's SKILL.md is bumped to a new version.
        writeSkill("evolving", "---\nname: evolving\nmetadata:\n  version: 2.0.0\n---\n");
        catalog.reindex();

        assertThat(store).hasSize(1);
        TurSkill after = store.values().iterator().next();
        assertThat(after.getId()).isEqualTo(idBefore);
        assertThat(after.getVersion()).isEqualTo("2.0.0");
        assertThat(after.getEnabled()).isZero();
    }

    @Test
    void prunesIndexRowsWhoseFolderDisappeared() throws IOException {
        writeSkill("stays", "---\nname: stays\n---\n");
        writeSkill("goes", "---\nname: goes\n---\n");
        assertThat(catalog.reindex()).isEqualTo(2);

        // Remove one folder, then reindex.
        deleteRecursively(tempDir.resolve("skills").resolve("goes"));
        assertThat(catalog.reindex()).isEqualTo(1);
        assertThat(store).hasSize(1);
        assertThat(store.values().iterator().next().getName()).isEqualTo("stays");
    }

    @Test
    void returnsMarkdownForAnIndexedSkill() throws IOException {
        writeSkill("readable", "---\nname: readable\n---\n# hello\n");
        catalog.reindex();
        String id = store.values().iterator().next().getId();

        assertThat(catalog.getSkillMarkdown(id)).get().asString().contains("# hello");
    }

    @Test
    void toggleEnabledFlipsTheStoredValue() throws IOException {
        writeSkill("toggle-me", "---\nname: toggle-me\n---\n");
        catalog.reindex();
        String id = store.values().iterator().next().getId();

        assertThat(catalog.setEnabled(id, false)).get().extracting(TurSkill::getEnabled).isEqualTo(0);
        assertThat(catalog.setEnabled(id, true)).get().extracting(TurSkill::getEnabled).isEqualTo(1);
    }

    @Test
    void staysInertWhenStorageIsDisabled() {
        TurStorageService disabled = mock(TurStorageService.class);
        when(disabled.isEnabled()).thenReturn(false);
        TurSkillCatalogService inert = new TurSkillCatalogService(
                disabled, inMemoryRepository(store), new TurSkillFrontmatterParser(), new TurConfigProperties());

        assertThat(inert.isEnabled()).isFalse();
        assertThat(inert.reindex()).isZero();
        assertThat(inert.listAll()).isEmpty();
        assertThat(inert.findById("anything")).isEmpty();
    }

    // ---- helpers -------------------------------------------------------------

    private void writeSkill(String folder, String skillMd) throws IOException {
        Path dir = tempDir.resolve("skills").resolve(folder);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), skillMd);
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

    /** Minimal Mockito-backed repository whose stubs read/write the given map. */
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
