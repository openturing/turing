/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.skill;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T317 / §IX.4.d — persistence for the thin {@link TurSkill} catalog index.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurSkillRepository extends JpaRepository<TurSkill, String> {

    /** Index identity lookup — the storage folder {@code path} is unique. */
    Optional<TurSkill> findByPath(String path);

    @Modifying
    @Query("delete from TurSkill s where s.id = ?1")
    void delete(String id);
}
