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
 * T317 / §IX.4.d — the <strong>thin catalog index</strong> for an
 * Anthropic-compatible skill <em>folder</em>.
 *
 * <p>The pivot locked on 2026-06-10 reframed a skill as a folder living in
 * object storage ({@code SKILL.md} at the root plus {@code references/},
 * {@code scripts/}, {@code assets/}), <em>not</em> a structured database row.
 * Turing treats the folder as the source of truth and runs it to Anthropic's
 * standard; this entity is merely a queryable <strong>index</strong> over the
 * folders that exist in storage, so the UI can list/filter/enable skills
 * without scanning storage on every request.
 *
 * <p>Every field except {@link #enabled} is derived from the {@code SKILL.md}
 * YAML frontmatter and is re-synced from storage on
 * {@link com.viglet.turing.genai.skill.TurSkillCatalogService#reindex() reindex}.
 * The {@link #path} (the skill's storage folder) is the stable identity — one
 * index row per folder.
 *
 * <p>Gated on storage: with {@code turing.storage.type=none} there is nowhere
 * for skill folders to live, so the index stays empty.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Getter
@Setter
@Entity
@Table(name = "skill", uniqueConstraints = @UniqueConstraint(
        name = "uq_skill_path", columnNames = { "path" }))
public class TurSkill implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    /** Frontmatter {@code name} — the skill's stable identifier (e.g. {@code brand-content-studio}). */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Frontmatter {@code description} — the progressive-disclosure hint the LLM
     * sees to decide whether a skill is relevant. Can be long (paragraphs), so
     * stored as {@code longtext}.
     */
    @Lob
    @Column(name = "description")
    private String description;

    /** Semver string from {@code metadata.version} (or top-level {@code version}). */
    @Column(name = "version", length = 32)
    private String version;

    /** First entry of {@code metadata.authors} (or top-level {@code author}). */
    @Column(name = "author", length = 150)
    private String author;

    /**
     * Storage folder path that contains {@code SKILL.md} (e.g.
     * {@code skills/brand-content-studio}). Unique — the skill's identity.
     */
    @Column(name = "path", nullable = false, length = 500)
    private String path;

    /** {@code 1} = enabled, {@code 0} = disabled (matches the project-wide int-flag convention). */
    @Column(name = "enabled", nullable = false)
    private int enabled = 1;
}
