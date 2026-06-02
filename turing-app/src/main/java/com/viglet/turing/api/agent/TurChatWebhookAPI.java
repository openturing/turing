/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurChatWebhookDto;
import com.viglet.turing.persistence.model.agent.TurChatWebhook;
import com.viglet.turing.persistence.repository.agent.TurChatWebhookRepository;
import com.viglet.turing.service.chatslots.TurChatWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T62 / §VII.6.c — CRUD for {@link TurChatWebhook}, the catalog of outbound
 * webhooks that POST chat transcripts/slots to a CRM. Deployment-wide (not
 * agent-scoped): one "push-to-salesforce" webhook serves every flow.
 *
 * <p>The {@code authHeader} secret is write-only — reads return only a
 * {@code hasAuthHeader} flag, and a blank value on update preserves the
 * stored credential (so an admin can edit the URL without re-typing the
 * token).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/genai/webhook")
@Tag(name = "Chat Webhooks", description = "Outbound webhooks fired on handoff or slot writes")
public class TurChatWebhookAPI {

    private final TurChatWebhookRepository repository;
    private final TurChatWebhookService webhookService;

    public TurChatWebhookAPI(TurChatWebhookRepository repository,
            TurChatWebhookService webhookService) {
        this.repository = repository;
        this.webhookService = webhookService;
    }

    @Operation(summary = "List all webhooks")
    @GetMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public List<TurChatWebhookDto> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(TurChatWebhook::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(TurChatWebhookDto::from)
                .toList();
    }

    @Operation(summary = "Get one webhook by id")
    @GetMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_VIEW" })
    public TurChatWebhookDto get(@PathVariable String id) {
        return repository.findById(id)
                .map(TurChatWebhookDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook not found"));
    }

    @Operation(summary = "Create a new webhook")
    @PostMapping
    @Secured({ "ROLE_ADMIN", "AI_AGENT_CREATE" })
    public TurChatWebhookDto create(@RequestBody TurChatWebhookDto dto) {
        validate(dto);
        repository.findByName(dto.name()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Webhook name already exists");
        });
        TurChatWebhook entity = new TurChatWebhook();
        applyDtoToEntity(dto, entity);
        entity.setId(null);
        // On create there is no prior cipher to keep.
        TurChatWebhook saved = webhookService.save(entity, dto.authHeader(), false, null);
        return TurChatWebhookDto.from(saved);
    }

    @Operation(summary = "Update an existing webhook")
    @PutMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_EDIT" })
    public TurChatWebhookDto update(@PathVariable String id, @RequestBody TurChatWebhookDto dto) {
        validate(dto);
        TurChatWebhook existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook not found"));
        if (!existing.getName().equals(dto.name())) {
            repository.findByName(dto.name())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Webhook name already exists");
                    });
        }
        String existingCipher = existing.getAuthHeader();
        applyDtoToEntity(dto, existing);
        // Blank authHeader on update = keep the stored secret untouched.
        TurChatWebhook saved = webhookService.save(existing, dto.authHeader(), true, existingCipher);
        return TurChatWebhookDto.from(saved);
    }

    @Transactional
    @Operation(summary = "Delete a webhook")
    @DeleteMapping("/{id}")
    @Secured({ "ROLE_ADMIN", "AI_AGENT_DELETE" })
    public boolean delete(@PathVariable String id) {
        return repository.findById(id)
                .map(w -> {
                    repository.delete(id);
                    return true;
                }).orElse(false);
    }

    private static final java.util.Set<String> ALLOWED_METHODS =
            java.util.Set.of("POST", "PUT", "PATCH", "GET", "DELETE");

    private static void applyDtoToEntity(TurChatWebhookDto dto, TurChatWebhook entity) {
        entity.setName(dto.name().trim());
        entity.setDescription(dto.description());
        entity.setTargetUrl(dto.targetUrl().trim());
        entity.setHttpMethod(normalizeMethod(dto.httpMethod()));
        entity.setHeadersJson(dto.headersJson());
        entity.setSlotTrigger(TurChatWebhookService.normalizeTrigger(dto.slotTrigger()));
        entity.setIncludeSlots(dto.includeSlots());
        entity.setPayloadTemplate(dto.payloadTemplate());
        entity.setEnabled(Optional.ofNullable(dto.enabled()).orElse(Boolean.TRUE));
    }

    /** Defaults blank to POST; rejects anything outside the allow-list. */
    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "POST";
        }
        String upper = method.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_METHODS.contains(upper)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported HTTP method: " + method + " (allowed: " + ALLOWED_METHODS + ")");
        }
        return upper;
    }

    private static void validate(TurChatWebhookDto dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook name is required");
        }
        if (dto.name().length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook name is too long");
        }
        if (!dto.name().matches("[A-Za-z_][A-Za-z0-9_.\\-]*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Webhook name must start with a letter or underscore and contain only letters, "
                            + "digits, underscore, hyphen, or dot");
        }
        String url = dto.targetUrl();
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target URL is required");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target URL must be an absolute http(s) URL");
        }
    }
}
