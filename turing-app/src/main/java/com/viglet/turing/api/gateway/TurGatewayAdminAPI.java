/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.gateway;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.distillation.TurOpenAiDistillationService;
import com.viglet.turing.genai.distillation.TurOpenAiDistillationService.DistillResult;
import com.viglet.turing.genai.gateway.TurGatewayKeyService;
import com.viglet.turing.genai.gateway.TurGatewayKeyService.GeneratedKey;
import com.viglet.turing.genai.gateway.TurGatewayTrafficCaptureService;
import com.viglet.turing.genai.gateway.TurGatewayUsageService;
import com.viglet.turing.genai.gateway.TurGatewayUsageService.KeyBudgetStatus;
import com.viglet.turing.genai.gateway.TurGatewayUsageService.KeyUsageRow;
import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.persistence.repository.gateway.TurGatewayKeyRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T747 / §XLIX — admin/observability surface for the Governed LLM Gateway
 * (Block AZ). Unlike the anonymous {@code /v1/*} egress this is an authenticated
 * console API ({@code ROLE_ADMIN}) that surfaces inbound spend keyed by virtual
 * key. Virtual-key CRUD (T748) is added to this controller.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/gateway")
@Tag(name = "LLM Gateway Admin", description = "Block AZ — virtual keys + spend")
public class TurGatewayAdminAPI {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final TurGatewayUsageService usageService;
    private final TurGatewayKeyService keyService;
    private final TurGatewayKeyRepository keyRepository;
    private final TurGatewayTrafficCaptureService trafficCapture;
    private final TurOpenAiDistillationService distillationService;

    public TurGatewayAdminAPI(TurGatewayUsageService usageService,
            TurGatewayKeyService keyService,
            TurGatewayKeyRepository keyRepository,
            TurGatewayTrafficCaptureService trafficCapture,
            TurOpenAiDistillationService distillationService) {
        this.usageService = usageService;
        this.keyService = keyService;
        this.keyRepository = keyRepository;
        this.trafficCapture = trafficCapture;
        this.distillationService = distillationService;
    }

    // ---- Traffic → eval/distillation bridge (T746) ------------------------

    /** Capture status: whether traffic capture is on and the growing dataset id. */
    public record TrafficCaptureStatus(boolean enabled, String datasetId) {
    }

    /** Propose-only distillation outcome (never auto-applied). */
    public record DistillReview(boolean started, String reason, String jobId) {
    }

    @Operation(summary = "Gateway traffic-capture status (dataset feeding the eval bridge)")
    @GetMapping("/traffic")
    @Secured({"ROLE_ADMIN"})
    public TrafficCaptureStatus trafficStatus() {
        return new TrafficCaptureStatus(trafficCapture.isEnabled(), trafficCapture.currentDatasetId());
    }

    /**
     * Propose a distilled/cheaper model for an agent from its captured traffic —
     * the "gateway that learns" bet. Delegates to the propose-only distillation
     * path ({@code distillForReview}); it is <b>never</b> auto-applied (a human
     * curates the resulting suggestion). Opt-in behind {@code turing.distillation.enabled}.
     */
    @Operation(summary = "Propose a distilled model from captured traffic (never auto-applied)")
    @PostMapping("/traffic/distill")
    @Secured({"ROLE_ADMIN"})
    public DistillReview distill(@RequestParam String agentId) {
        DistillResult result = distillationService.distillForReview(agentId);
        return new DistillReview(result.started(), result.reason(),
                result.job() != null ? result.job().getId() : null);
    }

    // ---- Virtual-key CRUD (T748) -----------------------------------------

    /** Safe projection of a virtual key — never carries the secret or its hash. */
    public record KeyView(String id, String name, String keyPrefix, String allowedModels,
            String tenantId, int enabled, LocalDateTime expiresAt, LocalDateTime creationDate,
            Double monthlyBudgetUsd, String budgetDowngradeLlmId, Double hardMonthlyCapUsd,
            Integer rateLimitPerMinute) {
    }

    /** Create/update payload (scope + budget + rate limit; secret never accepted). */
    public record KeyUpsertRequest(String name, String allowedModels, LocalDateTime expiresAt,
            Double monthlyBudgetUsd, String budgetDowngradeLlmId, Double hardMonthlyCapUsd,
            Integer rateLimitPerMinute, Integer enabled) {
    }

    /** Create/rotate response — the raw {@code sk-turing-...} key is shown exactly once. */
    public record CreatedKey(KeyView key, String rawKey) {
    }

    @Operation(summary = "List virtual keys (never returns the secret)")
    @GetMapping("/keys")
    @Secured({"ROLE_ADMIN"})
    public List<KeyView> listKeys() {
        return keyRepository.findAll().stream().map(TurGatewayAdminAPI::toView).toList();
    }

    @Operation(summary = "Create a virtual key — raw key returned once")
    @PostMapping("/keys")
    @Secured({"ROLE_ADMIN"})
    public CreatedKey createKey(@RequestBody KeyUpsertRequest request) {
        GeneratedKey generated = keyService.create(request.name(), request.allowedModels(),
                request.expiresAt(), null);
        TurGatewayKey key = generated.key();
        applyBudget(key, request);
        keyRepository.save(key);
        return new CreatedKey(toView(key), generated.rawKey());
    }

    @Operation(summary = "Update a virtual key's scope / budget / rate limit")
    @PutMapping("/keys/{id}")
    @Secured({"ROLE_ADMIN"})
    public KeyView updateKey(@PathVariable String id, @RequestBody KeyUpsertRequest request) {
        TurGatewayKey key = keyRepository.findById(id)
                .orElseThrow(() -> TurNotFoundException.of("Gateway key", id));
        if (request.name() != null && !request.name().isBlank()) {
            key.setName(request.name());
        }
        key.setAllowedModels(request.allowedModels());
        key.setExpiresAt(request.expiresAt());
        if (request.enabled() != null) {
            key.setEnabled(request.enabled());
        }
        applyBudget(key, request);
        key.setModificationDate(LocalDateTime.now());
        keyRepository.save(key);
        return toView(key);
    }

    @Operation(summary = "Rotate a virtual key's secret — new raw key returned once")
    @PostMapping("/keys/{id}/rotate")
    @Secured({"ROLE_ADMIN"})
    public CreatedKey rotateKey(@PathVariable String id) {
        TurGatewayKey key = keyRepository.findById(id)
                .orElseThrow(() -> TurNotFoundException.of("Gateway key", id));
        GeneratedKey rotated = keyService.rotate(key);
        return new CreatedKey(toView(rotated.key()), rotated.rawKey());
    }

    @Operation(summary = "Revoke (delete) a virtual key")
    @DeleteMapping("/keys/{id}")
    @Secured({"ROLE_ADMIN"})
    public boolean deleteKey(@PathVariable String id) {
        keyRepository.findById(id).ifPresent(keyRepository::delete);
        return true;
    }

    private void applyBudget(TurGatewayKey key, KeyUpsertRequest request) {
        key.setMonthlyBudgetUsd(request.monthlyBudgetUsd());
        key.setBudgetDowngradeLlmId(request.budgetDowngradeLlmId());
        key.setHardMonthlyCapUsd(request.hardMonthlyCapUsd());
        key.setRateLimitPerMinute(request.rateLimitPerMinute());
    }

    private static KeyView toView(TurGatewayKey k) {
        return new KeyView(k.getId(), k.getName(), k.getKeyPrefix(), k.getAllowedModels(),
                k.getTenantId(), k.getEnabled(), k.getExpiresAt(), k.getCreationDate(),
                k.getMonthlyBudgetUsd(), k.getBudgetDowngradeLlmId(), k.getHardMonthlyCapUsd(),
                k.getRateLimitPerMinute());
    }

    @Operation(summary = "Per-virtual-key spend / tokens / requests over a window")
    @GetMapping("/usage")
    @Secured({"ROLE_ADMIN"})
    public List<KeyUsageRow> usage(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return usageService.usageByKey(parseFrom(from), parseTo(to));
    }

    @Operation(summary = "Month-to-date budget status per virtual key")
    @GetMapping("/budget-status")
    @Secured({"ROLE_ADMIN"})
    public List<KeyBudgetStatus> budgetStatus() {
        return usageService.budgetStatus();
    }

    private LocalDateTime parseFrom(String from) {
        if (from != null && !from.isBlank()) {
            return LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        return LocalDate.now(ZoneId.systemDefault()).minusDays(DEFAULT_WINDOW_DAYS).atStartOfDay();
    }

    private LocalDateTime parseTo(String to) {
        if (to != null && !to.isBlank()) {
            return LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1).atTime(LocalTime.MIN);
        }
        return LocalDate.now(ZoneId.systemDefault()).plusDays(1).atStartOfDay();
    }
}
