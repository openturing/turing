/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.persistence.model.sn.genai;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;
import com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * GenAI binding for an SN site. The site delegates LLM, embedding model,
 * vector store and the RAG enabled flag to a referenced {@link TurAIAgent} —
 * only the site-specific prompts (system prompt and site description prompt)
 * remain here.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
@Entity
@Table(name = "sn_site_genai")
@EntityListeners(TurSNSiteSnapshotEvictionListener.class)
public class TurSNSiteGenAi implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * AI agent that powers this site's GenAI features. When null, GenAI is
     * effectively disabled for the site. The agent's {@code ragEnabled},
     * embedding model, store, LLM list and {@code systemPrompt} are the
     * source of truth.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "agent_id")
    private TurAIAgent turAIAgent;

    /**
     * Site description used as input to the summary/insights LLM call (a
     * different feature from the chat). Distinct from the agent's system
     * prompt, which drives the chat itself.
     */
    @Lob
    @Column
    private String sitePrompt;
}
