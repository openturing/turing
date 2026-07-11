package com.viglet.turing.genai.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.domain.mcp.TurMcpServerDomain;
import com.viglet.turing.domain.mcp.TurMcpServerRepositoryPort;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurMcpToolCallbackService {

    private final TurMcpServerRepositoryPort mcpServerRepositoryPort;
    private final com.viglet.turing.properties.TurMcpClientProperty mcpClientProperty;
    private final com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard;
    private final ConcurrentHashMap<String, McpSyncClient> clientCache = new ConcurrentHashMap<>();

    public TurMcpToolCallbackService(TurMcpServerRepositoryPort mcpServerRepositoryPort,
            com.viglet.turing.properties.TurMcpClientProperty mcpClientProperty,
            com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard) {
        this.mcpServerRepositoryPort = mcpServerRepositoryPort;
        this.mcpClientProperty = mcpClientProperty;
        this.ssrfGuard = ssrfGuard;
    }

    /**
     * Get tool callbacks only for the specified MCP servers (filtered by agent
     * configuration). The argument keeps its JPA-entity shape so callers that
     * already hold {@code agent.getMcpServers()} need not project — internally
     * we collapse the set to ids and resolve through the port.
     */
    public ToolCallback[] getToolCallbacks(Set<TurMcpServer> mcpServers) {
        if (mcpServers == null || mcpServers.isEmpty()) {
            return new ToolCallback[0];
        }
        Set<String> allowedIds = mcpServers.stream()
                .map(TurMcpServer::getId)
                .collect(Collectors.toSet());

        List<TurMcpServerDomain> enabledServers = mcpServerRepositoryPort.findAllEnabled().stream()
                .filter(s -> allowedIds.contains(s.id()))
                .toList();

        return getToolCallbacksForServers(enabledServers);
    }

    public ToolCallback[] getToolCallbacks() {
        return getToolCallbacksForServers(mcpServerRepositoryPort.findAllEnabled());
    }

    private ToolCallback[] getToolCallbacksForServers(List<TurMcpServerDomain> enabledServers) {
        if (enabledServers.isEmpty()) {
            return new ToolCallback[0];
        }

        // Remove stale clients
        Set<String> enabledIds = enabledServers.stream()
                .map(TurMcpServerDomain::id)
                .collect(Collectors.toSet());
        clientCache.keySet().stream()
                .filter(id -> !enabledIds.contains(id))
                .toList()
                .forEach(id -> {
                    McpSyncClient removed = clientCache.remove(id);
                    if (removed != null) {
                        closeClient(removed);
                    }
                });

        List<McpSyncClient> activeClients = new ArrayList<>();
        for (TurMcpServerDomain server : enabledServers) {
            try {
                McpSyncClient client = clientCache.computeIfAbsent(server.id(),
                        id -> createAndInitClient(server));
                activeClients.add(client);
            } catch (Exception e) {
                log.warn("[MCP] Failed to connect to MCP server '{}': {}", server.title(), e.getMessage());
                clientCache.remove(server.id());
            }
        }

        if (activeClients.isEmpty()) {
            return new ToolCallback[0];
        }

        try {
            var provider = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(activeClients)
                    .build();
            return provider.getToolCallbacks();
        } catch (Exception e) {
            log.warn("[MCP] Failed to get tool callbacks: {}", e.getMessage());
            return new ToolCallback[0];
        }
    }

    private McpSyncClient createAndInitClient(TurMcpServerDomain server) {
        McpClientTransport transport = createTransport(server);
        McpSyncClient client = McpClient.sync(transport)
                // MCP SDK 2.0.0-RC1 deprecated the Implementation(name, version)
                // constructor in favour of the builder.
                .clientInfo(McpSchema.Implementation.builder("turing", "2026.1").build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
        log.info("[MCP] Connected to MCP server '{}' ({})", server.title(), server.connectionType());
        return client;
    }

    // T294 — per-server HTTP transport choice. Streamable HTTP and legacy
    // SSE are DIFFERENT wire protocols, not interchangeable: SSE is the
    // two-endpoint handshake (GET /sse opens the stream + an `endpoint` event
    // tells the client where to POST), whereas Streamable HTTP is a single
    // endpoint (default /mcp) with no fallback to the SSE handshake — pointing
    // it at an SSE-only server would break the connection. So each HTTP server
    // declares its transport via TurMcpServer.transportType; a null value
    // (legacy rows) defaults to SSE so existing servers are unaffected.
    //
    // HttpClientSseClientTransport is @Deprecated (NOT forRemoval) in MCP SDK
    // 2.0.0; we still offer it as an explicit per-server choice, so the
    // suppression stays as long as SSE remains selectable.
    @SuppressWarnings("deprecation")
    private McpClientTransport createTransport(TurMcpServerDomain server) {
        if (server.connectionType() == TurMcpServerConnectionType.HTTP) {
            // T650 / §XXXVII.12 — optional SSRF guard on the client URL.
            if (mcpClientProperty.isBlockPrivateUrls() && !ssrfGuard.isAllowedUrl(server.url())) {
                throw new IllegalArgumentException(
                        "MCP client URL is not an allowed egress target: " + server.url());
            }
            if (server.isStreamableHttp()) {
                return HttpClientStreamableHttpTransport.builder(server.url()).build();
            }
            return HttpClientSseClientTransport.builder(server.url()).build();
        } else {
            // T650 / §XXXVII.12 — optional stdio command allowlist (RCE-by-config).
            assertStdioCommandAllowed(server.command());
            String[] args = server.args() != null && !server.args().isBlank()
                    ? server.args().split("\\s+")
                    : new String[0];
            ServerParameters params = ServerParameters.builder(server.command())
                    .args(args)
                    .build();
            return new StdioClientTransport(params, McpJsonDefaults.getMapper());
        }
    }

    /**
     * T650 / §XXXVII.12 — when {@code turing.mcp-client.allowed-stdio-commands} is
     * configured, refuse a stdio MCP server whose command's base name is not in
     * the allowlist (blocks arbitrary local-process execution by configuration).
     * Empty allowlist = legacy (any command allowed).
     */
    private void assertStdioCommandAllowed(String command) {
        if (!isStdioCommandAllowed(command, mcpClientProperty.getAllowedStdioCommands())) {
            throw new IllegalArgumentException(
                    "MCP client stdio command '" + command + "' is not in the allowed-stdio-commands allowlist.");
        }
    }

    /**
     * Pure allowlist check (package-visible for tests). Empty/null allowlist =
     * legacy (any command allowed). Otherwise the command's base name must match
     * an allowlist entry case-insensitively.
     */
    static boolean isStdioCommandAllowed(String command, java.util.List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        String base = command == null ? "" : command.trim().replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        final String baseName = base.toLowerCase();
        return allowed.stream().anyMatch(a -> a != null && a.trim().equalsIgnoreCase(baseName));
    }

    @PreDestroy
    public void cleanup() {
        clientCache.values().forEach(this::closeClient);
        clientCache.clear();
        log.info("[MCP] All MCP clients closed");
    }

    private void closeClient(McpSyncClient client) {
        try {
            client.close();
        } catch (Exception e) {
            log.debug("[MCP] Error closing MCP client", e);
        }
    }
}
