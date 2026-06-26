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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.genai.TurChatAttachmentService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.utils.TurFileUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Generic document-to-slot extraction. The caller hands over an uploaded
 * file (PDF / DOCX / TXT / RTF / HTML — anything Tika auto-detects), a
 * conversation id, and a list of slot names; the service:
 *
 * <ol>
 *   <li>For text-bearing uploads, runs Tika
 *       ({@link TurFileUtils#documentToText}) to extract plain text. Same
 *       code path the asset-indexing RAG uses, so behavior is consistent
 *       across the platform. For <b>image</b> uploads (a phone-photo of a
 *       CV — {@code image/png|jpeg|gif|webp}, T99) Tika yields no text, so
 *       the bytes are instead sent to the agent's vision LLM as a Spring AI
 *       {@code Media} block — same extraction contract, no OCR dependency.</li>
 *   <li>Builds a structured-output prompt listing the target slot names
 *       and their descriptions (read from the agent's slot catalog), then
 *       calls the agent's configured LLM with the prompt + the extracted
 *       document text.</li>
 *   <li>Parses the JSON reply, writes each non-null field as a chat-flow
 *       slot via {@link TurChatFlowEngineService#writeSlot} (which also
 *       publishes SSE events so the React UI receives the values
 *       immediately), and returns the extracted map.</li>
 * </ol>
 *
 * <p>The use case is generic: a Programa-Match-style flow accepts a CV
 * to skip the 4 manual questions; a customer-service flow accepts a
 * delivery receipt to extract tracking ids; a billing flow accepts an
 * invoice PDF to extract amounts/dates. The service has no per-domain
 * knowledge — only what the agent's slot catalog declares.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurChatSlotExtractionService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP =
            new TypeReference<>() { };

    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService cryptoService;
    private final TurChatFlowEngineService engineService;
    private final TurAIAgentSlotRepository slotRepository;

    public TurChatSlotExtractionService(TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService cryptoService,
            TurChatFlowEngineService engineService,
            TurAIAgentSlotRepository slotRepository) {
        this.llmProviderFactory = llmProviderFactory;
        this.cryptoService = cryptoService;
        this.engineService = engineService;
        this.slotRepository = slotRepository;
    }

    /**
     * Result of one extraction call. {@code extracted} maps slot name to
     * the value the LLM produced (null when the LLM couldn't find a value
     * for that slot in the document — caller can decide whether to retry
     * with a different file or fall back to asking the user). {@code
     * slotsWritten} counts how many were actually persisted via the
     * engine; a value &lt; {@code extracted.size()} usually means the
     * conversation has no live flow state yet, so {@code writeSlot} was
     * a no-op for some slots.
     */
    public record SlotExtractionResult(
            Map<String, String> extracted,
            int slotsWritten,
            int extractedTextChars) {
        public static SlotExtractionResult empty() {
            return new SlotExtractionResult(Map.of(), 0, 0);
        }
    }

    /**
     * @param file              Multipart upload — Tika auto-detects the
     *                          MIME type and parses to plain text. Empty
     *                          / unparseable files return an empty result.
     * @param agent             AI agent that owns the conversation. The
     *                          first enabled LLM instance attached to the
     *                          agent is used for extraction.
     * @param conversationId    Sticky id of the visitor session — same id
     *                          the SDK's {@code TUR_SESSION} cookie holds.
     * @param requestedSlotNames Optional explicit list of slot names to
     *                          extract. When null/empty, all the agent's
     *                          declared slots become candidates.
     */
    public SlotExtractionResult extract(MultipartFile file, TurAIAgent agent,
            String conversationId, List<String> requestedSlotNames) {
        if (file == null || file.isEmpty() || agent == null
                || conversationId == null || conversationId.isBlank()) {
            return SlotExtractionResult.empty();
        }

        List<TurAIAgentSlot> targetSlots = resolveTargetSlots(agent, requestedSlotNames);
        if (targetSlots.isEmpty()) {
            log.warn("[SlotExtraction] No target slots resolved for agent '{}' — nothing to extract",
                    agent.getId());
            return SlotExtractionResult.empty();
        }

        ChatModel chatModel = buildChatModelForAgent(agent);
        if (chatModel == null) {
            log.warn("[SlotExtraction] Agent '{}' has no enabled LLM instance — extraction skipped",
                    agent.getId());
            return SlotExtractionResult.empty();
        }

        // T99 — a phone-photo of a CV (image/*) carries no machine-readable
        // text, so Tika returns empty and the legacy path silently extracted
        // nothing. Route images straight to the agent's vision LLM as a Media
        // block instead — same contract (buildSystemPrompt + parseJsonReply),
        // no Tesseract dependency, reusing the agent's already-configured model.
        String contentType = resolveContentType(file);
        if (isImage(contentType)) {
            return extractFromImage(file, contentType, targetSlots, chatModel,
                    agent, conversationId);
        }

        TurFileAttributes parsed = TurFileUtils.documentToText(file);
        String text = parsed == null ? null : parsed.getContent();
        if (text == null || text.isBlank()) {
            log.warn("[SlotExtraction] Tika produced empty content for file '{}'",
                    file.getOriginalFilename());
            return SlotExtractionResult.empty();
        }
        // Trim very large documents — most career documents are <20k chars,
        // and longer ones blow the LLM's context window on cheaper models.
        // The trim is from the END so the opening sections (which usually
        // hold the most relevant identity fields) survive.
        String trimmedText = text.length() > 24_000 ? text.substring(0, 24_000) : text;

        String systemPrompt = buildSystemPrompt(targetSlots);
        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(trimmedText));

        String reply;
        try {
            reply = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
        } catch (RuntimeException e) {
            log.warn("[SlotExtraction] LLM call failed for conv={}: {}", conversationId, e.getMessage());
            return SlotExtractionResult.empty();
        }
        Map<String, String> extracted = applyValidationPatterns(
                parseJsonReply(reply, targetSlots), targetSlots);

        int written = writeExtractedSlots(extracted, conversationId, "agent=" + agent.getId());
        log.info("[SlotExtraction] conv={} extracted={} written={} (textChars={})",
                conversationId, extracted.size(), written, trimmedText.length());
        return new SlotExtractionResult(extracted, written, trimmedText.length());
    }

    /**
     * Image extraction path (T99): sends the uploaded image to the agent's
     * vision-capable LLM as a Spring AI {@link Media} block and asks it to fill
     * the same target slots a text document would. The reply is parsed and
     * persisted identically to the Tika path. {@code extractedTextChars} is 0
     * because no text was extracted — callers reading that field as a "did we
     * get content?" signal should also inspect {@code slotsWritten}.
     */
    private SlotExtractionResult extractFromImage(MultipartFile file, String contentType,
            List<TurAIAgentSlot> targetSlots, ChatModel chatModel, TurAIAgent agent,
            String conversationId) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            log.warn("[SlotExtraction] Could not read image upload '{}': {}",
                    file.getOriginalFilename(), e.getMessage());
            return SlotExtractionResult.empty();
        }

        String filename = file.getOriginalFilename();
        UserMessage userMessage = UserMessage.builder()
                .text("The attached image is a document (CV, form, receipt, etc.). "
                        + "Read it and extract the slot values defined by the system prompt.")
                .media(Media.builder()
                        .mimeType(MimeType.valueOf(contentType))
                        .data(new ByteArrayResource(bytes))
                        .name(filename == null ? "image" : filename)
                        .build())
                .build();
        List<Message> messages = List.of(
                new SystemMessage(buildSystemPrompt(targetSlots)),
                userMessage);

        String reply;
        try {
            reply = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
        } catch (RuntimeException e) {
            log.warn("[SlotExtraction] Vision LLM call failed for conv={}: {}",
                    conversationId, e.getMessage());
            return SlotExtractionResult.empty();
        }
        Map<String, String> extracted = applyValidationPatterns(
                parseJsonReply(reply, targetSlots), targetSlots);
        int written = writeExtractedSlots(extracted, conversationId, "vision-agent=" + agent.getId());
        log.info("[SlotExtraction] (image) conv={} extracted={} written={} ({} bytes)",
                conversationId, extracted.size(), written, bytes.length);
        return new SlotExtractionResult(extracted, written, 0);
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    private static boolean isImage(String contentType) {
        return contentType != null && TurChatAttachmentService.IMAGE_MIME_TYPES.contains(contentType);
    }

    /**
     * Writes each non-blank extracted value to its slot, returning the number
     * of slots actually touched.
     */
    private int writeExtractedSlots(Map<String, String> extracted, String conversationId,
            String originDetail) {
        int written = 0;
        for (Map.Entry<String, String> entry : extracted.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) continue;
            int touched = engineService.writeSlot(conversationId, entry.getKey(), value,
                    TurChatSlotAuditSource.EXTRACT, originDetail);
            if (touched > 0) written++;
        }
        return written;
    }

    /**
     * Resolves which slots an extraction call targets. Order matters: the
     * prompt lists slots in agent-catalog order so it stays deterministic
     * across runs. Precedence:
     *
     * <ol>
     *   <li>An explicit caller-provided {@code requestedNames} subset always
     *       wins (legacy per-request behaviour, used by the SDK's
     *       {@code slotNames} param).</li>
     *   <li>Otherwise, the agent's <em>document schema</em> (T100): the slots
     *       flagged {@code extractFromDocument}. This lets an agent declare its
     *       extraction targets once in admin instead of the caller naming them
     *       per request.</li>
     *   <li>Otherwise (no flagged slot — an agent that predates T100), the
     *       whole catalog, preserving the original behaviour.</li>
     * </ol>
     */
    private List<TurAIAgentSlot> resolveTargetSlots(TurAIAgent agent, List<String> requestedNames) {
        List<TurAIAgentSlot> all = slotRepository
                .findByTurAIAgent_IdOrderByNameAsc(agent.getId());
        if (requestedNames != null && !requestedNames.isEmpty()) {
            Set<String> wanted = requestedNames.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            List<TurAIAgentSlot> filtered = new ArrayList<>();
            for (TurAIAgentSlot slot : all) {
                if (wanted.contains(slot.getName())) filtered.add(slot);
            }
            return filtered;
        }
        List<TurAIAgentSlot> schema = new ArrayList<>();
        for (TurAIAgentSlot slot : all) {
            if (slot.isExtractFromDocument()) schema.add(slot);
        }
        return schema.isEmpty() ? all : schema;
    }

    /**
     * Drops extracted values that fail their slot's {@code validationPattern}
     * (T100) so a malformed fill never reaches the slot. Slots with no pattern
     * pass through unchanged; an invalid regex is treated as "no validation"
     * (logged once) rather than silently dropping every value for that slot.
     */
    static Map<String, String> applyValidationPatterns(Map<String, String> extracted,
            List<TurAIAgentSlot> slots) {
        Map<String, Pattern> patterns = new HashMap<>();
        for (TurAIAgentSlot s : slots) {
            String raw = s.getValidationPattern();
            if (raw == null || raw.isBlank()) continue;
            try {
                patterns.put(s.getName(), Pattern.compile(raw.trim()));
            } catch (RuntimeException e) {
                log.warn("[SlotExtraction] Slot '{}' has an invalid validationPattern '{}' — ignored: {}",
                        s.getName(), raw, e.getMessage());
            }
        }
        if (patterns.isEmpty()) return extracted;
        Map<String, String> valid = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : extracted.entrySet()) {
            Pattern p = patterns.get(entry.getKey());
            String value = entry.getValue();
            if (p != null && value != null && !p.matcher(value).matches()) {
                log.info("[SlotExtraction] Dropping slot '{}' — value did not match validationPattern",
                        entry.getKey());
                continue;
            }
            valid.put(entry.getKey(), value);
        }
        return valid;
    }

    private ChatModel buildChatModelForAgent(TurAIAgent agent) {
        TurLLMInstance llm = agent.getLlmInstances().stream()
                .filter(l -> l.getEnabled() == 1)
                .findFirst()
                .orElseGet(() -> agent.getLlmInstances().stream().findFirst().orElse(null));
        if (llm == null) return null;
        String apiKey = cryptoService.decrypt(llm.getApiKeyEncrypted());
        return llmProviderFactory.getProvider(llm).createChatModel(llm, apiKey);
    }

    /**
     * Structured-output prompt: lists each target slot with its description
     * + asks the LLM to return a single JSON object. Phrased to be model-
     * agnostic — gpt-4o, claude-3.5, gemini-flash all follow the same
     * contract when the schema is small.
     */
    static String buildSystemPrompt(List<TurAIAgentSlot> slots) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a precision extractor. The user message is the FULL TEXT of a document ");
        sb.append("(curriculum, invoice, form, etc.). Extract values for the following slots:\n\n");
        for (TurAIAgentSlot slot : slots) {
            sb.append("- ").append(slot.getName());
            if (slot.getDescription() != null && !slot.getDescription().isBlank()) {
                sb.append(" — ").append(slot.getDescription().trim());
            }
            if (slot.getValidationPattern() != null && !slot.getValidationPattern().isBlank()) {
                sb.append(" (the value must match this regex: ")
                        .append(slot.getValidationPattern().trim()).append(')');
            }
            sb.append('\n');
        }
        sb.append("\nReturn a single JSON object whose keys are the slot names above and whose ");
        sb.append("values are STRINGS (or null when the document does not contain enough information ");
        sb.append("to fill that slot). Examples of valid replies:\n");
        sb.append("  {\"name\": \"Alexandre\", \"cargo_atual\": \"Gerente de Produto há 4 anos\", \"objetivo\": null}\n\n");
        sb.append("Hard rules:\n");
        sb.append("- Reply with the JSON object ONLY. No commentary, no markdown fences, no preamble.\n");
        sb.append("- Use null (not \"\" / not \"N/A\" / not \"unknown\") when you can't find a value.\n");
        sb.append("- Quote string values literally as they appear in the document (don't paraphrase).\n");
        sb.append("- Reply in the same language the document was written in.\n");
        return sb.toString();
    }

    /**
     * Parses the LLM reply tolerantly: handles bare JSON, code-fenced JSON
     * ({@code ```json ... ```}), and replies prefixed with whitespace. Keeps
     * only keys that match the requested slot names — any drift / extra
     * fields the model invented are discarded so the writeSlot loop can't
     * accidentally persist garbage.
     */
    static Map<String, String> parseJsonReply(String reply, List<TurAIAgentSlot> slots) {
        if (reply == null || reply.isBlank()) return Map.of();
        String body = stripCodeFences(reply.trim());
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> allowedKeys = new java.util.HashSet<>();
        for (TurAIAgentSlot s : slots) allowedKeys.add(s.getName());
        try {
            Map<String, String> raw = JSON.readValue(body, STRING_MAP);
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                if (allowedKeys.contains(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (RuntimeException e) {
            log.warn("[SlotExtraction] LLM reply was not parseable JSON ({} chars): {}",
                    body.length(), e.getMessage());
        }
        return result;
    }

    private static String stripCodeFences(String text) {
        // Tolerate ```json\n...\n``` and ```\n...\n``` wrappings — small
        // models often return them despite the prompt's "JSON only" rule.
        Matcher m = CODE_FENCE.matcher(text);
        return m.matches() ? m.group(1).trim() : text;
    }

    private static final Pattern CODE_FENCE = Pattern.compile(
            "(?s)```(?:json)?\\s*(.+?)\\s*```");
}
