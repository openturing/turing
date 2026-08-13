/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.persona;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.persona.TurPersonaAudienceApplyLog;

/**
 * Repository for the append-only audience-apply provenance series
 * {@link TurPersonaAudienceApplyLog} (T831 / §LXI.7).
 *
 * <p>Deliberately has no delete-by-project method: a row records why a persona
 * changed and must outlive the project that suggested it, which is the whole
 * point of stamping it. Pruning, if it is ever needed, is a retention decision
 * and not a cascade.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurPersonaAudienceApplyLogRepository
        extends JpaRepository<TurPersonaAudienceApplyLog, String> {

    /** One persona's applied history, most recent first. */
    List<TurPersonaAudienceApplyLog> findByPersonaIdOrderByAppliedAtDesc(String personaId);

    /** Everything one project's suggestions ever wrote, most recent first. */
    List<TurPersonaAudienceApplyLog> findByProjectIdOrderByAppliedAtDesc(String projectId);
}
