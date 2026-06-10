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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.skill.TurSkill;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * T116 / §IX.4 — parse / validate / sign a {@code skill.manifest.json} and map
 * it to and from the persisted {@link TurSkill} entity.
 *
 * <p>The signature is a {@code sha256:<hex>} digest over the manifest content
 * with the {@code signature} field itself excluded, serialized with sorted
 * keys so the digest is stable regardless of authoring key order. A manifest
 * that ships no signature is <em>self-signed</em> on install (the computed
 * digest is stamped onto the entity); a manifest that ships one is verified
 * against the recomputed digest. Hard rejection of bad signatures is deferred
 * to the signed public registry (T118) — here a mismatch is reported, not
 * fatal, so first-party and locally-edited skills install cleanly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSkillManifestService {

    private static final String SHA256_PREFIX = "sha256:";

    /** {@code name} must be a lowercase, kebab-case identifier — safe as a registry slug. */
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** Permissive semver: {@code MAJOR.MINOR.PATCH} with an optional pre-release / build suffix. */
    private static final Pattern SEMVER_PATTERN =
            Pattern.compile("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?");

    /** Lenient mapper for parsing inbound manifests (unknown fields already ignored on the record). */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * Canonical mapper used only for signing: keys sorted alphabetically so the
     * same logical manifest always hashes to the same digest.
     */
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    /** Outcome of comparing a manifest's declared signature against the recomputed digest. */
    public enum SignatureStatus {
        /** No signature declared — will be self-signed at install. */
        UNSIGNED,
        /** Declared signature matches the recomputed digest. */
        VALID,
        /** Declared signature does NOT match — tampered or stale. */
        INVALID
    }

    /** Result of {@link #validate(TurSkillManifest)} — never throws, collects all problems. */
    public record ValidationResult(boolean valid, List<String> errors, SignatureStatus signatureStatus,
            String computedSignature) {
    }

    /**
     * Parse a manifest JSON document. Throws {@link IllegalArgumentException}
     * (wrapping the Jackson cause) on malformed JSON so callers can surface a
     * clean 400 rather than a 500.
     */
    public TurSkillManifest parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty skill manifest.");
        }
        try {
            return MAPPER.readValue(json, TurSkillManifest.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed skill manifest: " + e.getMessage(), e);
        }
    }

    /**
     * Validate required fields and report the signature status. Collects every
     * problem instead of failing on the first so the editor can show them all.
     */
    public ValidationResult validate(TurSkillManifest manifest) {
        List<String> errors = new ArrayList<>();
        if (manifest == null) {
            return new ValidationResult(false, List.of("Manifest is null."), SignatureStatus.UNSIGNED, null);
        }
        if (isBlank(manifest.name())) {
            errors.add("Field 'name' is required.");
        } else if (!NAME_PATTERN.matcher(manifest.name()).matches()) {
            errors.add("Field 'name' must be lowercase kebab-case (e.g. scaffold-markdown).");
        } else if (manifest.name().length() > 100) {
            errors.add("Field 'name' must be at most 100 characters.");
        }
        if (isBlank(manifest.version())) {
            errors.add("Field 'version' is required.");
        } else if (!SEMVER_PATTERN.matcher(manifest.version()).matches()) {
            errors.add("Field 'version' must be semver (e.g. 1.2.0).");
        }
        if (manifest.description() != null && manifest.description().length() > 500) {
            errors.add("Field 'description' must be at most 500 characters.");
        }

        String computed = computeSignature(manifest);
        SignatureStatus status;
        if (isBlank(manifest.signature())) {
            status = SignatureStatus.UNSIGNED;
        } else if (manifest.signature().equals(computed)) {
            status = SignatureStatus.VALID;
        } else {
            status = SignatureStatus.INVALID;
        }
        return new ValidationResult(errors.isEmpty(), errors, status, computed);
    }

    /**
     * Compute the {@code sha256:<hex>} digest of the manifest content with the
     * {@code signature} field excluded, using the canonical (sorted-key)
     * serialization.
     */
    public String computeSignature(TurSkillManifest manifest) {
        TurSkillManifest unsigned = new TurSkillManifest(
                manifest.name(), manifest.version(), manifest.author(), null,
                manifest.description(), manifest.personaFragment(), manifest.flowSnippet(),
                manifest.tools(), manifest.workspaceDefaults(), manifest.dependencies());
        byte[] canonical = CANONICAL_MAPPER.writeValueAsBytes(unsigned);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            StringBuilder hex = new StringBuilder(SHA256_PREFIX.length() + digest.length * 2);
            hex.append(SHA256_PREFIX);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS to be present on every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Map a manifest to a persistable entity, serializing the structured bundle
     * members to their JSON columns and stamping the signature (self-signed
     * when the manifest carried none). {@code builtIn} is decided by the
     * caller, not the manifest.
     */
    public TurSkill toEntity(TurSkillManifest manifest, boolean builtIn) {
        TurSkill skill = new TurSkill();
        skill.setName(manifest.name());
        skill.setVersion(manifest.version());
        skill.setAuthor(manifest.author());
        skill.setDescription(manifest.description());
        skill.setSignature(isBlank(manifest.signature()) ? computeSignature(manifest) : manifest.signature());
        skill.setPersonaFragmentJson(writeOrNull(manifest.personaFragment()));
        skill.setFlowSnippetJson(manifest.flowSnippet() == null || manifest.flowSnippet().isNull()
                ? null : writeOrNull(manifest.flowSnippet()));
        skill.setToolsJson(writeOrNull(manifest.tools()));
        skill.setWorkspaceDefaultsJson(writeOrNull(manifest.workspaceDefaults()));
        skill.setDependenciesJson(writeOrNull(manifest.dependencies()));
        skill.setBuiltIn(builtIn);
        skill.setEnabled(1);
        return skill;
    }

    /** Rehydrate the manifest exchange shape from a persisted entity. */
    public TurSkillManifest toManifest(TurSkill skill) {
        return new TurSkillManifest(
                skill.getName(),
                skill.getVersion(),
                skill.getAuthor(),
                skill.getSignature(),
                skill.getDescription(),
                readOrNull(skill.getPersonaFragmentJson(), TurSkillManifest.PersonaFragment.class),
                readNode(skill.getFlowSnippetJson()),
                readList(skill.getToolsJson()),
                readMap(skill.getWorkspaceDefaultsJson()),
                readList(skill.getDependenciesJson()));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String writeOrNull(Object value) {
        if (value == null) {
            return null;
        }
        return MAPPER.writeValueAsString(value);
    }

    private <T> T readOrNull(String json, Class<T> type) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (RuntimeException e) {
            log.warn("[Skill] Could not parse stored JSON for {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private JsonNode readNode(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (RuntimeException e) {
            log.warn("[Skill] Could not parse stored flow snippet JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<String> readList(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (RuntimeException e) {
            log.warn("[Skill] Could not parse stored list JSON: {}", e.getMessage());
            return null;
        }
    }

    private java.util.Map<String, String> readMap(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<java.util.Map<String, String>>() {});
        } catch (RuntimeException e) {
            log.warn("[Skill] Could not parse stored map JSON: {}", e.getMessage());
            return null;
        }
    }
}
