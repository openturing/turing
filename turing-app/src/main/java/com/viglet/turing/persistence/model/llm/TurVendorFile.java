/*
 * Copyright (C) 2016-2026 the original author or authors.
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
import java.time.OffsetDateTime;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * T175 / §X.12.a — the vendor Files-API upload cache: one row per
 * (vendor × LLM instance × file content) that has been mirrored into an
 * OpenAI or Anthropic Files API, keyed by the SHA-256 of the bytes.
 *
 * <p>The roadmap framed the cache as living "in {@code TurStorageObjectStat}
 * metadata", but that record carries no arbitrary-metadata map and the
 * filesystem backend has no sidecar; a dedicated table is the portable home and
 * — unlike per-object metadata — it also keys on the <em>content hash</em>, so
 * the same binary indexed under different object names uploads once. That same
 * content-hash key is what T177 (vendor-side chunk reuse across agents in one
 * tenant) builds on.
 *
 * <p>The vendor {@code fileId} is account-scoped (it lives under the API key),
 * so T175 keyed the cache on {@code (pluginType, instanceId, contentHash)}: two
 * agents pointing at the same instance reuse the upload; two instances each got
 * their own — even when they pointed at the <em>same</em> vendor account, so the
 * same PDF re-uploaded once per instance.
 *
 * <p><b>T177 / §X.12.c — vendor-side reuse across agents in one tenant.</b> The
 * real validity boundary of a {@code file_id} is the vendor <em>account</em>
 * (the credential it was uploaded under), not the Turing instance. So the cache
 * now also carries {@link #accountKey} — a stable fingerprint of
 * {@code (pluginType, baseUrl, apiKey)} — and the lookup key broadens to
 * {@code (pluginType, accountKey, contentHash)}. N agents on instances that
 * share one vendor account (a tenant's own key, or a platform GLOBAL instance)
 * now share a single {@code file_id}: one upload, many references. Instances
 * with distinct keys produce distinct {@code accountKey}s and stay isolated, and
 * dedup only ever fires on byte-identical content, so nothing crosses an account
 * that did not already hold those exact bytes. {@link #instanceId} is retained as
 * the provenance of the instance that triggered the upload.
 *
 * <p>{@code expiresAt} is null for OpenAI / Anthropic (their files persist) and
 * is reserved for providers whose uploads expire (Gemini's 48 h Files API,
 * T497) — a stale row is treated as a miss.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Entity
@Table(name = "llm_vendor_file",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_llm_vendor_file",
                        columnNames = {"pluginType", "instanceId", "contentHash"}),
                @UniqueConstraint(name = "uq_llm_vendor_file_acct",
                        columnNames = {"pluginType", "accountKey", "contentHash"})
        })
public class TurVendorFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Lowercased provider plugin type the file was uploaded to ({@code openai} / {@code anthropic}). */
    @Column(name = "pluginType", length = 32, nullable = false)
    private String pluginType;

    /** FK-by-value to {@code llm_instance.id} — provenance of the instance that triggered the upload. */
    @Column(name = "instanceId", length = 40, nullable = false)
    private String instanceId;

    /**
     * T177 — hex SHA-256 fingerprint of the vendor account the file lives under
     * ({@code pluginType + baseUrl + apiKey}). The cross-instance dedup key:
     * instances sharing one credential share this value and thus the upload.
     * Nullable only for legacy T175 rows written before this column existed.
     */
    @Column(name = "accountKey", length = 64)
    private String accountKey;

    /** Hex SHA-256 of the uploaded bytes — the dedup key. */
    @Column(name = "contentHash", length = 64, nullable = false)
    private String contentHash;

    /** The opaque {@code file_id} returned by the vendor's Files API. */
    @Column(name = "vendorFileId", length = 255, nullable = false)
    private String vendorFileId;

    @Column(name = "fileName", length = 512)
    private String fileName;

    @Column(name = "contentType", length = 128)
    private String contentType;

    @Column(name = "sizeBytes")
    private long sizeBytes;

    @Column(name = "createdAt", nullable = false)
    private OffsetDateTime createdAt;

    /** When the vendor upload expires; {@code null} means it persists (OpenAI / Anthropic). */
    @Column(name = "expiresAt")
    private OffsetDateTime expiresAt;
}
