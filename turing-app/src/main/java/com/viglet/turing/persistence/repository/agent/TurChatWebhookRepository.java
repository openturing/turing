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

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.agent.TurChatWebhook;

/**
 * Repository for {@link TurChatWebhook} entities (T62).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurChatWebhookRepository extends JpaRepository<TurChatWebhook, String> {

    Optional<TurChatWebhook> findByName(String name);

    @Modifying
    @Query("delete from TurChatWebhook w where w.id = ?1")
    void delete(String id);
}
