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

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
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

    public TurSkillAPI(TurSkillCatalogService catalogService) {
        this.catalogService = catalogService;
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
}
