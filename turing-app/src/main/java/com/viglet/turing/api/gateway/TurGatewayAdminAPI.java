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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.gateway.TurGatewayUsageService;
import com.viglet.turing.genai.gateway.TurGatewayUsageService.KeyBudgetStatus;
import com.viglet.turing.genai.gateway.TurGatewayUsageService.KeyUsageRow;

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

    public TurGatewayAdminAPI(TurGatewayUsageService usageService) {
        this.usageService = usageService;
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
