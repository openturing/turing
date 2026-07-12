/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlotType;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.service.chatslots.TurChatMultiModalSlotService.MultiModalSlotResult;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Pure-Mockito unit tests for {@link TurChatMultiModalSlotService} (T64).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatMultiModalSlotServiceTest {

    private static final String CONV = "conv-1";
    private static final String AGENT_ID = "agent-1";

    @Mock private TurStorageService storageService;
    @Mock private TurGenAiLlmProviderFactory llmProviderFactory;
    @Mock private TurSecretCryptoService cryptoService;
    @Mock private TurChatFlowEngineService engineService;
    @Mock private TurAIAgentSlotRepository slotRepository;
    @Mock private com.viglet.turing.genai.nativeapi.gemini.TurGeminiVideoUnderstandingService videoUnderstanding;
    @Mock private com.viglet.turing.genai.transcription.TurTranscriptionService transcriptionService;

    private TurChatMultiModalSlotService service;

    @BeforeEach
    void setUp() {
        service = new TurChatMultiModalSlotService(storageService, llmProviderFactory,
                cryptoService, engineService, slotRepository, videoUnderstanding,
                transcriptionService);
    }

    private TurAIAgent agent() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId(AGENT_ID);
        return agent;
    }

    private TurAIAgentSlot slot(String name, TurAIAgentSlotType type) {
        TurAIAgentSlot s = new TurAIAgentSlot();
        s.setName(name);
        s.setType(type);
        return s;
    }

    private MultipartFile file(String name, String contentType, byte[] bytes) {
        try {
            MultipartFile f = mock(MultipartFile.class);
            lenient().when(f.isEmpty()).thenReturn(bytes.length == 0);
            lenient().when(f.getBytes()).thenReturn(bytes);
            lenient().when(f.getContentType()).thenReturn(contentType);
            lenient().when(f.getOriginalFilename()).thenReturn(name);
            lenient().when(f.getSize()).thenReturn((long) bytes.length);
            return f;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void uploadStoresBinaryAndWritesUrlSlot() {
        when(storageService.isEnabled()).thenReturn(true);
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("cv_file", TurAIAgentSlotType.FILE)));
        when(engineService.writeSlot(eq(CONV), eq("cv_file"), anyString(),
                eq(TurChatSlotAuditSource.UPLOAD), anyString())).thenReturn(1);

        MultiModalSlotResult result = service.upload(
                file("cv.pdf", "application/pdf", new byte[] { 1, 2, 3 }),
                agent(), CONV, "cv_file", false, List.of());

        assertThat(result.error()).isNull();
        assertThat(result.slotType()).isEqualTo(TurAIAgentSlotType.FILE);
        assertThat(result.objectName()).isEqualTo("chat-slots/conv-1/cv_file/cv.pdf");
        assertThat(result.url()).startsWith("/api/asset/preview?objectName=");
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.visionSlotsWritten()).isZero();

        verify(storageService).uploadStream(eq("chat-slots/conv-1/cv_file/cv.pdf"),
                any(InputStream.class), eq(3L), eq("application/pdf"));
        verify(engineService).writeSlot(eq(CONV), eq("cv_file"), anyString(),
                eq(TurChatSlotAuditSource.UPLOAD), anyString());
    }

    @Test
    void uploadErrorsWhenStorageDisabled() {
        when(storageService.isEnabled()).thenReturn(false);

        MultiModalSlotResult result = service.upload(
                file("cv.pdf", "application/pdf", new byte[] { 1 }),
                agent(), CONV, "cv_file", false, List.of());

        assertThat(result.error()).contains("storage is disabled");
        verify(storageService, never()).uploadStream(anyString(), any(), anyLong(), anyString());
        verify(engineService, never()).writeSlot(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void uploadErrorsOnUnknownSlot() {
        when(storageService.isEnabled()).thenReturn(true);
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID)).thenReturn(List.of());

        MultiModalSlotResult result = service.upload(
                file("cv.pdf", "application/pdf", new byte[] { 1 }),
                agent(), CONV, "missing", false, List.of());

        assertThat(result.error()).contains("Unknown slot");
        verify(storageService, never()).uploadStream(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadErrorsWhenSlotNotMultiModal() {
        when(storageService.isEnabled()).thenReturn(true);
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name", TurAIAgentSlotType.STRING)));

        MultiModalSlotResult result = service.upload(
                file("cv.pdf", "application/pdf", new byte[] { 1 }),
                agent(), CONV, "name", false, List.of());

        assertThat(result.error()).contains("not a multi-modal");
        verify(storageService, never()).uploadStream(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void uploadErrorsOnEmptyFile() {
        MultiModalSlotResult result = service.upload(
                file("empty.png", "image/png", new byte[0]),
                agent(), CONV, "photo", false, List.of());

        assertThat(result.error()).contains("File is required");
    }

    @Test
    void uploadErrorsOnNullAgentAndBlankInputs() {
        assertThat(service.upload(file("a.png", "image/png", new byte[] { 1 }),
                null, CONV, "photo", false, List.of()).error()).isNotNull();
        assertThat(service.upload(file("a.png", "image/png", new byte[] { 1 }),
                agent(), "  ", "photo", false, List.of()).error()).contains("conversationId");
        assertThat(service.upload(file("a.png", "image/png", new byte[] { 1 }),
                agent(), CONV, "  ", false, List.of()).error()).contains("slotName");
    }

    @Test
    void visionPassFillsScalarSlotsForImageSlot() {
        when(storageService.isEnabled()).thenReturn(true);
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID)).thenReturn(List.of(
                slot("photo", TurAIAgentSlotType.IMAGE),
                slot("name", TurAIAgentSlotType.STRING),
                slot("cargo", TurAIAgentSlotType.STRING)));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);

        TurAIAgent agent = agent();
        agent.setLlmInstances(java.util.Set.of(enabledLlm()));
        when(cryptoService.decrypt(any())).thenReturn("key");
        TurGenAiLlmProvider provider = mock(TurGenAiLlmProvider.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(llmProviderFactory.getProvider(any())).thenReturn(provider);
        when(provider.createChatModel(any(), eq("key"))).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "{\"name\":\"Alexandre\",\"cargo\":\"Gerente\"}"));

        MultiModalSlotResult result = service.upload(
                file("cv.png", "image/png", new byte[] { 9, 9, 9 }),
                agent, CONV, "photo", true, List.of());

        assertThat(result.error()).isNull();
        assertThat(result.visionExtracted()).containsEntry("name", "Alexandre")
                .containsEntry("cargo", "Gerente");
        assertThat(result.visionSlotsWritten()).isEqualTo(2);
        // image slot URL + two scalar slots
        verify(engineService).writeSlot(eq(CONV), eq("photo"), anyString(),
                eq(TurChatSlotAuditSource.UPLOAD), anyString());
        verify(engineService).writeSlot(eq(CONV), eq("name"), eq("Alexandre"),
                eq(TurChatSlotAuditSource.EXTRACT), anyString());
        verify(engineService).writeSlot(eq(CONV), eq("cargo"), eq("Gerente"),
                eq(TurChatSlotAuditSource.EXTRACT), anyString());
    }

    @Test
    void visionSkippedWhenNotRequested() {
        when(storageService.isEnabled()).thenReturn(true);
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("photo", TurAIAgentSlotType.IMAGE)));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);

        MultiModalSlotResult result = service.upload(
                file("cv.png", "image/png", new byte[] { 1 }),
                agent(), CONV, "photo", false, List.of());

        assertThat(result.error()).isNull();
        assertThat(result.visionSlotsWritten()).isZero();
        verify(llmProviderFactory, never()).getProvider(any());
    }

    @Test
    void visionSkippedForAudioSlotEvenWhenRequested() {
        when(storageService.isEnabled()).thenReturn(true);
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("voice", TurAIAgentSlotType.AUDIO)));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);

        MultiModalSlotResult result = service.upload(
                file("clip.mp3", "audio/mpeg", new byte[] { 1 }),
                agent(), CONV, "voice", true, List.of());

        assertThat(result.error()).isNull();
        assertThat(result.visionSlotsWritten()).isZero();
        verify(llmProviderFactory, never()).getProvider(any());
    }

    @Test
    void audioSlotTranscribesThroughSeamIntoTextSlot() {
        when(storageService.isEnabled()).thenReturn(true);
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID)).thenReturn(List.of(
                slot("voice", TurAIAgentSlotType.AUDIO),
                slot("notes", TurAIAgentSlotType.STRING)));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        when(transcriptionService.isAvailable()).thenReturn(true);
        when(transcriptionService.transcribe(any(), any(), any())).thenReturn(
                com.viglet.turing.genai.transcription.TurTranscriptionResult.ok("hello there", "en"));

        MultiModalSlotResult result = service.upload(
                file("clip.mp3", "audio/mpeg", new byte[] { 1, 2, 3 }),
                agent(), CONV, "voice", true, List.of());

        assertThat(result.error()).isNull();
        assertThat(result.visionSlotsWritten()).isEqualTo(1);
        assertThat(result.visionExtracted()).containsEntry("notes", "hello there");
        verify(engineService).writeSlot(eq(CONV), eq("notes"), eq("hello there"),
                eq(TurChatSlotAuditSource.EXTRACT), anyString());
        // The Gemini fallback must NOT run when the transcription seam handled it.
        verify(videoUnderstanding, never()).understand(any(), any(), any(), any());
    }

    @Test
    void buildObjectNameStripsPathTraversal() {
        String name = TurChatMultiModalSlotService.buildObjectName(
                "conv/../x", "my slot", "../../etc/passwd");
        // conversation/slot segments neutralise '/'; the filename's path
        // component is stripped to its base name ("passwd").
        assertThat(name)
                .isEqualTo("chat-slots/conv_.._x/my_slot/passwd")
                .doesNotContain("/..");
    }

    @Test
    void buildObjectNameDefaultsBlankFilename() {
        assertThat(TurChatMultiModalSlotService.buildObjectName(CONV, "photo", null))
                .isEqualTo("chat-slots/conv-1/photo/upload");
    }

    private TurLLMInstance enabledLlm() {
        TurLLMInstance llm = new TurLLMInstance();
        llm.setEnabled(1);
        llm.setApiKeyEncrypted("enc");
        return llm;
    }

    private ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
