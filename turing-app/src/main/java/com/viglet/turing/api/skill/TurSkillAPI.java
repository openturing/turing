/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.skill;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.TurSkillFileService;
import com.viglet.turing.persistence.model.skill.TurSkill;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T317 / §IX.4.d — read / reindex / enable API over the thin {@link TurSkill}
 * catalog index. The folder editor (T318) and chat/SN activation harness
 * (T322+) are thin clients over these endpoints.
 *
 * <p>All endpoints degrade gracefully when storage is disabled: listing
 * returns empty and {@code reindex} reports {@code indexed=0}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/skill")
@Tag(name = "Skill", description = "Anthropic-compatible skill folders — thin catalog index (T317)")
public class TurSkillAPI {

    private final TurSkillCatalogService catalogService;
    private final TurSkillFileService fileService;
    private final com.viglet.turing.genai.skill.ui.TurSkillUiService skillUiService;

    public TurSkillAPI(TurSkillCatalogService catalogService, TurSkillFileService fileService,
            com.viglet.turing.genai.skill.ui.TurSkillUiService skillUiService) {
        this.catalogService = catalogService;
        this.fileService = fileService;
        this.skillUiService = skillUiService;
    }

    /** Compact view of an indexed skill — enough to render a catalog card. */
    public record TurSkillSummary(String id, String name, String version, String author,
            String description, String path, boolean enabled) {
        static TurSkillSummary of(TurSkill skill) {
            return new TurSkillSummary(skill.getId(), skill.getName(), skill.getVersion(),
                    skill.getAuthor(), skill.getDescription(), skill.getPath(), skill.getEnabled() == 1);
        }
    }

    public record ReindexResponse(boolean enabled, int indexed) {
    }

    @Operation(summary = "List indexed skills")
    @GetMapping
    public List<TurSkillSummary> list() {
        return catalogService.listAll().stream().map(TurSkillSummary::of).toList();
    }

    @Operation(summary = "Show one indexed skill")
    @GetMapping("/{id}")
    public ResponseEntity<TurSkillSummary> get(@PathVariable String id) {
        return catalogService.findById(id)
                .map(TurSkillSummary::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "T449 — list the UI components a skill ships (ui/components.json)")
    @GetMapping("/{id}/ui-components")
    public List<com.viglet.turing.genai.skill.ui.TurSkillUiComponent> uiComponents(
            @PathVariable String id) {
        return skillUiService.listComponents(id);
    }

    @Operation(summary = "Get the raw SKILL.md of an indexed skill")
    @GetMapping(value = "/{id}/skill-md", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> skillMarkdown(@PathVariable String id) {
        return catalogService.getSkillMarkdown(id)
                .map(md -> ResponseEntity.ok().contentType(MediaType.valueOf("text/markdown;charset=UTF-8")).body(md))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Re-scan storage and rebuild the skill index")
    @PostMapping("/reindex")
    public ReindexResponse reindex() {
        return new ReindexResponse(catalogService.isEnabled(), catalogService.reindex());
    }

    @Operation(summary = "Enable or disable an indexed skill")
    @PutMapping("/{id}/enabled")
    public ResponseEntity<TurSkillSummary> setEnabled(@PathVariable String id,
            @RequestParam("value") boolean value) {
        return catalogService.setEnabled(id, value)
                .map(TurSkillSummary::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---- T318: skill folder editor (mini-VS-Code) ---------------------------

    /** A file's relative path plus its UTF-8 text contents. */
    public record TurSkillFileContent(String path, String content) {
    }

    /** Write payload — the file's new UTF-8 text contents (path is a query param). */
    public record TurSkillFileWrite(String content) {
    }

    @Operation(summary = "Create a new, empty skill folder with a starter SKILL.md")
    @PostMapping
    public ResponseEntity<TurSkillSummary> create(@RequestParam("name") String name) {
        return ResponseEntity.ok(TurSkillSummary.of(fileService.createSkill(name)));
    }

    @Operation(summary = "List the file tree of a skill folder (paths relative to the skill root)")
    @GetMapping("/{id}/files")
    public List<TurSkillFileNode> files(@PathVariable String id) {
        return fileService.listFiles(id);
    }

    @Operation(summary = "Read a file inside the skill folder")
    @GetMapping("/{id}/file")
    public ResponseEntity<TurSkillFileContent> readFile(@PathVariable String id,
            @RequestParam("path") String path) {
        return fileService.readFile(id, path)
                .map(content -> ResponseEntity.ok(new TurSkillFileContent(path, content)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create or overwrite a file inside the skill folder")
    @PutMapping("/{id}/file")
    public ResponseEntity<Void> writeFile(@PathVariable String id, @RequestParam("path") String path,
            @RequestBody(required = false) TurSkillFileWrite body) {
        fileService.writeFile(id, path, body == null || body.content() == null ? "" : body.content());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Create a folder inside the skill folder")
    @PostMapping("/{id}/folder")
    public ResponseEntity<Void> createFolder(@PathVariable String id, @RequestParam("path") String path) {
        fileService.createFolder(id, path);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Rename/move a file or folder inside the skill folder")
    @PostMapping("/{id}/rename")
    public ResponseEntity<Void> rename(@PathVariable String id, @RequestParam("from") String from,
            @RequestParam("to") String to) {
        fileService.rename(id, from, to);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete a file or folder inside the skill folder")
    @DeleteMapping("/{id}/file")
    public ResponseEntity<Void> deleteFile(@PathVariable String id, @RequestParam("path") String path) {
        fileService.deletePath(id, path);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Import one or many skills from an uploaded ZIP")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<String> importZip(@RequestParam("file") MultipartFile file) {
        return fileService.importZip(file);
    }

    @Operation(summary = "Export a skill folder as an Anthropic-compatible ZIP")
    @GetMapping(value = "/{id}/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportZip(@PathVariable String id) {
        byte[] zip = fileService.exportZip(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileService.exportFileName(id) + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String onBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String onConflict(IllegalStateException e) {
        return e.getMessage();
    }
}
