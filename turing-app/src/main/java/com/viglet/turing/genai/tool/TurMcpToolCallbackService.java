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
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurMcpToolCallbackService {

    /** T1032 / §D.12 — the header an authenticated remote MCP server expects. */
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TurMcpServerRepositoryPort mcpServerRepositoryPort;
    private final com.viglet.turing.properties.TurMcpClientProperty mcpClientProperty;
    private final com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard;
    private final com.viglet.turing.system.security.TurSecretCryptoService secretCryptoService;
    private final ConcurrentHashMap<String, CachedClient> clientCache = new ConcurrentHashMap<>();

    public TurMcpToolCallbackService(TurMcpServerRepositoryPort mcpServerRepositoryPort,
            com.viglet.turing.properties.TurMcpClientProperty mcpClientProperty,
            com.viglet.turing.spring.security.ssrf.TurSsrfGuard ssrfGuard,
            com.viglet.turing.system.security.TurSecretCryptoService secretCryptoService) {
        this.mcpServerRepositoryPort = mcpServerRepositoryPort;
        this.mcpClientProperty = mcpClientProperty;
        this.ssrfGuard = ssrfGuard;
        this.secretCryptoService = secretCryptoService;
    }

    /**
     * T1032 / §D.12 — a live client together with the connection it was built
     * for, so an edit to that connection is detected rather than outlived.
     */
    private record CachedClient(String fingerprint, McpSyncClient client) {
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
                .forEach(this::evict);

        List<McpSyncClient> activeClients = new ArrayList<>();
        for (TurMcpServerDomain server : enabledServers) {
            try {
                activeClients.add(resolveClient(server));
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

    /**
     * T1032 / §D.12 — returns the cached client for a server, rebuilding it when
     * the connection it was built for has since been edited. Before this the
     * cache was keyed on the id alone, so a rotated credential (or a corrected
     * URL) kept the old client until the next restart.
     */
    private McpSyncClient resolveClient(TurMcpServerDomain server) {
        String fingerprint = connectionFingerprint(server);
        CachedClient cached = clientCache.get(server.id());
        if (cached != null) {
            if (fingerprint.equals(cached.fingerprint())) {
                return cached.client();
            }
            log.info("[MCP] Connection details changed for MCP server '{}' — reconnecting",
                    server.title());
            evict(server.id());
        }
        McpSyncClient client = createAndInitClient(server);
        clientCache.put(server.id(), new CachedClient(fingerprint, client));
        return client;
    }

    /** Drops a cached client and closes its transport. */
    private void evict(String serverId) {
        CachedClient removed = clientCache.remove(serverId);
        if (removed != null) {
            closeClient(removed.client());
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
            // T1032 / §D.12 — carry the per-server bearer token on every request
            // (the SSE GET included), so an authenticated remote MCP server is
            // reachable at all. Unauthenticated servers get no customizer, which
            // is byte-for-byte the request they got before.
            var authCustomizer = authorizationCustomizer(server);
            if (server.isStreamableHttp()) {
                var builder = HttpClientStreamableHttpTransport.builder(server.url());
                if (authCustomizer != null) {
                    builder.httpRequestCustomizer(authCustomizer);
                }
                return builder.build();
            }
            var builder = HttpClientSseClientTransport.builder(server.url());
            if (authCustomizer != null) {
                builder.httpRequestCustomizer(authCustomizer);
            }
            return builder.build();
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
     * T1032 / §D.12 — the request customizer that puts the server's bearer token
     * on every outbound HTTP request, or {@code null} when the server declares no
     * credential (so the transport is built exactly as it was before).
     *
     * <p>The token is decrypted here and nowhere earlier: the domain record that
     * carries it may be listed or logged, and a plaintext credential has no
     * business in one. A token that fails to decrypt — the key rotated out from
     * under a stored secret — yields no header rather than a broken one, and says
     * so once in the log; the server then fails its {@code initialize()} the way
     * an unauthenticated one does, which is the honest outcome.
     */
    McpSyncHttpClientRequestCustomizer authorizationCustomizer(TurMcpServerDomain server) {
        if (!server.hasAuthToken()) {
            return null;
        }
        String token;
        try {
            token = secretCryptoService.decrypt(server.authTokenEncrypted());
        } catch (Exception e) {
            log.warn("[MCP] Could not decrypt the stored credential for MCP server '{}': {}",
                    server.title(), e.getMessage());
            return null;
        }
        if (token == null || token.isBlank()) {
            log.warn("[MCP] Stored credential for MCP server '{}' decrypted to nothing",
                    server.title());
            return null;
        }
        String headerValue = BEARER_PREFIX + token.trim();
        return (requestBuilder, method, uri, body, context) ->
                requestBuilder.header(AUTHORIZATION_HEADER, headerValue);
    }

    /**
     * T1032 / §D.12 — identifies the connection a cached client was built for, so
     * a rotated credential (or an edited URL or transport) takes effect on the
     * next resolve instead of at the next restart. A stale client that keeps
     * presenting a revoked token is the failure mode worth spending a string
     * comparison to avoid.
     *
     * <p>The encrypted token is safe to include: it is ciphertext, and AES/GCM's
     * random IV means re-saving the same token changes it — which only causes a
     * harmless rebuild.
     */
    static String connectionFingerprint(TurMcpServerDomain server) {
        return String.join("|",
                String.valueOf(server.connectionType()),
                String.valueOf(server.transportType()),
                String.valueOf(server.url()),
                String.valueOf(server.command()),
                String.valueOf(server.args()),
                String.valueOf(server.authTokenEncrypted()));
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
        clientCache.values().forEach(cached -> closeClient(cached.client()));
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
