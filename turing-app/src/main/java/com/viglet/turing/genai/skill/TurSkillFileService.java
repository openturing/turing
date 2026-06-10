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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.api.skill.TurSkillFileNode;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.skill.TurSkillRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import com.viglet.turing.service.storage.TurStorageService;

/**
 * T318 / §IX.4.d — file-system CRUD over a skill <em>folder</em> in object
 * storage, plus skill creation and ZIP import (the import half of T319).
 *
 * <p>Every operation that takes a caller-supplied path is <strong>scoped to a
 * single skill's root folder</strong>: paths are relative to the skill root and
 * run through {@link #safeRelative(String)}, which rejects {@code ..}, absolute
 * paths and back-slashes. There is therefore no way for the editor to read or
 * write outside the skill folder — it is isolated to its own branch and cannot
 * climb back up to the storage root.
 *
 * <p>The {@code SKILL.md} at the skill root is the source of truth for the thin
 * catalog index ({@link TurSkill}); after any mutation that can affect it
 * (writing/deleting {@code SKILL.md}, creating a skill, importing a ZIP) this
 * service calls {@link TurSkillCatalogService#reindex()} so the index stays in
 * sync.
 *
 * <p>Gated on storage exactly like {@link TurSkillCatalogService}: when storage
 * is disabled there is nowhere for skill folders to live and every mutation
 * throws {@link IllegalStateException}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurSkillFileService {

    private static final Logger log = LoggerFactory.getLogger(TurSkillFileService.class);
    private static final String SKILL_FILE = "SKILL.md";
    private static final String DEFAULT_SKILLS_PATH = "skills";
    private static final int MAX_DEPTH = 32;

    private final TurStorageService storageService;
    private final TurSkillRepository skillRepository;
    private final TurSkillCatalogService catalogService;
    private final TurSkillFrontmatterParser frontmatterParser;
    private final TurConfigProperties configProperties;

    public TurSkillFileService(TurStorageService storageService, TurSkillRepository skillRepository,
            TurSkillCatalogService catalogService, TurSkillFrontmatterParser frontmatterParser,
            TurConfigProperties configProperties) {
        this.storageService = storageService;
        this.skillRepository = skillRepository;
        this.catalogService = catalogService;
        this.frontmatterParser = frontmatterParser;
        this.configProperties = configProperties;
    }

    public boolean isEnabled() {
        return storageService.isEnabled();
    }

    // ---- file tree ----------------------------------------------------------

    /**
     * The recursive file tree of a skill folder, as a flat list of nodes whose
     * paths are relative to the skill root. Empty when the skill is unknown.
     */
    public List<TurSkillFileNode> listFiles(String skillId) {
        Optional<TurSkill> skill = catalogService.findById(skillId);
        if (skill.isEmpty()) {
            return List.of();
        }
        String root = stripTrailingSlash(skill.get().getPath());
        List<TurSkillFileNode> nodes = new ArrayList<>();
        walk(root, root, nodes, 0);
        nodes.sort(TurSkillFileService::compareNodes);
        return nodes;
    }

    private void walk(String root, String folder, List<TurSkillFileNode> out, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        for (TurAssetItem item : storageService.listObjects(folder + "/")) {
            String objectName = stripTrailingSlash(item.name());
            String relative = relativize(root, objectName);
            if (relative.isEmpty()) {
                continue;
            }
            out.add(new TurSkillFileNode(relative, lastSegment(relative), item.directory(),
                    item.directory() ? 0L : item.size()));
            if (item.directory()) {
                walk(root, objectName, out, depth + 1);
            }
        }
    }

    // ---- read / write -------------------------------------------------------

    /** UTF-8 text contents of a file inside the skill, or empty when absent. */
    public Optional<String> readFile(String skillId, String relativePath) {
        String objectName = resolve(skillId, relativePath);
        try (InputStream in = storageService.downloadObject(objectName)) {
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("[Skills] Cannot read '{}': {}", objectName, e.getMessage());
            return Optional.empty();
        }
    }

    /** Create or overwrite a text file inside the skill. */
    public void writeFile(String skillId, String relativePath, String content) {
        String safe = safeRelative(relativePath);
        String objectName = resolve(skillId, safe);
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        storageService.uploadStream(objectName, new ByteArrayInputStream(bytes), bytes.length,
                storageService.guessContentType(safe));
        reindexIfSkillMd(safe);
    }

    /** Create an empty folder inside the skill (a no-op for back-ends that have no real folders). */
    public void createFolder(String skillId, String relativePath) {
        String objectName = resolve(skillId, safeRelative(relativePath));
        storageService.createFolder(objectName + "/");
    }

    /** Delete a file or, recursively, a folder inside the skill. */
    public void deletePath(String skillId, String relativePath) {
        String safe = safeRelative(relativePath);
        String objectName = resolve(skillId, safe);
        // Delete both the object itself and anything beneath it (folder case).
        storageService.deleteObjectsWithPrefix(objectName + "/");
        storageService.deleteObject(objectName);
        reindexIfSkillMd(safe);
    }

    /** Move/rename a file or folder within the skill. */
    public void rename(String skillId, String fromRelative, String toRelative) {
        TurSkill skill = requireSkill(skillId);
        String root = stripTrailingSlash(skill.getPath());
        String fromSafe = safeRelative(fromRelative);
        String toSafe = safeRelative(toRelative);
        String fromObject = root + "/" + fromSafe;
        String toObject = root + "/" + toSafe;

        List<TurSkillFileNode> tree = listFiles(skillId);
        boolean isFolder = tree.stream().anyMatch(n -> n.path().equals(fromSafe) && n.directory());
        if (isFolder) {
            for (TurSkillFileNode node : tree) {
                if (!node.directory() && (node.path().equals(fromSafe) || node.path().startsWith(fromSafe + "/"))) {
                    String tail = node.path().substring(fromSafe.length());
                    moveObject(root + "/" + node.path(), toObject + tail);
                }
            }
            storageService.deleteObjectsWithPrefix(fromObject + "/");
        } else {
            moveObject(fromObject, toObject);
        }
        if (fromSafe.equals(SKILL_FILE) || toSafe.equals(SKILL_FILE)) {
            catalogService.reindex();
        }
    }

    private void moveObject(String fromObject, String toObject) {
        if (fromObject.equals(toObject)) {
            return;
        }
        try (InputStream in = storageService.downloadObject(fromObject)) {
            byte[] bytes = in.readAllBytes();
            storageService.uploadStream(toObject, new ByteArrayInputStream(bytes), bytes.length,
                    storageService.guessContentType(toObject));
        } catch (IOException e) {
            throw new IllegalStateException("Could not move " + fromObject, e);
        }
        storageService.deleteObject(fromObject);
    }

    // ---- create skill -------------------------------------------------------

    /**
     * Create a brand-new skill folder with a starter {@code SKILL.md}, then
     * reindex. The folder name is the slugified {@code name}; a numeric suffix is
     * appended when that folder already exists.
     *
     * @return the freshly indexed {@link TurSkill}
     */
    public TurSkill createSkill(String name) {
        requireEnabled();
        String slug = uniqueFolder(slug(name));
        String folderPath = skillsPrefix() + slug;
        String content = starterSkillMd(slug, name);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        storageService.uploadStream(folderPath + "/" + SKILL_FILE, new ByteArrayInputStream(bytes), bytes.length,
                "text/markdown");
        catalogService.reindex();
        return skillRepository.findByPath(folderPath)
                .orElseThrow(() -> new IllegalStateException("Skill folder created but not indexed: " + folderPath));
    }

    private String starterSkillMd(String slug, String displayName) {
        String title = (displayName == null || displayName.isBlank()) ? slug : displayName.trim();
        return """
                ---
                name: %s
                description: Describe what this skill does and, crucially, WHEN it should be used.
                metadata:
                  version: 0.1.0
                ---

                # %s

                Explain the skill here. Reference supporting files under `references/`,
                executable helpers under `scripts/`, and static files under `assets/`.
                """.formatted(slug, title);
    }

    // ---- export ZIP ---------------------------------------------------------

    /**
     * Serialize a skill folder into an Anthropic-compatible ZIP: every file is
     * written under a single top-level folder named after the skill, so the
     * archive both uploads to Claude and round-trips back through
     * {@link #importZip(MultipartFile)} (a top-level folder carrying a
     * {@code SKILL.md}).
     *
     * @return the ZIP bytes (skill folders are small, so an in-memory buffer is fine)
     */
    public byte[] exportZip(String skillId) {
        TurSkill skill = requireSkill(skillId);
        String root = stripTrailingSlash(skill.getPath());
        String topFolder = slug(skill.getName() != null && !skill.getName().isBlank()
                ? skill.getName()
                : lastSegment(root));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            for (TurSkillFileNode node : listFiles(skillId)) {
                if (node.directory()) {
                    continue;
                }
                try (InputStream in = storageService.downloadObject(root + "/" + node.path())) {
                    zos.putNextEntry(new ZipEntry(topFolder + "/" + node.path()));
                    in.transferTo(zos);
                    zos.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the skill export ZIP.", e);
        }
        return buffer.toByteArray();
    }

    /** Suggested download file name for {@link #exportZip(String)}. */
    public String exportFileName(String skillId) {
        TurSkill skill = requireSkill(skillId);
        return slug(skill.getName() != null && !skill.getName().isBlank()
                ? skill.getName()
                : lastSegment(stripTrailingSlash(skill.getPath()))) + ".zip";
    }

    // ---- import ZIP ---------------------------------------------------------

    /**
     * Unpack an uploaded ZIP into the skills storage prefix. A single ZIP may
     * carry many skills: every folder containing a {@code SKILL.md} becomes its
     * own skill folder (the entry door of §IX.4.d). When {@code SKILL.md} sits at
     * the ZIP root, the whole archive is treated as one skill.
     *
     * @return the destination folder slugs that were imported
     */
    public List<String> importZip(MultipartFile file) {
        requireEnabled();
        Map<String, byte[]> entries = readZip(file);
        Set<String> roots = detectSkillRoots(entries.keySet());
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("No SKILL.md found in the uploaded ZIP.");
        }
        List<String> imported = new ArrayList<>();
        for (String root : roots) {
            String slug = uniqueFolder(destinationSlug(root, entries, file));
            String destPrefix = skillsPrefix() + slug + "/";
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String entryPath = entry.getKey();
                if (!underRoot(root, entryPath)) {
                    continue;
                }
                String within = root.isEmpty() ? entryPath : entryPath.substring(root.length() + 1);
                if (within.isBlank()) {
                    continue;
                }
                byte[] data = entry.getValue();
                String objectName = destPrefix + within;
                storageService.uploadStream(objectName, new ByteArrayInputStream(data), data.length,
                        storageService.guessContentType(within));
            }
            imported.add(slug);
        }
        catalogService.reindex();
        log.info("[Skills] Imported {} skill(s) from ZIP: {}", imported.size(), imported);
        return imported;
    }

    /** Read the ZIP fully into memory, skipping directories and Mac/hidden junk. */
    private Map<String, byte[]> readZip(MultipartFile file) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || isJunk(name)) {
                    zis.closeEntry();
                    continue;
                }
                entries.put(stripLeadingSlash(name), zis.readAllBytes());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded ZIP.", e);
        }
        return entries;
    }

    /** Top-most folders that hold a {@code SKILL.md}; {@code ""} means the ZIP root. */
    private Set<String> detectSkillRoots(Set<String> paths) {
        Set<String> roots = new LinkedHashSet<>();
        for (String path : paths) {
            if (path.equals(SKILL_FILE)) {
                return Set.of(""); // root skill — the whole archive is one skill
            }
            if (path.endsWith("/" + SKILL_FILE)) {
                roots.add(path.substring(0, path.length() - SKILL_FILE.length() - 1));
            }
        }
        // Drop roots nested under another root, so a skill referencing a nested
        // SKILL.md isn't split into two.
        Set<String> topMost = new LinkedHashSet<>();
        for (String candidate : roots) {
            boolean nested = roots.stream().anyMatch(other -> !other.equals(candidate)
                    && candidate.startsWith(other + "/"));
            if (!nested) {
                topMost.add(candidate);
            }
        }
        return topMost;
    }

    private boolean underRoot(String root, String entryPath) {
        return root.isEmpty() || entryPath.equals(root) || entryPath.startsWith(root + "/");
    }

    /** Prefer the skill's frontmatter {@code name}, else the folder/zip name. */
    private String destinationSlug(String root, Map<String, byte[]> entries, MultipartFile file) {
        byte[] skillMd = entries.get(root.isEmpty() ? SKILL_FILE : root + "/" + SKILL_FILE);
        if (skillMd != null) {
            TurSkillFrontmatter fm = frontmatterParser.parse(new String(skillMd, StandardCharsets.UTF_8));
            if (fm.isValid()) {
                return slug(fm.name());
            }
        }
        if (!root.isEmpty()) {
            return slug(lastSegment(root));
        }
        String filename = file.getOriginalFilename();
        return slug(filename == null ? "imported-skill" : filename.replaceFirst("(?i)\\.zip$", ""));
    }

    // ---- path safety / helpers ---------------------------------------------

    /** Resolve a caller-supplied relative path to a storage object inside the skill. */
    private String resolve(String skillId, String relativePath) {
        TurSkill skill = requireSkill(skillId);
        return stripTrailingSlash(skill.getPath()) + "/" + safeRelative(relativePath);
    }

    private TurSkill requireSkill(String skillId) {
        requireEnabled();
        return catalogService.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Skills require object storage to be configured.");
        }
    }

    private void reindexIfSkillMd(String safeRelativePath) {
        if (SKILL_FILE.equals(safeRelativePath)) {
            catalogService.reindex();
        }
    }

    /**
     * Validate a path is strictly inside the skill folder: no absolute paths, no
     * back-slashes, no {@code .}/{@code ..} segments. Returns the normalised
     * forward-slash relative path.
     */
    static String safeRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Path is required.");
        }
        String normalized = relativePath.replace('\\', '/').trim();
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Path must be relative to the skill root: " + relativePath);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Path traversal is not allowed: " + relativePath);
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Path is required.");
        }
        return String.join("/", segments);
    }

    private String uniqueFolder(String slug) {
        String prefix = skillsPrefix();
        String candidate = slug;
        int n = 2;
        while (skillRepository.findByPath(prefix + candidate).isPresent()
                || folderExists(prefix + candidate)) {
            candidate = slug + "-" + n++;
        }
        return candidate;
    }

    private boolean folderExists(String folderPath) {
        return storageService.listObjects(folderPath + "/").stream().findAny().isPresent();
    }

    private static String slug(String value) {
        String base = (value == null ? "" : value).trim().toLowerCase();
        base = base.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        return base.isEmpty() ? "skill" : base;
    }

    private String skillsPrefix() {
        TurStorageProperty storage = configProperties.getStorage();
        String path = (storage != null && storage.getSkillsPath() != null && !storage.getSkillsPath().isBlank())
                ? storage.getSkillsPath()
                : DEFAULT_SKILLS_PATH;
        return stripTrailingSlash(path.trim()) + "/";
    }

    private static String relativize(String root, String objectName) {
        if (objectName.equals(root)) {
            return "";
        }
        return objectName.startsWith(root + "/") ? objectName.substring(root.length() + 1) : "";
    }

    private static int compareNodes(TurSkillFileNode a, TurSkillFileNode b) {
        return a.path().compareToIgnoreCase(b.path());
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static boolean isJunk(String name) {
        return name.startsWith("__MACOSX") || name.contains("/.") || name.startsWith(".");
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
