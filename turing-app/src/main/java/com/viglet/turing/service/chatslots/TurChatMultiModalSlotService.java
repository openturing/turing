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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.genai.TurChatAttachmentService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlotType;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Multi-modal slot ingestion (T64). Where {@link TurChatSlotExtractionService}
 * turns a document into <em>scalar</em> slot values, this service handles
 * binary uploads that target a <em>multi-modal</em> slot
 * ({@code IMAGE}/{@code AUDIO}/{@code FILE}, see {@link TurAIAgentSlotType}):
 *
 * <ol>
 *   <li>The uploaded binary is persisted to object storage
 *       ({@link TurStorageService} — MinIO / S3 / filesystem) under a
 *       conversation-scoped prefix {@code chat-slots/{conversationId}/{slot}/}.</li>
 *   <li>The resolved <em>preview URL</em> ({@code /api/asset/preview?objectName=…})
 *       is written into the slot via {@link TurChatFlowEngineService#writeSlot}
 *       with audit source {@link TurChatSlotAuditSource#UPLOAD} — so a React
 *       component renders the image / audio / download link straight from the
 *       slot value, and the SSE channel delivers it in &lt;100 ms.</li>
 *   <li><b>Optionally</b>, for {@code IMAGE} slots the bytes are handed to the
 *       agent's vision-capable LLM as a Spring AI {@link Media} block to extract
 *       further scalar slots — the headline "photo of a CV → Vision LLM → name /
 *       cargo / objetivo slots" use case. Those follow-up writes are tagged
 *       {@link TurChatSlotAuditSource#EXTRACT}, identical to the document path,
 *       so the audit timeline reads consistently.</li>
 * </ol>
 *
 * <p>The slot value is the URL rather than the binary itself, keeping the
 * {@code chat_flow_state.variablesJson} map small and letting Custom Tools /
 * downstream consumers resolve the binary on demand through the storage layer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatMultiModalSlotService {

    /** Conversation-scoped object-storage prefix for multi-modal slot binaries. */
    static final String SLOT_STORAGE_PREFIX = "chat-slots/";

    /** Document extraction trims very large texts; vision needs only the bytes. */
    private final TurStorageService storageService;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService cryptoService;
    private final TurChatFlowEngineService engineService;
    private final TurAIAgentSlotRepository slotRepository;

    public TurChatMultiModalSlotService(TurStorageService storageService,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService cryptoService,
            TurChatFlowEngineService engineService,
            TurAIAgentSlotRepository slotRepository) {
        this.storageService = storageService;
        this.llmProviderFactory = llmProviderFactory;
        this.cryptoService = cryptoService;
        this.engineService = engineService;
        this.slotRepository = slotRepository;
    }

    /**
     * Outcome of one upload. {@code error} is non-null only on a failed
     * upload (storage disabled, unknown / non-multi-modal slot, empty file,
     * storage I/O error); in that case the binary was not persisted and no
     * slot was written. On success {@code url} is what landed in the slot and
     * {@code visionExtracted}/{@code visionSlotsWritten} describe the optional
     * vision pass (empty / 0 when vision was not requested or not applicable).
     */
    public record MultiModalSlotResult(
            String slotName,
            TurAIAgentSlotType slotType,
            String objectName,
            String url,
            String contentType,
            long size,
            Map<String, String> visionExtracted,
            int visionSlotsWritten,
            String error) {

        public static MultiModalSlotResult error(String message) {
            return new MultiModalSlotResult(null, null, null, null, null, 0,
                    Map.of(), 0, message);
        }
    }

    /**
     * Stores {@code file} into object storage and writes its URL into the
     * multi-modal slot named {@code slotName} on the live conversation.
     *
     * @param file             the binary upload (image / audio / any file)
     * @param agent            AI agent that owns the conversation + slot catalog
     * @param conversationId   sticky session id (same as the SDK cookie)
     * @param slotName         target slot; must exist on the agent and be of a
     *                         multi-modal type ({@code IMAGE}/{@code AUDIO}/{@code FILE})
     * @param runVision        when {@code true} and the slot is {@code IMAGE},
     *                         run a Vision LLM pass to fill scalar slots
     * @param visionSlotNames  optional whitelist of scalar slots the vision
     *                         pass may fill; null/empty → every scalar slot
     */
    public MultiModalSlotResult upload(MultipartFile file, TurAIAgent agent,
            String conversationId, String slotName, boolean runVision,
            List<String> visionSlotNames) {
        if (file == null || file.isEmpty()) {
            return MultiModalSlotResult.error("File is required");
        }
        if (agent == null) {
            return MultiModalSlotResult.error("No AI agent for the conversation");
        }
        if (conversationId == null || conversationId.isBlank()) {
            return MultiModalSlotResult.error("conversationId is required");
        }
        if (slotName == null || slotName.isBlank()) {
            return MultiModalSlotResult.error("slotName is required");
        }
        if (!storageService.isEnabled()) {
            return MultiModalSlotResult.error(
                    "Object storage is disabled — set turing.storage.type to minio or filesystem");
        }

        TurAIAgentSlot slot = findSlot(agent, slotName.trim());
        if (slot == null) {
            return MultiModalSlotResult.error("Unknown slot '" + slotName + "' on agent");
        }
        if (slot.getType() == null || !slot.getType().isMultiModal()) {
            return MultiModalSlotResult.error("Slot '" + slotName
                    + "' is not a multi-modal (IMAGE/AUDIO/FILE) slot");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("[MultiModalSlot] Could not read upload for slot '{}': {}", slotName, e.getMessage());
            return MultiModalSlotResult.error("Could not read uploaded file");
        }

        String contentType = resolveContentType(file);
        String objectName = buildObjectName(conversationId, slot.getName(), file.getOriginalFilename());

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            storageService.uploadStream(objectName, in, bytes.length, contentType);
        } catch (IOException | RuntimeException e) {
            log.warn("[MultiModalSlot] Storage upload failed for object '{}': {}", objectName, e.getMessage());
            return MultiModalSlotResult.error("Storage upload failed: " + e.getMessage());
        }

        String url = previewUrl(objectName);
        engineService.writeSlot(conversationId, slot.getName(), url,
                TurChatSlotAuditSource.UPLOAD, "agent=" + agent.getId());

        Map<String, String> visionExtracted = Map.of();
        int visionWritten = 0;
        if (runVision && slot.getType() == TurAIAgentSlotType.IMAGE && isImage(contentType)) {
            VisionResult vision = runVisionExtraction(bytes, contentType,
                    file.getOriginalFilename(), agent, conversationId, visionSlotNames);
            visionExtracted = vision.extracted();
            visionWritten = vision.written();
        }

        log.info("[MultiModalSlot] conv={} slot='{}' type={} object='{}' ({} bytes) visionWritten={}",
                conversationId, slot.getName(), slot.getType(), objectName, bytes.length, visionWritten);
        return new MultiModalSlotResult(slot.getName(), slot.getType(), objectName, url,
                contentType, bytes.length, visionExtracted, visionWritten, null);
    }

    private record VisionResult(Map<String, String> extracted, int written) { }

    /**
     * Vision pass: send the image to the agent's LLM as a {@link Media} block
     * and ask it to fill the agent's scalar slots. Failures are swallowed so a
     * vision miss never undoes the successful binary upload — the image slot is
     * already written; the operator can re-run extraction or ask the user.
     */
    private VisionResult runVisionExtraction(byte[] bytes, String contentType, String filename,
            TurAIAgent agent, String conversationId, List<String> visionSlotNames) {
        List<TurAIAgentSlot> targetSlots = resolveScalarTargetSlots(agent, visionSlotNames);
        if (targetSlots.isEmpty()) {
            log.info("[MultiModalSlot] No scalar slots to fill from image — vision skipped");
            return new VisionResult(Map.of(), 0);
        }
        ChatModel chatModel = buildChatModelForAgent(agent);
        if (chatModel == null) {
            log.warn("[MultiModalSlot] Agent '{}' has no enabled LLM — vision skipped", agent.getId());
            return new VisionResult(Map.of(), 0);
        }

        UserMessage userMessage = UserMessage.builder()
                .text("The attached image is a document (CV, form, receipt, etc.). "
                        + "Extract the slot values defined by the system prompt.")
                .media(Media.builder()
                        .mimeType(MimeType.valueOf(contentType))
                        .data(new ByteArrayResource(bytes))
                        .name(filename == null ? "image" : filename)
                        .build())
                .build();
        List<Message> messages = List.of(
                new SystemMessage(TurChatSlotExtractionService.buildSystemPrompt(targetSlots)),
                userMessage);

        String reply;
        try {
            reply = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
        } catch (RuntimeException e) {
            log.warn("[MultiModalSlot] Vision LLM call failed for conv={}: {}", conversationId, e.getMessage());
            return new VisionResult(Map.of(), 0);
        }

        Map<String, String> extracted = TurChatSlotExtractionService.parseJsonReply(reply, targetSlots);
        int written = 0;
        String originDetail = "vision-agent=" + agent.getId();
        for (Map.Entry<String, String> entry : extracted.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            int touched = engineService.writeSlot(conversationId, entry.getKey(), value,
                    TurChatSlotAuditSource.EXTRACT, originDetail);
            if (touched > 0) {
                written++;
            }
        }
        return new VisionResult(extracted, written);
    }

    private TurAIAgentSlot findSlot(TurAIAgent agent, String name) {
        return slotRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId()).stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }

    /** All scalar (non-multi-modal) slots, optionally filtered to a whitelist. */
    private List<TurAIAgentSlot> resolveScalarTargetSlots(TurAIAgent agent, List<String> requestedNames) {
        List<TurAIAgentSlot> all = slotRepository.findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        Set<String> wanted = (requestedNames == null) ? Set.of() : requestedNames.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<TurAIAgentSlot> filtered = new ArrayList<>();
        for (TurAIAgentSlot s : all) {
            if (s.getType() != null && s.getType().isMultiModal()) {
                continue;
            }
            if (wanted.isEmpty() || wanted.contains(s.getName())) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    private ChatModel buildChatModelForAgent(TurAIAgent agent) {
        TurLLMInstance llm = agent.getLlmInstances().stream()
                .filter(l -> l.getEnabled() == 1)
                .findFirst()
                .orElseGet(() -> agent.getLlmInstances().stream().findFirst().orElse(null));
        if (llm == null) {
            return null;
        }
        String apiKey = cryptoService.decrypt(llm.getApiKeyEncrypted());
        return llmProviderFactory.getProvider(llm).createChatModel(llm, apiKey);
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            String guessed = storageService.guessContentType(file.getOriginalFilename());
            if (guessed != null && !guessed.isBlank()) {
                return guessed;
            }
        }
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private static boolean isImage(String contentType) {
        return contentType != null && TurChatAttachmentService.IMAGE_MIME_TYPES.contains(contentType);
    }

    /**
     * {@code chat-slots/{conversationId}/{slot}/{filename}} — the conversation
     * + slot segments scope the binary so a later upload to the same slot lands
     * beside (not over) the previous one, and a conversation purge can delete
     * the whole prefix.
     */
    static String buildObjectName(String conversationId, String slotName, String originalFilename) {
        String filename = sanitizeFilename(originalFilename);
        return SLOT_STORAGE_PREFIX
                + sanitizeSegment(conversationId) + "/"
                + sanitizeSegment(slotName) + "/"
                + filename;
    }

    private static String sanitizeSegment(String segment) {
        String cleaned = segment.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "_" : cleaned;
    }

    private static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload";
        }
        // Strip any path component a browser might include, then neutralise
        // anything that isn't a safe filename character.
        String base = originalFilename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "upload" : cleaned;
    }

    private static String previewUrl(String objectName) {
        return "/api/asset/preview?objectName="
                + URLEncoder.encode(objectName, StandardCharsets.UTF_8);
    }
}
