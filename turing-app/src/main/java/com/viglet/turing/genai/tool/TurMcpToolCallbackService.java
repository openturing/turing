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

import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurMcpToolCallbackService {

    private final TurMcpServerRepository mcpServerRepository;
    private final ConcurrentHashMap<String, McpSyncClient> clientCache = new ConcurrentHashMap<>();

    public TurMcpToolCallbackService(TurMcpServerRepository mcpServerRepository) {
        this.mcpServerRepository = mcpServerRepository;
    }

    public ToolCallback[] getToolCallbacks() {
        List<TurMcpServer> enabledServers = mcpServerRepository.findAll().stream()
                .filter(s -> s.getEnabled() == 1)
                .toList();

        if (enabledServers.isEmpty()) {
            return new ToolCallback[0];
        }

        // Remove stale clients
        Set<String> enabledIds = enabledServers.stream()
                .map(TurMcpServer::getId)
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
        for (TurMcpServer server : enabledServers) {
            try {
                McpSyncClient client = clientCache.computeIfAbsent(server.getId(),
                        id -> createAndInitClient(server));
                activeClients.add(client);
            } catch (Exception e) {
                log.warn("[MCP] Failed to connect to MCP server '{}': {}", server.getTitle(), e.getMessage());
                clientCache.remove(server.getId());
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

    private McpSyncClient createAndInitClient(TurMcpServer server) {
        McpClientTransport transport = createTransport(server);
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("turing", "2026.1"))
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
        log.info("[MCP] Connected to MCP server '{}' ({})", server.getTitle(), server.getConnectionType());
        return client;
    }

    private McpClientTransport createTransport(TurMcpServer server) {
        if (server.getConnectionType() == TurMcpServerConnectionType.HTTP) {
            return HttpClientSseClientTransport.builder(server.getUrl()).build();
        } else {
            String[] args = server.getArgs() != null && !server.getArgs().isBlank()
                    ? server.getArgs().split("\\s+")
                    : new String[0];
            ServerParameters params = ServerParameters.builder(server.getCommand())
                    .args(args)
                    .build();
            return new StdioClientTransport(params, McpJsonMapper.getDefault());
        }
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
