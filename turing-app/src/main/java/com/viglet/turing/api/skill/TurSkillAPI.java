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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.TurSkillManifest;
import com.viglet.turing.genai.skill.TurSkillManifestService;
import com.viglet.turing.genai.skill.TurSkillManifestService.SignatureStatus;
import com.viglet.turing.genai.skill.TurSkillManifestService.ValidationResult;
import com.viglet.turing.genai.skill.TurSkillService;
import com.viglet.turing.persistence.model.skill.TurSkill;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T116 / §IX.4 — read / install / validate API for {@link TurSkill} bundles and
 * the bundled platform catalog. The browsing / install UI (T117) is a thin
 * client over these endpoints.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/skill")
@Tag(name = "Skill", description = "Skills marketplace API (T116)")
public class TurSkillAPI {

    private final TurSkillService skillService;
    private final TurSkillCatalogService catalogService;
    private final TurSkillManifestService manifestService;

    public TurSkillAPI(TurSkillService skillService, TurSkillCatalogService catalogService,
            TurSkillManifestService manifestService) {
        this.skillService = skillService;
        this.catalogService = catalogService;
        this.manifestService = manifestService;
    }

    /** Compact view of an installed skill — enough to render a marketplace card. */
    public record TurSkillSummary(String id, String name, String version, String author,
            String description, String signature, boolean builtIn, boolean enabled, List<String> tools) {
    }

    public record ValidateManifestResponse(boolean valid, List<String> errors,
            SignatureStatus signatureStatus, String computedSignature) {
    }

    public record InstallSkillResponse(boolean success, String id, String name, String version,
            String error) {
    }

    @Operation(summary = "List installed skills")
    @GetMapping
    public List<TurSkillSummary> list() {
        return skillService.listAll().stream().map(this::toSummary).toList();
    }

    @Operation(summary = "Show an installed skill as a manifest")
    @GetMapping("/{id}")
    public ResponseEntity<TurSkillManifest> get(@PathVariable String id) {
        return skillService.findById(id)
                .map(manifestService::toManifest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "List bundled platform skills (the v1 catalog)")
    @GetMapping("/catalog")
    public List<TurSkillManifest> catalog() {
        return catalogService.list();
    }

    @Operation(summary = "Validate a skill manifest without installing it")
    @PostMapping("/validate")
    public ResponseEntity<ValidateManifestResponse> validate(@RequestBody TurSkillManifest manifest) {
        ValidationResult result = manifestService.validate(manifest);
        return ResponseEntity.ok(new ValidateManifestResponse(
                result.valid(), result.errors(), result.signatureStatus(), result.computedSignature()));
    }

    @Operation(summary = "Install a user-supplied skill manifest")
    @PostMapping("/install")
    public ResponseEntity<InstallSkillResponse> install(@RequestBody TurSkillManifest manifest) {
        try {
            TurSkill saved = skillService.install(manifest, false);
            return ResponseEntity.ok(new InstallSkillResponse(true, saved.getId(),
                    saved.getName(), saved.getVersion(), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new InstallSkillResponse(false, null, null, null, e.getMessage()));
        }
    }

    @Operation(summary = "Install a bundled platform skill by manifest name")
    @PostMapping("/catalog/{name}/install")
    public ResponseEntity<InstallSkillResponse> installFromCatalog(@PathVariable String name) {
        return catalogService.get(name)
                .map(manifest -> {
                    TurSkill saved = skillService.install(manifest, true);
                    return ResponseEntity.ok(new InstallSkillResponse(true, saved.getId(),
                            saved.getName(), saved.getVersion(), null));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new InstallSkillResponse(false, null, null, null,
                                "No bundled skill named '" + name + "'.")));
    }

    @Operation(summary = "Delete a user-installed skill (built-in skills are protected)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> delete(@PathVariable String id) {
        boolean deleted = skillService.delete(id);
        if (deleted) {
            return ResponseEntity.ok(true);
        }
        // Either not found or a protected built-in — 409 distinguishes "exists but undeletable".
        return skillService.findById(id).isPresent()
                ? ResponseEntity.status(HttpStatus.CONFLICT).body(false)
                : ResponseEntity.notFound().build();
    }

    private TurSkillSummary toSummary(TurSkill skill) {
        TurSkillManifest manifest = manifestService.toManifest(skill);
        return new TurSkillSummary(skill.getId(), skill.getName(), skill.getVersion(),
                skill.getAuthor(), skill.getDescription(), skill.getSignature(),
                skill.isBuiltIn(), skill.getEnabled() == 1, manifest.tools());
    }
}
