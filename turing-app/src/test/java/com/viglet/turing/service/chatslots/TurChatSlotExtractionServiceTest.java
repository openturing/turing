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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
import com.viglet.turing.service.chatslots.TurChatSlotExtractionService.SlotExtractionResult;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Pure-Mockito unit tests for {@link TurChatSlotExtractionService}. Covers the
 * T99 image (vision) extraction path, which needs no Tika and is therefore
 * fully exercisable without static mocking.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatSlotExtractionServiceTest {

    private static final String CONV = "conv-1";
    private static final String AGENT_ID = "agent-1";

    @Mock private TurGenAiLlmProviderFactory llmProviderFactory;
    @Mock private TurSecretCryptoService cryptoService;
    @Mock private TurChatFlowEngineService engineService;
    @Mock private TurAIAgentSlotRepository slotRepository;

    private TurChatSlotExtractionService service;

    @BeforeEach
    void setUp() {
        service = new TurChatSlotExtractionService(llmProviderFactory, cryptoService,
                engineService, slotRepository);
    }

    private TurAIAgent agentWithLlm() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId(AGENT_ID);
        agent.setLlmInstances(java.util.Set.of(enabledLlm()));
        return agent;
    }

    private TurAIAgentSlot slot(String name) {
        TurAIAgentSlot s = new TurAIAgentSlot();
        s.setName(name);
        s.setType(TurAIAgentSlotType.STRING);
        return s;
    }

    private TurAIAgentSlot slot(String name, boolean extractFromDocument, String validationPattern) {
        TurAIAgentSlot s = slot(name);
        s.setExtractFromDocument(extractFromDocument);
        s.setValidationPattern(validationPattern);
        return s;
    }

    private MultipartFile file(String name, String contentType, byte[] bytes) {
        try {
            MultipartFile f = mock(MultipartFile.class);
            lenient().when(f.isEmpty()).thenReturn(bytes.length == 0);
            lenient().when(f.getBytes()).thenReturn(bytes);
            lenient().when(f.getContentType()).thenReturn(contentType);
            lenient().when(f.getOriginalFilename()).thenReturn(name);
            return f;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private void wireChatModel(ChatModel chatModel) {
        when(cryptoService.decrypt(any())).thenReturn("key");
        TurGenAiLlmProvider provider = mock(TurGenAiLlmProvider.class);
        when(llmProviderFactory.getProvider(any())).thenReturn(provider);
        when(provider.createChatModel(any(), eq("key"))).thenReturn(chatModel);
    }

    @Test
    void imageUploadRoutesToVisionAndWritesSlots() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name"), slot("cargo")));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "{\"name\":\"Alexandre\",\"cargo\":\"Gerente\"}"));
        wireChatModel(chatModel);

        SlotExtractionResult result = service.extract(
                file("cv.jpg", "image/jpeg", new byte[] { 1, 2, 3 }),
                agentWithLlm(), CONV, List.of());

        assertThat(result.extracted()).containsEntry("name", "Alexandre")
                .containsEntry("cargo", "Gerente");
        assertThat(result.slotsWritten()).isEqualTo(2);
        // No Tika text was read for an image — the signal is slotsWritten, not chars.
        assertThat(result.extractedTextChars()).isZero();
        // Vision writes are audited as EXTRACT, same as the text path.
        verify(engineService).writeSlot(eq(CONV), eq("name"), eq("Alexandre"),
                eq(TurChatSlotAuditSource.EXTRACT), anyString());
        verify(engineService).writeSlot(eq(CONV), eq("cargo"), eq("Gerente"),
                eq(TurChatSlotAuditSource.EXTRACT), anyString());
    }

    @Test
    void imageUploadSendsMediaBlockToModel() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name")));
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("{\"name\":\"X\"}"));
        wireChatModel(chatModel);

        service.extract(file("cv.png", "image/png", new byte[] { 9 }),
                agentWithLlm(), CONV, List.of());

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> messages = captor.getValue().getInstructions();
        UserMessage userMessage = (UserMessage) messages.get(messages.size() - 1);
        assertThat(userMessage.getMedia()).isNotEmpty();
    }

    @Test
    void imageUploadReturnsEmptyWhenAgentHasNoLlm() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name")));
        TurAIAgent agent = new TurAIAgent();
        agent.setId(AGENT_ID);
        agent.setLlmInstances(java.util.Set.of());

        SlotExtractionResult result = service.extract(
                file("cv.png", "image/png", new byte[] { 1 }), agent, CONV, List.of());

        assertThat(result.extracted()).isEmpty();
        verify(engineService, never()).writeSlot(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void returnsEmptyForBlankInputs() {
        assertThat(service.extract(null, agentWithLlm(), CONV, List.of()).extracted()).isEmpty();
        assertThat(service.extract(file("a.png", "image/png", new byte[] { 1 }),
                agentWithLlm(), "  ", List.of()).extracted()).isEmpty();
    }

    @Test
    void documentSchemaLimitsTargetsToFlaggedSlotsWhenNoNamesGiven() {
        // name is flagged extractFromDocument; cargo is not → only name is a target.
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID)).thenReturn(List.of(
                slot("name", true, null),
                slot("cargo", false, null)));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        // The model echoes both keys; parseJsonReply keeps only the target slot.
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("{\"name\":\"Alexandre\",\"cargo\":\"Gerente\"}"));
        wireChatModel(chatModel);

        SlotExtractionResult result = service.extract(
                file("cv.png", "image/png", new byte[] { 1 }), agentWithLlm(), CONV, List.of());

        assertThat(result.extracted()).containsOnlyKeys("name");
        verify(engineService, never()).writeSlot(eq(CONV), eq("cargo"), anyString(), any(), any());
    }

    @Test
    void confidenceFlagPopulatesConfidencesAndWritesScalarValue() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name"), slot("objetivo")));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "{\"name\":{\"value\":\"Alexandre\",\"confidence\":0.95},"
                        + "\"objetivo\":{\"value\":null,\"confidence\":0.1}}"));
        wireChatModel(chatModel);

        SlotExtractionResult result = service.extract(
                file("cv.png", "image/png", new byte[] { 1 }), agentWithLlm(), CONV, List.of(), true);

        // Scalar value written; confidence surfaced separately.
        assertThat(result.extracted()).containsEntry("name", "Alexandre");
        assertThat(result.confidences()).containsEntry("name", 0.95)
                .containsEntry("objetivo", 0.1);
        verify(engineService).writeSlot(eq(CONV), eq("name"), eq("Alexandre"),
                eq(TurChatSlotAuditSource.EXTRACT), anyString());
        // null value is not written.
        verify(engineService, never()).writeSlot(eq(CONV), eq("objetivo"), anyString(), any(), any());
    }

    @Test
    void withoutConfidenceFlagConfidencesMapIsEmpty() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name")));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("{\"name\":\"Alexandre\"}"));
        wireChatModel(chatModel);

        SlotExtractionResult result = service.extract(
                file("cv.png", "image/png", new byte[] { 1 }), agentWithLlm(), CONV, List.of(), false);

        assertThat(result.extracted()).containsEntry("name", "Alexandre");
        assertThat(result.confidences()).isEmpty();
    }

    @Test
    void parseJsonReplyWithConfidenceHandlesObjectAndPlainForms() {
        List<TurAIAgentSlot> slots = List.of(slot("a"), slot("b"), slot("c"));
        var parsed = TurChatSlotExtractionService.parseJsonReplyWithConfidence(
                "{\"a\":{\"value\":\"x\",\"confidence\":0.8},"
                        + "\"b\":\"plain\","                       // tolerated fallback form
                        + "\"c\":{\"value\":\"y\",\"confidence\":5}}", // out-of-range → clamped to 1.0
                slots);

        assertThat(parsed.get("a").value()).isEqualTo("x");
        assertThat(parsed.get("a").confidence()).isEqualTo(0.8);
        assertThat(parsed.get("b").value()).isEqualTo("plain");
        assertThat(parsed.get("b").confidence()).isNull();
        assertThat(parsed.get("c").confidence()).isEqualTo(1.0);
    }

    @Test
    void instanceFileUploadEnabledRoutesPdfThroughMediaWithoutRequestFlag() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name")));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("{\"name\":\"Alexandre\"}"));
        wireChatModel(chatModel);

        // Agent's LLM has fileUploadEnabled=true → binary passthrough fires even
        // though the request did NOT pass nativeBinary (passBinary=false).
        TurLLMInstance llm = enabledLlm();
        llm.setFileUploadEnabled(true);
        TurAIAgent agent = new TurAIAgent();
        agent.setId(AGENT_ID);
        agent.setLlmInstances(java.util.Set.of(llm));

        SlotExtractionResult result = service.extract(
                file("cv.pdf", "application/pdf", new byte[] { 1, 2 }),
                agent, CONV, List.of(), false, false);

        assertThat(result.extracted()).containsEntry("name", "Alexandre");
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> messages = captor.getValue().getInstructions();
        UserMessage userMessage = (UserMessage) messages.get(messages.size() - 1);
        assertThat(userMessage.getMedia()).isNotEmpty();
    }

    @Test
    void nativeBinaryRoutesPdfThroughMediaWithoutTika() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name")));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("{\"name\":\"Alexandre\"}"));
        wireChatModel(chatModel);

        // passBinary=true sends the PDF straight to the model as media — no Tika.
        SlotExtractionResult result = service.extract(
                file("cv.pdf", "application/pdf", new byte[] { 1, 2 }),
                agentWithLlm(), CONV, List.of(), false, true);

        assertThat(result.extracted()).containsEntry("name", "Alexandre");
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        List<Message> messages = captor.getValue().getInstructions();
        UserMessage userMessage = (UserMessage) messages.get(messages.size() - 1);
        assertThat(userMessage.getMedia()).isNotEmpty();
        assertThat(userMessage.getMedia().get(0).getMimeType().toString())
                .isEqualTo("application/pdf");
    }

    @Test
    void confidenceSystemPromptRequestsObjectForm() {
        String prompt = TurChatSlotExtractionService.buildSystemPrompt(List.of(slot("name")), true);
        assertThat(prompt).contains("\"confidence\"").contains("0.0 (guess) to 1.0");
    }

    @Test
    void applyValidationPatternsDropsNonMatchingValue() {
        List<TurAIAgentSlot> slots = List.of(
                slot("cpf", true, "^[0-9]{11}$"),
                slot("name", true, null));
        java.util.Map<String, String> in = new java.util.LinkedHashMap<>();
        in.put("cpf", "not-a-cpf");
        in.put("name", "Alexandre");

        java.util.Map<String, String> out =
                TurChatSlotExtractionService.applyValidationPatterns(in, slots);

        assertThat(out).containsOnlyKeys("name").containsEntry("name", "Alexandre");
    }

    @Test
    void multiDocWithImagesReasonsAcrossAndWritesSlot() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("skill_gap")));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("{\"skill_gap\":\"missing Kubernetes\"}"));
        wireChatModel(chatModel);

        SlotExtractionResult result = service.extractMulti(
                List.of(file("cv.png", "image/png", new byte[] { 1 }),
                        file("jd.png", "image/png", new byte[] { 2 })),
                agentWithLlm(), CONV, List.of());

        assertThat(result.extracted()).containsEntry("skill_gap", "missing Kubernetes");
        assertThat(result.slotsWritten()).isEqualTo(1);
        // Reasoned across both docs in a single model call.
        verify(chatModel).call(any(Prompt.class));
        verify(engineService).writeSlot(eq(CONV), eq("skill_gap"), eq("missing Kubernetes"),
                eq(TurChatSlotAuditSource.EXTRACT), anyString());
    }

    @Test
    void multiDocSingleNonEmptyFileDelegatesToExtract() {
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name")));
        when(engineService.writeSlot(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("{\"name\":\"Alexandre\"}"));
        wireChatModel(chatModel);

        // One non-empty file + one empty file → behaves like single-doc extract.
        SlotExtractionResult result = service.extractMulti(
                List.of(file("cv.png", "image/png", new byte[] { 1 }),
                        file("blank.png", "image/png", new byte[0])),
                agentWithLlm(), CONV, List.of());

        assertThat(result.extracted()).containsEntry("name", "Alexandre");
        assertThat(result.slotsWritten()).isEqualTo(1);
    }

    @Test
    void multiDocReturnsEmptyForAllEmptyOrNullFiles() {
        assertThat(service.extractMulti(List.of(), agentWithLlm(), CONV, List.of()).extracted())
                .isEmpty();
        assertThat(service.extractMulti(
                List.of(file("a.png", "image/png", new byte[0])),
                agentWithLlm(), CONV, List.of()).extracted()).isEmpty();
        verify(engineService, never()).writeSlot(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void multiDocSystemPromptDirectsCrossDocumentReasoning() {
        String prompt = TurChatSlotExtractionService.buildMultiDocSystemPrompt(
                List.of(slot("skill_gap")));
        assertThat(prompt).contains("MULTIPLE documents").contains("reason ACROSS");
    }

    @Test
    void applyValidationPatternsKeepsMatchingAndIgnoresInvalidRegex() {
        List<TurAIAgentSlot> slots = List.of(
                slot("cpf", true, "^[0-9]{11}$"),
                slot("broken", true, "([unclosed"));
        java.util.Map<String, String> in = new java.util.LinkedHashMap<>();
        in.put("cpf", "12345678901");
        in.put("broken", "anything");

        java.util.Map<String, String> out =
                TurChatSlotExtractionService.applyValidationPatterns(in, slots);

        // valid CPF kept; an uncompilable pattern is treated as "no validation".
        assertThat(out).containsEntry("cpf", "12345678901").containsEntry("broken", "anything");
    }
}
