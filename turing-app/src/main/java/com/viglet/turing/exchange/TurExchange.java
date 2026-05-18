/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.exchange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.turing.exchange.agent.TurAIAgentExchange;
import com.viglet.turing.exchange.agent.TurCustomToolExchange;
import com.viglet.turing.exchange.sn.TurSNSiteExchange;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import lombok.Getter;
import lombok.Setter;

/**
 * Root envelope serialized to {@code export.json} for both SN site and AI
 * agent bundles. Every top-level list is independently optional — a bundle
 * carrying only AI agents leaves {@link #snSites}/{@link #se} null, and vice
 * versa. New entity types added in 2026.2.8 for the agent export
 * ({@link #agents}, {@link #personas}, {@link #mcpServers},
 * {@link #customTools}) live alongside the older site-side keys so a future
 * single ZIP can transport an agent and its host SN site together.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurExchange {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurSNSiteExchange> snSites;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurLLMInstance> llm;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurStoreInstance> store;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurSEInstance> se;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurEmbeddingModel> embeddingModels;

	// ─── Agent-side blocks (added 2026.2.8) ───────────────────────────

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurAIAgentExchange> agents;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurPersona> personas;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurMcpServer> mcpServers;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurCustomToolExchange> customTools;
}
