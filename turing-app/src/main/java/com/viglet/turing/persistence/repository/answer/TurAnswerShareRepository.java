/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.answer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.turing.persistence.model.answer.TurAnswerShare;

/**
 * T844 / §LXII.13 — persistence for answer permalinks ({@link TurAnswerShare}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Repository
public interface TurAnswerShareRepository extends JpaRepository<TurAnswerShare, String> {
}
