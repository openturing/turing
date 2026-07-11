/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.persistence.model.kb;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * T668 / §XL (Block AQ) — the aggregate root of the Knowledge Base: a
 * tenant-scoped <em>library</em> of hierarchical {@link TurMicrothesaurus} trees
 * (controlled vocabularies), a controlled-vocabulary / semantic-navigation model. A knowledge
 * base is authored/imported once and SN sites <em>opt into</em> its microthesauri
 * per site (T670); at index time the selected trees expand each document with the
 * root→term hierarchical path of every term it mentions (T672).
 *
 * <p>This is the aggregate root, so it carries the Hibernate-managed
 * {@code @TenantId}; its {@link TurMicrothesaurus} children isolate through their
 * FK rather than a duplicated tenant column (the same scoping the SN-site children
 * use). Architecture: {@code docs/adr/0003-knowledge-base-microthesaurus.md}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "kb_knowledge_base")
public class TurKnowledgeBase implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    /** Provenance (seed / AI-generated / XML-imported / user-authored). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TurKnowledgeBaseSource source = TurKnowledgeBaseSource.USER;

    /** Multi-tenancy discriminator (Hibernate-managed); see the TurSNSite pilot. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column
    private LocalDateTime creationDate;

    @Column
    private LocalDateTime modificationDate;

    @OneToMany(mappedBy = "turKnowledgeBase", orphanRemoval = true,
            fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<TurMicrothesaurus> turMicrothesauri = new HashSet<>();

    public void setTurMicrothesauri(Set<TurMicrothesaurus> turMicrothesauri) {
        this.turMicrothesauri.clear();
        if (turMicrothesauri != null) {
            this.turMicrothesauri.addAll(turMicrothesauri);
        }
    }
}
