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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.files.FileMetadata;
import com.openai.client.OpenAIClient;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FileObject;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient;
import com.viglet.turing.genai.nativeapi.TurNativeProviderClient.NativeCredentials;
import com.viglet.turing.genai.nativeapi.files.TurFilesApiBridge.TurVendorFileRef;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurVendorFile;
import com.viglet.turing.persistence.repository.llm.TurVendorFileRepository;

/**
 * T175 — unit coverage for {@link TurFilesApiBridge}: content-hash cache hit /
 * miss, the OpenAI and Anthropic upload branches, the non-native and error
 * fail-open paths.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurFilesApiBridgeTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 hello".getBytes(StandardCharsets.UTF_8);

    @Mock
    private TurNativeProviderClient nativeClient;
    @Mock
    private TurGenAiLlmProviderFactory providerFactory;
    @Mock
    private TurVendorFileRepository vendorFileRepository;

    @InjectMocks
    private TurFilesApiBridge bridge;

    private TurLLMInstance instance(String id, String pluginType) {
        return instance(id, pluginType, "sk-shared-key");
    }

    private TurLLMInstance instance(String id, String pluginType, String apiKey) {
        TurLLMInstance instance = mock(TurLLMInstance.class);
        // lenient: the non-native-vendor test returns before the id is read.
        org.mockito.Mockito.lenient().when(instance.getId()).thenReturn(id);
        TurGenAiLlmProvider provider = mock(TurGenAiLlmProvider.class);
        when(provider.getPluginType()).thenReturn(pluginType);
        when(providerFactory.getProvider(instance)).thenReturn(provider);
        // lenient: the non-native and empty-data paths return before the
        // account fingerprint (and thus credentials) is computed.
        org.mockito.Mockito.lenient().when(nativeClient.credentials(instance))
                .thenReturn(new NativeCredentials(apiKey, "https://api.vendor.test"));
        return instance;
    }

    @Test
    void uploadsToOpenAiOnCacheMissAndPersists() {
        TurLLMInstance instance = instance("inst-openai", "openai");
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), anyString(), anyString())).thenReturn(Optional.empty());

        OpenAIClient client = mock(OpenAIClient.class);
        com.openai.services.blocking.FileService files = mock(com.openai.services.blocking.FileService.class);
        FileObject fileObject = mock(FileObject.class);
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(client));
        when(client.files()).thenReturn(files);
        when(files.create(any(FileCreateParams.class))).thenReturn(fileObject);
        when(fileObject.id()).thenReturn("file_openai_1");

        Optional<TurVendorFileRef> ref = bridge.ensureUploaded(instance, PDF_BYTES, "report.pdf",
                "application/pdf");

        assertThat(ref).isPresent();
        assertThat(ref.get().vendorFileId()).isEqualTo("file_openai_1");
        assertThat(ref.get().pluginType()).isEqualTo("openai");
        assertThat(ref.get().sizeBytes()).isEqualTo(PDF_BYTES.length);

        ArgumentCaptor<TurVendorFile> saved = ArgumentCaptor.forClass(TurVendorFile.class);
        verify(vendorFileRepository).save(saved.capture());
        assertThat(saved.getValue().getPluginType()).isEqualTo("openai");
        assertThat(saved.getValue().getInstanceId()).isEqualTo("inst-openai");
        assertThat(saved.getValue().getVendorFileId()).isEqualTo("file_openai_1");
        assertThat(saved.getValue().getContentHash()).hasSize(64);
        assertThat(saved.getValue().getFileName()).isEqualTo("report.pdf");
        // T177 — the row carries the account fingerprint that other instances dedup against.
        assertThat(saved.getValue().getAccountKey()).hasSize(64);
    }

    @Test
    void uploadsToAnthropicOnCacheMiss() {
        TurLLMInstance instance = instance("inst-anthropic", "anthropic");
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("anthropic"), anyString(), anyString())).thenReturn(Optional.empty());

        AnthropicClient client = mock(AnthropicClient.class);
        com.anthropic.services.blocking.BetaService beta =
                mock(com.anthropic.services.blocking.BetaService.class);
        com.anthropic.services.blocking.beta.FileService files =
                mock(com.anthropic.services.blocking.beta.FileService.class);
        FileMetadata metadata = mock(FileMetadata.class);
        when(nativeClient.anthropic(instance)).thenReturn(Optional.of(client));
        when(client.beta()).thenReturn(beta);
        when(beta.files()).thenReturn(files);
        when(files.upload(any(com.anthropic.models.beta.files.FileUploadParams.class)))
                .thenReturn(metadata);
        when(metadata.id()).thenReturn("file_anthropic_1");

        Optional<TurVendorFileRef> ref = bridge.ensureUploaded(instance, PDF_BYTES, "report.pdf",
                "application/pdf");

        assertThat(ref).isPresent();
        assertThat(ref.get().vendorFileId()).isEqualTo("file_anthropic_1");
        assertThat(ref.get().pluginType()).isEqualTo("anthropic");
        verify(vendorFileRepository).save(any(TurVendorFile.class));
    }

    @Test
    void servesCacheHitWithoutUploading() {
        TurLLMInstance instance = instance("inst-openai", "openai");
        TurVendorFile cached = new TurVendorFile();
        cached.setPluginType("openai");
        cached.setInstanceId("inst-openai");
        cached.setVendorFileId("file_cached");
        cached.setFileName("report.pdf");
        cached.setContentType("application/pdf");
        cached.setSizeBytes(PDF_BYTES.length);
        cached.setCreatedAt(OffsetDateTime.now());
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), anyString(), anyString())).thenReturn(Optional.of(cached));

        Optional<TurVendorFileRef> ref = bridge.ensureUploaded(instance, PDF_BYTES, "report.pdf",
                "application/pdf");

        assertThat(ref).isPresent();
        assertThat(ref.get().vendorFileId()).isEqualTo("file_cached");
        verify(nativeClient, never()).openAi(any());
        verify(vendorFileRepository, never()).save(any());
    }

    @Test
    void treatsExpiredCacheRowAsMissAndReUploads() {
        TurLLMInstance instance = instance("inst-openai", "openai");
        TurVendorFile expired = new TurVendorFile();
        expired.setPluginType("openai");
        expired.setInstanceId("inst-openai");
        expired.setVendorFileId("file_stale");
        expired.setExpiresAt(OffsetDateTime.now().minusHours(1));
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), anyString(), anyString())).thenReturn(Optional.of(expired));

        OpenAIClient client = mock(OpenAIClient.class);
        com.openai.services.blocking.FileService files = mock(com.openai.services.blocking.FileService.class);
        FileObject fileObject = mock(FileObject.class);
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(client));
        when(client.files()).thenReturn(files);
        when(files.create(any(FileCreateParams.class))).thenReturn(fileObject);
        when(fileObject.id()).thenReturn("file_fresh");

        Optional<TurVendorFileRef> ref = bridge.ensureUploaded(instance, PDF_BYTES, "report.pdf",
                "application/pdf");

        assertThat(ref).isPresent();
        assertThat(ref.get().vendorFileId()).isEqualTo("file_fresh");
    }

    @Test
    void servesGeminiCacheHitWithoutUploading() {
        // T497 — Gemini is now a native Files-API vendor; a fresh cache row is
        // served without touching the SDK (the upload path is integration-only).
        TurLLMInstance instance = instance("inst-gemini", "gemini");
        TurVendorFile cached = new TurVendorFile();
        cached.setPluginType("gemini");
        cached.setInstanceId("inst-gemini");
        cached.setVendorFileId("https://generativelanguage.googleapis.com/v1beta/files/abc");
        cached.setFileName("report.pdf");
        cached.setContentType("application/pdf");
        cached.setSizeBytes(PDF_BYTES.length);
        cached.setCreatedAt(OffsetDateTime.now());
        cached.setExpiresAt(OffsetDateTime.now().plusHours(48));
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("gemini"), anyString(), anyString())).thenReturn(Optional.of(cached));

        Optional<TurVendorFileRef> ref = bridge.ensureUploaded(instance, PDF_BYTES, "report.pdf",
                "application/pdf");

        assertThat(ref).isPresent();
        assertThat(ref.get().pluginType()).isEqualTo("gemini");
        assertThat(ref.get().vendorFileId()).contains("files/abc");
        verify(nativeClient, never()).gemini(any());
        verify(vendorFileRepository, never()).save(any());
    }

    @Test
    void returnsEmptyForNonNativeVendor() {
        TurLLMInstance instance = instance("inst-ollama", "ollama");

        Optional<TurVendorFileRef> ref = bridge.ensureUploaded(instance, PDF_BYTES, "report.pdf",
                "application/pdf");

        assertThat(ref).isEmpty();
        verify(vendorFileRepository, never())
                .findByPluginTypeAndAccountKeyAndContentHash(anyString(), anyString(), anyString());
        verify(vendorFileRepository, never()).save(any());
    }

    @Test
    void failsOpenWhenUploadThrows() {
        TurLLMInstance instance = instance("inst-openai", "openai");
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), anyString(), anyString())).thenReturn(Optional.empty());

        OpenAIClient client = mock(OpenAIClient.class);
        com.openai.services.blocking.FileService files = mock(com.openai.services.blocking.FileService.class);
        when(nativeClient.openAi(instance)).thenReturn(Optional.of(client));
        when(client.files()).thenReturn(files);
        when(files.create(any(FileCreateParams.class)))
                .thenThrow(new RuntimeException("429 rate limited"));

        Optional<TurVendorFileRef> ref = bridge.ensureUploaded(instance, PDF_BYTES, "report.pdf",
                "application/pdf");

        assertThat(ref).isEmpty();
        verify(vendorFileRepository, never()).save(any());
    }

    @Test
    void returnsEmptyForEmptyData() {
        assertThat(bridge.ensureUploaded(mock(TurLLMInstance.class), new byte[0], "x", "x")).isEmpty();
        assertThat(bridge.ensureUploaded(mock(TurLLMInstance.class), null, "x", "x")).isEmpty();
    }

    /**
     * T177 — two different instances pointing at the <em>same</em> vendor account
     * resolve to the same {@code accountKey}, so the second one's cache lookup
     * targets exactly the row the first one uploaded: one upload, many references.
     */
    @Test
    void sharesUploadAcrossInstancesWithSameVendorAccount() {
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), anyString(), anyString())).thenReturn(Optional.empty());

        TurLLMInstance instanceA = instance("inst-a", "openai", "sk-shared");
        OpenAIClient client = mock(OpenAIClient.class);
        com.openai.services.blocking.FileService files = mock(com.openai.services.blocking.FileService.class);
        FileObject fileObject = mock(FileObject.class);
        when(nativeClient.openAi(instanceA)).thenReturn(Optional.of(client));
        when(client.files()).thenReturn(files);
        when(files.create(any(FileCreateParams.class))).thenReturn(fileObject);
        when(fileObject.id()).thenReturn("file_shared");

        bridge.ensureUploaded(instanceA, PDF_BYTES, "report.pdf", "application/pdf");

        ArgumentCaptor<TurVendorFile> saved = ArgumentCaptor.forClass(TurVendorFile.class);
        verify(vendorFileRepository).save(saved.capture());
        String accountKey = saved.getValue().getAccountKey();
        assertThat(accountKey).hasSize(64);

        // A second instance sharing the credential — its lookup uses the same account key.
        TurLLMInstance instanceB = instance("inst-b", "openai", "sk-shared");
        bridge.ensureUploaded(instanceB, PDF_BYTES, "report.pdf", "application/pdf");

        ArgumentCaptor<String> lookupKeys = ArgumentCaptor.forClass(String.class);
        verify(vendorFileRepository, times(2)).findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), lookupKeys.capture(), anyString());
        assertThat(lookupKeys.getAllValues()).containsExactly(accountKey, accountKey);
    }

    /** T177 — instances with distinct credentials stay isolated (distinct account keys). */
    @Test
    void distinctCredentialsProduceDistinctAccountKeys() {
        when(vendorFileRepository.findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), anyString(), anyString())).thenReturn(Optional.empty());

        bridge.ensureUploaded(instance("inst-a", "openai", "sk-aaa"), PDF_BYTES, "r.pdf",
                "application/pdf");
        bridge.ensureUploaded(instance("inst-b", "openai", "sk-bbb"), PDF_BYTES, "r.pdf",
                "application/pdf");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(vendorFileRepository, times(2)).findByPluginTypeAndAccountKeyAndContentHash(
                eq("openai"), keys.capture(), anyString());
        assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(1));
    }
}
