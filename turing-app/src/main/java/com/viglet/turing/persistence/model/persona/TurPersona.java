/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A reusable voice profile that can be attached to a
 * {@link com.viglet.turing.persistence.model.agent.TurAIAgent}. The persona
 * fuses into the system prompt at chat time: base instruction, style
 * guidelines (tone / verbosity / language style), required + forbidden
 * vocabulary, optional brand context pulled from an MCP server, and
 * few-shot Q/A examples retrieved by similarity from a dedicated vector
 * store.
 *
 * <p>Forbidden terms are also enforced post-LLM by
 * {@code TurPersonaToneValidator}: matches are masked before the response
 * leaves the chat executor.
 *
 * <p>Both {@code mandatoryTerms} and {@code forbiddenTerms} are pipe
 * (<code>|</code>)-separated rather than comma-separated so individual
 * terms can themselves contain commas.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Getter
@Setter
@Entity
@Table(name = "ai_persona")
public class TurPersona implements Serializable {
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

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Lob
    @Column(name = "system_instruction")
    private String systemInstruction;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TurPersonaTone tone;

    @Column(nullable = false)
    private int verbosity = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "language_style", length = 16)
    private TurPersonaLanguageStyle languageStyle;

    @Lob
    @Column(name = "mandatory_terms")
    private String mandatoryTerms;

    @Lob
    @Column(name = "forbidden_terms")
    private String forbiddenTerms;

    @Column(nullable = false)
    private int enabled = 1;

    @ManyToOne
    @JoinColumn(name = "few_shot_store_id")
    private TurStoreInstance fewShotStore;

    @ManyToOne
    @JoinColumn(name = "brand_context_mcp_server_id")
    private TurMcpServer brandContextMcpServer;
}
