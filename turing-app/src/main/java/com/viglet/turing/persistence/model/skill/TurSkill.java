/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.skill;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T116 / §IX.4 — an installed <strong>skill</strong>: a typed, versioned,
 * marketplace-distributable bundle of
 * {@code (personaFragment + flowSnippet + tools + workspaceDefaults +
 * dependencies)}. Unlike Deep Agents' loose "skills as filesystem files",
 * Turing models a skill as a first-class entity with a signed manifest so it
 * can be browsed, version-pinned and vendor-filtered (T117) and ultimately
 * published to a public registry (T118).
 *
 * <p>The structured bundle members are persisted as opaque JSON
 * ({@code longtext}) columns rather than relational sub-tables: a skill is an
 * atomic, immutable-per-version artifact, so there is no need to query inside
 * it relationally. The canonical exchange shape is
 * {@link com.viglet.turing.genai.skill.TurSkillManifest}; serialization in and
 * out of these columns is owned by
 * {@link com.viglet.turing.genai.skill.TurSkillManifestService}.
 *
 * <p>{@code (name, version)} is unique — installing the same name at a new
 * version yields a new row, which is what lets the marketplace pin versions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "skill", uniqueConstraints = @UniqueConstraint(
        name = "uq_skill_name_version", columnNames = { "name", "version" }))
public class TurSkill implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Manifest {@code name} — the skill's stable identifier (e.g. {@code scaffold-markdown}). */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Semver string from the manifest (e.g. {@code 1.2.0}). */
    @Column(name = "version", nullable = false, length = 32)
    private String version;

    /** Publishing author / vendor (e.g. {@code viglet}). */
    @Column(name = "author", length = 100)
    private String author;

    /**
     * {@code sha256:<hex>} digest of the manifest content (signature field
     * excluded). For built-in skills authored without a signature this is the
     * self-computed digest stamped at install time; the registry (T118) will
     * carry vendor-signed digests verified on install.
     */
    @Column(name = "signature", length = 100)
    private String signature;

    @Column(name = "description", length = 500)
    private String description;

    /** JSON object: {@code {instruction, vocab:{mandatory[],forbidden[]}}}. */
    @Lob
    @Column(name = "personaFragmentJson")
    private String personaFragmentJson;

    /** JSON: a chat-flow snippet graph (nodes/edges) the skill contributes. */
    @Lob
    @Column(name = "flowSnippetJson")
    private String flowSnippetJson;

    /** JSON array of custom-tool names the skill requires (e.g. {@code ["scaffold.markdown"]}). */
    @Lob
    @Column(name = "toolsJson")
    private String toolsJson;

    /** JSON object mapping workspace file paths to seed templates. */
    @Lob
    @Column(name = "workspaceDefaultsJson")
    private String workspaceDefaultsJson;

    /** JSON array of dependency specifiers (e.g. {@code ["http@^1.0.0"]}). */
    @Lob
    @Column(name = "dependenciesJson")
    private String dependenciesJson;

    /**
     * {@code true} for skills shipped inside the platform jar (the v1 catalog).
     * Built-ins are re-synced from the classpath on every boot and cannot be
     * deleted through the API; user-installed skills are mutable.
     */
    @Column(name = "builtIn", nullable = false)
    private boolean builtIn = false;

    /** {@code 1} = enabled, {@code 0} = disabled (matches the project-wide int-flag convention). */
    @Column(name = "enabled", nullable = false)
    private int enabled = 1;
}
