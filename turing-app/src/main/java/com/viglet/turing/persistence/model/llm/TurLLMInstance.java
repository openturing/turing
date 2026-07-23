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
package com.viglet.turing.persistence.model.llm;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.core.tenancy.VigletTenantOwnedInfra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the TurLLMInstance database table.
 * 
 */
@Getter
@Setter
@Entity
@Table(name = "llm_instance")
public class TurLLMInstance implements Serializable, VigletTenantOwnedInfra {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@VigletAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	/**
	 * T275 / §XIV.5.1 — owning tenant for BYO infra. A non-null value is a
	 * tenant's own instance (their key/endpoint); {@code null} is a
	 * platform-provided GLOBAL instance every tenant may use. Deliberately
	 * NOT {@code @TenantId} — that would filter the shared NULLs out; the
	 * repositories use an explicit {@code tenantId = :current OR IS NULL}.
	 */
	@jakarta.persistence.Column(name = "tenantId", length = 40)
	private String tenantId;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = true, length = 500)
	private String description;

	@Column(length = 150)
	private String icon;

	@Column(nullable = false)
	private int enabled;

	// T796 — url is optional: every provider's resolveBaseUrl(...) falls back to
	// a vendor default when the configured url is blank (e.g. OpenAI/Anthropic
	// hosted endpoints), so instances created without a url (Model Advisor
	// one-click create) are valid.
	@Column
	private String url;

	@ManyToOne
	@JoinColumn(name = "llm_vendor_id", nullable = false)
	private TurLLMVendor turLLMVendor;

	@Column
	private String modelName;

	/**
	 * Comma-separated list of models this instance may serve. {@code modelName}
	 * stays the single <em>default</em> model consumed everywhere the platform
	 * does not (yet) support multiple models per instance; this column simply
	 * records the wider set the operator selected so it can be surfaced (e.g. the
	 * gateway model picker) without changing the default-model contract. Null or
	 * blank on legacy rows means "only {@code modelName}".
	 *
	 * @since 2026.3.4
	 */
	@Column
	private String modelNames;

	/**
	 * Unified model entity, phase 2 (T753 / ADR 0004). {@code modelName} above is
	 * the CHAT default; these are the per-kind defaults + embedding-serving fields
	 * folded from {@code TurEmbeddingModel}. All nullable — populated only for
	 * embedding/rerank-capable instances; a chat-only instance leaves them null.
	 * Consumers are repointed onto these in T756; nothing reads them yet.
	 *
	 * @since 2026.3.4
	 */
	@Column
	private String embeddingModelName;

	@Column
	private String rerankModelName;

	@Column(length = 500)
	private String embeddingModelPath;

	@Column(length = 500)
	private String embeddingTokenizerPath;

	@Column
	private Integer embeddingBatchSize;

	/** Detected embedding output dimensions — preserved on migration for the T627 guard. */
	@Column
	private Integer embeddingDimensions;

	@Column
	private Double temperature;

	@Column
	private Integer topK;

	@Column
	private Double topP;

	@Column
	private Double repeatPenalty;

	@Column
	private Integer seed;

	@Column
	private Integer numPredict;

	@Column
	private String stop;

	@Column
	private String responseFormat;

	@Column
	private String supportedCapabilities;

	@Column
	private String timeout;

	@Column
	private Integer maxRetries;

	@Column(name = "context_window")
	private Integer contextWindow;

	@Lob
	@Column
	private String providerOptionsJson;

	@Column(name = "tools_enabled", nullable = false)
	private boolean toolsEnabled = true;

	/**
	 * T431 — when {@code true}, this provider is allowed to receive raw
	 * document binaries (PDF / DOCX …) via its native file-input capability,
	 * so document-to-slot extraction (T103) sends the file straight to the
	 * model instead of flattening it to text with Tika. The user enables it
	 * only for providers that actually support document input (Anthropic /
	 * Gemini / OpenAI); default {@code false} keeps the Tika text path.
	 */
	@Column(name = "file_upload_enabled", nullable = false)
	private boolean fileUploadEnabled = false;

	@Column
	@JsonIgnore
	private String apiKeyEncrypted;

	@Transient
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String apiKey;
}