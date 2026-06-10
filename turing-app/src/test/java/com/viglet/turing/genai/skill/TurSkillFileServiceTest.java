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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.api.skill.TurSkillFileNode;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.skill.TurSkillRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import com.viglet.turing.service.storage.TurFilesystemStorageService;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T318 / §IX.4.d — verifies {@link TurSkillFileService} performs file CRUD that
 * is strictly scoped to a skill's root folder, creates new skills with a
 * starter {@code SKILL.md}, and imports single- and multi-skill ZIPs. Backed by
 * the real {@link TurFilesystemStorageService} over a {@code @TempDir} plus an
 * in-memory {@link TurSkillRepository} stub (same harness as
 * {@link TurSkillCatalogServiceTest}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSkillFileServiceTest {

    @TempDir
    Path tempDir;

    private TurSkillFileService fileService;
    private TurSkillCatalogService catalog;
    private Map<String, TurSkill> store;

    @BeforeEach
    void setUp() {
        TurConfigProperties configProperties = new TurConfigProperties();
        TurStorageProperty storageProperty = new TurStorageProperty();
        storageProperty.setSkillsPath("skills");
        configProperties.setStorage(storageProperty);

        TurFilesystemStorageService storage = new TurFilesystemStorageService(configProperties);
        ReflectionTestUtils.setField(storage, "basePath", tempDir.toAbsolutePath().normalize());

        store = new LinkedHashMap<>();
        TurSkillRepository repository = inMemoryRepository(store);
        TurSkillFrontmatterParser parser = new TurSkillFrontmatterParser();
        catalog = new TurSkillCatalogService(storage, repository, parser, configProperties);
        fileService = new TurSkillFileService(storage, repository, catalog, parser, configProperties);
    }

    @Test
    void createSkillWritesAStarterSkillMdAndIndexesIt() {
        TurSkill skill = fileService.createSkill("My First Skill");

        assertThat(skill.getName()).isEqualTo("my-first-skill");
        assertThat(skill.getPath()).isEqualTo("skills/my-first-skill");
        assertThat(fileService.readFile(skill.getId(), "SKILL.md")).get().asString()
                .contains("name: my-first-skill")
                .contains("# My First Skill");
    }

    @Test
    void createSkillDeduplicatesTheFolderSlug() {
        TurSkill first = fileService.createSkill("Docs");
        TurSkill second = fileService.createSkill("Docs");

        assertThat(first.getPath()).isEqualTo("skills/docs");
        assertThat(second.getPath()).isEqualTo("skills/docs-2");
    }

    @Test
    void listFilesReturnsTreeRelativeToTheSkillRoot() {
        TurSkill skill = fileService.createSkill("tree");
        fileService.writeFile(skill.getId(), "scripts/build.py", "print('hi')");
        fileService.writeFile(skill.getId(), "references/guide.md", "# guide");

        List<String> paths = fileService.listFiles(skill.getId()).stream().map(TurSkillFileNode::path).toList();

        assertThat(paths).contains("SKILL.md", "scripts", "scripts/build.py", "references", "references/guide.md");
        // Paths are relative — none leaks the storage prefix.
        assertThat(paths).noneMatch(p -> p.startsWith("skills/"));
    }

    @Test
    void writeFileReadFileRoundTrips() {
        TurSkill skill = fileService.createSkill("rw");
        fileService.writeFile(skill.getId(), "notes.txt", "hello world");

        assertThat(fileService.readFile(skill.getId(), "notes.txt")).contains("hello world");
    }

    @Test
    void writingSkillMdRefreshesTheIndex() {
        TurSkill skill = fileService.createSkill("evolving");
        fileService.writeFile(skill.getId(), "SKILL.md", "---\nname: evolving\nmetadata:\n  version: 9.9.9\n---\n");

        assertThat(catalog.findById(skill.getId())).get().extracting(TurSkill::getVersion).isEqualTo("9.9.9");
    }

    @Test
    void deletePathRemovesAFile() {
        TurSkill skill = fileService.createSkill("del");
        fileService.writeFile(skill.getId(), "scripts/tmp.py", "x = 1");
        fileService.deletePath(skill.getId(), "scripts/tmp.py");

        assertThat(fileService.readFile(skill.getId(), "scripts/tmp.py")).isEmpty();
    }

    @Test
    void renameMovesAFileWithinTheSkill() {
        TurSkill skill = fileService.createSkill("ren");
        fileService.writeFile(skill.getId(), "old.txt", "data");
        fileService.rename(skill.getId(), "old.txt", "docs/new.txt");

        assertThat(fileService.readFile(skill.getId(), "old.txt")).isEmpty();
        assertThat(fileService.readFile(skill.getId(), "docs/new.txt")).contains("data");
    }

    @Test
    void pathTraversalIsRejected() {
        TurSkill skill = fileService.createSkill("safe");
        assertThatThrownBy(() -> fileService.writeFile(skill.getId(), "../escape.txt", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fileService.readFile(skill.getId(), "/etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importSingleSkillZipWithRootSkillMd() throws IOException {
        byte[] zip = zip(Map.of(
                "SKILL.md", "---\nname: imported-one\n---\n# one",
                "scripts/run.py", "print(1)"));

        List<String> imported = fileService.importZip(new MockMultipartFile("file", "one.zip", "application/zip", zip));

        assertThat(imported).containsExactly("imported-one");
        assertThat(store.values()).extracting(TurSkill::getName).contains("imported-one");
        TurSkill skill = store.values().stream().filter(s -> "imported-one".equals(s.getName())).findFirst().orElseThrow();
        assertThat(fileService.readFile(skill.getId(), "scripts/run.py")).contains("print(1)");
    }

    @Test
    void importMultiSkillZipCreatesOneFolderPerSkill() throws IOException {
        byte[] zip = zip(Map.of(
                "alpha/SKILL.md", "---\nname: alpha\n---\n",
                "alpha/data.txt", "a",
                "beta/SKILL.md", "---\nname: beta\n---\n",
                "beta/data.txt", "b"));

        List<String> imported = fileService.importZip(new MockMultipartFile("file", "many.zip", "application/zip", zip));

        assertThat(imported).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(store.values()).extracting(TurSkill::getName).containsExactlyInAnyOrder("alpha", "beta");
    }

    @Test
    void exportZipWrapsFilesUnderASingleSkillFolder() throws IOException {
        TurSkill skill = fileService.createSkill("Exportable");
        fileService.writeFile(skill.getId(), "scripts/run.py", "print('go')");

        Map<String, String> entries = unzip(fileService.exportZip(skill.getId()));

        assertThat(entries).containsKeys("exportable/SKILL.md", "exportable/scripts/run.py");
        assertThat(entries.get("exportable/scripts/run.py")).contains("print('go')");
        assertThat(fileService.exportFileName(skill.getId())).isEqualTo("exportable.zip");
    }

    @Test
    void exportThenImportRoundTrips() throws IOException {
        TurSkill original = fileService.createSkill("round-trip");
        fileService.writeFile(original.getId(), "references/notes.md", "# notes");
        byte[] exported = fileService.exportZip(original.getId());

        List<String> imported = fileService.importZip(
                new MockMultipartFile("file", "round-trip.zip", "application/zip", exported));

        // Re-imported as a fresh, deduplicated folder carrying the same files.
        assertThat(imported).containsExactly("round-trip-2");
        TurSkill copy = store.values().stream().filter(s -> "skills/round-trip-2".equals(s.getPath()))
                .findFirst().orElseThrow();
        assertThat(fileService.readFile(copy.getId(), "references/notes.md")).contains("# notes");
    }

    @Test
    void importZipWithNoSkillMdIsRejected() throws IOException {
        byte[] zip = zip(Map.of("README.md", "not a skill"));
        assertThatThrownBy(() -> fileService.importZip(new MockMultipartFile("file", "x.zip", "application/zip", zip)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mutationsThrowWhenStorageDisabled() {
        TurStorageService disabled = mock(TurStorageService.class);
        when(disabled.isEnabled()).thenReturn(false);
        TurSkillRepository repository = inMemoryRepository(store);
        TurSkillFrontmatterParser parser = new TurSkillFrontmatterParser();
        TurConfigProperties props = new TurConfigProperties();
        TurSkillCatalogService inertCatalog = new TurSkillCatalogService(disabled, repository, parser, props);
        TurSkillFileService inert = new TurSkillFileService(disabled, repository, inertCatalog, parser, props);

        assertThat(inert.isEnabled()).isFalse();
        assertThat(inert.listFiles("anything")).isEmpty();
        assertThatThrownBy(() -> inert.createSkill("x")).isInstanceOf(IllegalStateException.class);
    }

    // ---- helpers -------------------------------------------------------------

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return entries;
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
