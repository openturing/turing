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
package com.viglet.turing.api.agent;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.genai.connector.TurConnectorDescriptor;
import com.viglet.turing.genai.connector.TurConnectorSelfInstallService;
import com.viglet.turing.genai.connector.TurConnectorSelfInstallService.ConnectionTestRequest;
import com.viglet.turing.genai.connector.TurConnectorSelfInstallService.ConnectionTestResult;
import com.viglet.turing.genai.connector.TurConnectorSelfInstallService.ConnectorGuide;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * T190 / §X.15.d — "self-installing" enterprise connectors for an AI agent. The
 * agent walks a customer through connecting a third-party system: list the
 * catalogue, show the token-generation steps for a chosen connector, and TEST the
 * supplied credential before the connector is saved/enabled via the existing
 * integration / MCP-server CRUD.
 *
 * <p>Strictly opt-in: every endpoint requires
 * {@link TurAIAgent}'s {@code isConnectorSetupEnabled()} (403 otherwise). The catalogue,
 * guided steps, computer-use availability (T136) and the fail-soft connection
 * probe all live in {@link TurConnectorSelfInstallService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/ai-agent/{agentId}/connector")
@Tag(name = "AI Agent Connectors",
        description = "Self-installing enterprise connectors for an AI Agent")
public class TurAIAgentConnectorAPI {

    private final TurAIAgentRepository turAIAgentRepository;
    private final TurConnectorSelfInstallService connectorService;

    public TurAIAgentConnectorAPI(TurAIAgentRepository turAIAgentRepository,
            TurConnectorSelfInstallService connectorService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.connectorService = connectorService;
    }

    /** The catalogue of well-known connectors a customer can self-install. */
    @GetMapping("/catalog")
    public List<TurConnectorDescriptor> catalog(@PathVariable String agentId) {
        requireEnabled(agentId);
        return connectorService.catalog();
    }

    /** The guided token-generation steps for one connector. */
    @GetMapping("/{connectorKey}/guide")
    public ConnectorGuide guide(@PathVariable String agentId, @PathVariable String connectorKey) {
        requireEnabled(agentId);
        return connectorService.guide(connectorKey);
    }

    /** Test a supplied credential against a connector before it is saved/enabled. */
    @PostMapping("/test")
    public ConnectionTestResult test(@PathVariable String agentId,
            @RequestBody ConnectionTestRequest request) {
        requireEnabled(agentId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        return connectorService.testConnection(request);
    }

    private void requireEnabled(String agentId) {
        TurAIAgent agent = turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
        if (agent.getEnabled() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent is disabled");
        }
        if (!agent.isConnectorSetupEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Connector self-install is not enabled for this agent");
        }
    }
}
