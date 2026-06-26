/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.mcp.TurMcpFederationDirectory.FederatedMcp;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;

class TurMcpFederationDirectoryTest {

    private final TurMcpFederationDirectory directory = new TurMcpFederationDirectory();

    private static TurMcpServer httpServer(String id, String title, String url) {
        TurMcpServer server = new TurMcpServer();
        server.setId(id);
        server.setTitle(title);
        server.setUrl(url);
        server.setConnectionType(TurMcpServerConnectionType.HTTP);
        server.setEnabled(1);
        return server;
    }

    private static TurAIAgent agentWith(boolean federation, TurMcpServer... servers) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle("agent");
        agent.setMcpNativeFederation(federation);
        agent.setMcpServers(new LinkedHashSet<>(Set.of(servers)));
        return agent;
    }

    @Test
    void federationOffYieldsNothing() {
        TurAIAgent agent = agentWith(false, httpServer("1", "Acme", "https://mcp.acme.com/sse"));
        assertThat(directory.federate(agent, "openai")).isEmpty();
        assertThat(directory.federate(agent, "anthropic")).isEmpty();
    }

    @Test
    void federatesHttpServerToOpenAiCapability() {
        TurAIAgent agent = agentWith(true, httpServer("1", "Acme Docs", "https://mcp.acme.com/sse"));
        List<FederatedMcp> federated = directory.federate(agent, "openai");
        assertThat(federated).hasSize(1);
        assertThat(federated.get(0).serverId()).isEqualTo("1");
        assertThat(federated.get(0).capability().capability())
                .isEqualTo(TurNativeCapability.OPENAI_MCP);
        // slug + url end up in the config the OpenAI buildTools reads.
        assertThat(federated.get(0).capability().configJson())
                .contains("https://mcp.acme.com/sse")
                .contains("acme_docs");
    }

    @Test
    void federatesHttpServerToAnthropicCapability() {
        TurAIAgent agent = agentWith(true, httpServer("1", "Acme", "https://mcp.acme.com/sse"));
        List<FederatedMcp> federated = directory.federate(agent, "anthropic");
        assertThat(federated).hasSize(1);
        assertThat(federated.get(0).capability().capability())
                .isEqualTo(TurNativeCapability.ANTHROPIC_MCP);
    }

    @Test
    void skipsDisabledServers() {
        TurMcpServer disabled = httpServer("1", "Acme", "https://mcp.acme.com/sse");
        disabled.setEnabled(0);
        assertThat(directory.federate(agentWith(true, disabled), "openai")).isEmpty();
    }

    @Test
    void skipsCommandServers() {
        TurMcpServer command = httpServer("1", "Local", "https://ignored");
        command.setConnectionType(TurMcpServerConnectionType.COMMAND);
        assertThat(directory.federate(agentWith(true, command), "openai")).isEmpty();
    }

    @Test
    void unknownVendorYieldsNothing() {
        TurAIAgent agent = agentWith(true, httpServer("1", "Acme", "https://mcp.acme.com/sse"));
        assertThat(directory.federate(agent, "gemini")).isEmpty();
    }

    @Test
    void slugifyFallsBackToServerIdWhenTitleHasNoUsableChars() {
        assertThat(TurMcpFederationDirectory.slugify("***", "abc12345xyz")).isEqualTo("mcp_abc12345");
        assertThat(TurMcpFederationDirectory.slugify("My Server!", "id")).isEqualTo("my_server");
    }
}
