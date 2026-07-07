/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.files;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.AnthropicBeta;
import com.anthropic.models.beta.files.FileMetadata;
import com.anthropic.models.beta.files.FileUploadParams;
import com.openai.client.OpenAIClient;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.openai.models.files.FilePurpose;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurVendorFile;
import com.viglet.turing.persistence.repository.llm.TurVendorFileRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T175 / §X.12.a — mirrors a binary into the OpenAI / Anthropic <b>Files API</b>
 * and caches the returned {@code file_id}, so a document can later be grounded
 * natively (page-accurate PDF understanding, T176) instead of as Tika-extracted
 * text only.
 *
 * <p>The upload is content-addressed: {@link #ensureUploaded} hashes the bytes
 * (SHA-256) and consults {@link TurVendorFileRepository} before calling the
 * vendor, so the same binary uploads exactly once per vendor account no matter
 * how many agents or turns reference it. The {@code file_id} is account-scoped
 * (it lives under the API key), so <b>T177 / §X.12.c</b> keys the cache on
 * {@code (pluginType, accountKey, contentHash)} — where {@code accountKey} is a
 * SHA-256 fingerprint of {@code (pluginType, baseUrl, apiKey)}. N agents on
 * different instances that share one vendor account (a tenant's own key, or a
 * platform GLOBAL instance) therefore share one upload; instances with distinct
 * credentials stay isolated, and dedup only fires on byte-identical content.
 *
 * <p><b>Fail-open by design.</b> Every failure mode — a non-native vendor, a
 * missing key, an HTTP error from the vendor — returns {@link Optional#empty()}
 * rather than throwing, so the caller (T176) silently falls back to the
 * established Tika-text grounding. Mirroring is an enhancement, never a
 * prerequisite, of a chat turn.
 *
 * <p>Wired to OpenAI ({@code files.create}, {@code purpose=user_data}) and
 * Anthropic ({@code beta.files.upload}, beta {@code files-api-2025-04-14}).
 * Gemini folds in here under T497.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurFilesApiBridge {

    private static final String OPENAI = "openai";
    private static final String ANTHROPIC = "anthropic";
    private static final String GEMINI = "gemini";
    /** T497 — Gemini Files API retention; the cache row expires with the upload. */
    private static final java.time.Duration GEMINI_RETENTION = java.time.Duration.ofHours(48);
    private static final String DEFAULT_FILE_NAME = "document";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int MAX_FILE_NAME = 512;
    private static final int MAX_CONTENT_TYPE = 128;

    private final TurNativeProviderClient nativeClient;
    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurVendorFileRepository vendorFileRepository;

    public TurFilesApiBridge(TurNativeProviderClient nativeClient,
            TurGenAiLlmProviderFactory providerFactory,
            TurVendorFileRepository vendorFileRepository) {
        this.nativeClient = nativeClient;
        this.providerFactory = providerFactory;
        this.vendorFileRepository = vendorFileRepository;
    }

    /**
     * A handle on a binary that lives in a vendor's Files API.
     *
     * @param vendorFileId the opaque {@code file_id}
     * @param pluginType   the vendor it was uploaded to ({@code openai} / {@code anthropic})
     * @param fileName     the file name sent at upload
     * @param contentType  the MIME type sent at upload
     * @param sizeBytes    the uploaded size
     */
    public record TurVendorFileRef(String vendorFileId, String pluginType, String fileName,
            String contentType, long sizeBytes) {
    }

    /**
     * Ensure {@code data} is present in the Files API of {@code instance}'s vendor
     * and return its {@code file_id}, uploading it the first time and serving the
     * cache thereafter. Returns {@link Optional#empty()} when the instance is not
     * an OpenAI / Anthropic native instance, when no usable client can be built,
     * or when the upload fails — the caller falls back to text grounding.
     *
     * @param instance    the LLM instance whose vendor account owns the upload
     * @param data        the file bytes (must be non-empty)
     * @param fileName    a display name (defaulted when blank)
     * @param contentType the MIME type (defaulted when blank)
     */
    public Optional<TurVendorFileRef> ensureUploaded(TurLLMInstance instance, byte[] data,
            String fileName, String contentType) {
        if (instance == null || data == null || data.length == 0) {
            return Optional.empty();
        }
        String pluginType = pluginType(instance);
        if (!OPENAI.equals(pluginType) && !ANTHROPIC.equals(pluginType)
                && !GEMINI.equals(pluginType)) {
            return Optional.empty();
        }
        String name = clamp(StringUtils.hasText(fileName) ? fileName : DEFAULT_FILE_NAME, MAX_FILE_NAME);
        String type = clamp(StringUtils.hasText(contentType) ? contentType : DEFAULT_CONTENT_TYPE,
                MAX_CONTENT_TYPE);
        String hash = sha256(data);
        if (hash == null) {
            return Optional.empty();
        }

        // T177 — dedup key is the vendor account (credential), not the instance:
        // instances sharing one key/endpoint share the upload. No usable
        // credential ⇒ the native upload could not run anyway, so fail open.
        String accountKey = accountKey(instance, pluginType);
        if (accountKey == null) {
            return Optional.empty();
        }

        Optional<TurVendorFile> cached = vendorFileRepository
                .findByPluginTypeAndAccountKeyAndContentHash(pluginType, accountKey, hash)
                .filter(this::notExpired);
        if (cached.isPresent()) {
            return cached.map(TurFilesApiBridge::toRef);
        }

        try {
            String fileId = switch (pluginType) {
                case OPENAI -> uploadToOpenAi(instance, data, name, type);
                case ANTHROPIC -> uploadToAnthropic(instance, data, name, type);
                case GEMINI -> uploadToGemini(instance, data, name, type);
                default -> null;
            };
            if (!StringUtils.hasText(fileId)) {
                return Optional.empty();
            }
            TurVendorFileRef ref = new TurVendorFileRef(fileId, pluginType, name, type, data.length);
            persist(ref, instance.getId(), accountKey, hash, expiryFor(pluginType));
            return Optional.of(ref);
        } catch (RuntimeException e) {
            log.warn("[Files-API] upload to '{}' for instance '{}' failed — falling back to text: {}",
                    pluginType, instance.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private String uploadToOpenAi(TurLLMInstance instance, byte[] data, String name, String type) {
        OpenAIClient client = nativeClient.openAi(instance).orElse(null);
        if (client == null) {
            return null;
        }
        FileObject file = client.files().create(FileCreateParams.builder()
                .purpose(FilePurpose.USER_DATA)
                .file(com.openai.core.MultipartField.<InputStream>builder()
                        .value(new ByteArrayInputStream(data))
                        .filename(name)
                        .contentType(type)
                        .build())
                .build());
        return file.id();
    }

    private String uploadToAnthropic(TurLLMInstance instance, byte[] data, String name, String type) {
        AnthropicClient client = nativeClient.anthropic(instance).orElse(null);
        if (client == null) {
            return null;
        }
        FileMetadata file = client.beta().files().upload(FileUploadParams.builder()
                .file(com.anthropic.core.MultipartField.<InputStream>builder()
                        .value(new ByteArrayInputStream(data))
                        .filename(name)
                        .contentType(type)
                        .build())
                .addBeta(AnthropicBeta.FILES_API_2025_04_14)
                .build());
        return file.id();
    }

    /**
     * T497 — upload to the Gemini Files API and return the file's URI (what a
     * {@code fileData} part references for native PDF/video grounding), falling
     * back to its resource name. Gemini files are retained ~48 h (see
     * {@link #expiryFor}).
     */
    private String uploadToGemini(TurLLMInstance instance, byte[] data, String name, String type) {
        com.google.genai.Client client = nativeClient.gemini(instance).orElse(null);
        if (client == null) {
            return null;
        }
        com.google.genai.types.File file = client.files.upload(data,
                com.google.genai.types.UploadFileConfig.builder()
                        .mimeType(type)
                        .displayName(name)
                        .build());
        return file.uri().or(file::name).orElse(null);
    }

    /** Files API retention per vendor: Gemini expires after 48 h; OpenAI/Anthropic don't. */
    private static OffsetDateTime expiryFor(String pluginType) {
        return GEMINI.equals(pluginType) ? OffsetDateTime.now().plus(GEMINI_RETENTION) : null;
    }

    /** Persist the cache row, tolerating a concurrent insert that already won the unique key. */
    private void persist(TurVendorFileRef ref, String instanceId, String accountKey, String hash,
            OffsetDateTime expiresAt) {
        TurVendorFile row = new TurVendorFile();
        row.setPluginType(ref.pluginType());
        row.setInstanceId(instanceId);
        row.setAccountKey(accountKey);
        row.setContentHash(hash);
        row.setVendorFileId(ref.vendorFileId());
        row.setFileName(ref.fileName());
        row.setContentType(ref.contentType());
        row.setSizeBytes(ref.sizeBytes());
        row.setCreatedAt(OffsetDateTime.now());
        row.setExpiresAt(expiresAt);
        try {
            vendorFileRepository.save(row);
        } catch (DataIntegrityViolationException e) {
            log.debug("[Files-API] cache row for instance '{}' already present (race) — keeping it",
                    instanceId);
        }
    }

    /**
     * T177 — a stable fingerprint of the vendor account the upload lives under,
     * so instances sharing one credential share the cache. Built from the
     * decrypted credentials ({@code pluginType + baseUrl + apiKey}); returns
     * {@code null} when no API key is available (the native upload could not run
     * anyway). The plaintext key never leaves this method — only its digest.
     */
    private String accountKey(TurLLMInstance instance, String pluginType) {
        TurNativeProviderClient.NativeCredentials creds = nativeClient.credentials(instance);
        if (creds == null || !StringUtils.hasText(creds.apiKey())) {
            return null;
        }
        String baseUrl = StringUtils.hasText(creds.baseUrl()) ? creds.baseUrl().strip() : "";
        return sha256Utf8(pluginType + "\n" + baseUrl + "\n" + creds.apiKey());
    }

    private boolean notExpired(TurVendorFile row) {
        return row.getExpiresAt() == null || row.getExpiresAt().isAfter(OffsetDateTime.now());
    }

    private String pluginType(TurLLMInstance instance) {
        try {
            return providerFactory.getProvider(instance).getPluginType().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static TurVendorFileRef toRef(TurVendorFile row) {
        return new TurVendorFileRef(row.getVendorFileId(), row.getPluginType(), row.getFileName(),
                row.getContentType(), row.getSizeBytes());
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS — unreachable, but stay fail-open.
            return null;
        }
    }

    private static String clamp(String value, int max) {
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** The UTF-8 SHA-256 of a string — backs the T177 account fingerprint and test helpers. */
    static String sha256Utf8(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}
