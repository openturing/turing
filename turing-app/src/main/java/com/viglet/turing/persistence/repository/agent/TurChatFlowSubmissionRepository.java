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

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.agent.TurChatFlowSubmission;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
public interface TurChatFlowSubmissionRepository extends JpaRepository<TurChatFlowSubmission, String> {

    /** Submissions of a flow ordered newest first — feeds the History page. */
    List<TurChatFlowSubmission> findByFlow_IdOrderByCompletedAtDesc(String flowId);
}
