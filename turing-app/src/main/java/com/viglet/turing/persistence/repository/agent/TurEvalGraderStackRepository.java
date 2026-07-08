/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurEvalGraderStack;

/**
 * T600 / §XXXIII.15 — repository for named, reusable grader stacks.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurEvalGraderStackRepository extends JpaRepository<TurEvalGraderStack, String> {

    List<TurEvalGraderStack> findByOrderByNameAsc();

    /** T602 — resolve a grader stack by human name (for the public {@code turing eval} API). */
    Optional<TurEvalGraderStack> findFirstByName(String name);
}
