package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import com.viglet.turing.domain.mcp.TurMcpServerDomain;
import com.viglet.turing.domain.mcp.TurMcpServerRepositoryPort;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerTransportType;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Tests for TurMcpToolCallbackService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurMcpToolCallbackServiceTest {

    @Mock
    private TurMcpServerRepositoryPort mcpServerRepositoryPort;

    @Mock
    private TurSecretCryptoService secretCryptoService;

    private TurMcpToolCallbackService service;

    @BeforeEach
    void setUp() {
        service = new TurMcpToolCallbackService(mcpServerRepositoryPort,
                new com.viglet.turing.properties.TurMcpClientProperty(),
                new com.viglet.turing.spring.security.ssrf.TurSsrfGuard(),
                secretCryptoService);
    }

    // T650 / §XXXVII.12 — stdio command allowlist (RCE-by-config guard).
    @Test
    void emptyAllowlistPermitsAnyCommand() {
        assertThat(TurMcpToolCallbackService.isStdioCommandAllowed("npx", List.of())).isTrue();
        assertThat(TurMcpToolCallbackService.isStdioCommandAllowed("/usr/bin/anything", null)).isTrue();
    }

    @Test
    void nonEmptyAllowlistMatchesBaseNameCaseInsensitively() {
        List<String> allowed = List.of("npx", "uvx");
        assertThat(TurMcpToolCallbackService.isStdioCommandAllowed("npx", allowed)).isTrue();
        assertThat(TurMcpToolCallbackService.isStdioCommandAllowed("/usr/local/bin/NPX", allowed)).isTrue();
        assertThat(TurMcpToolCallbackService.isStdioCommandAllowed("C:\\tools\\uvx.exe", allowed)).isFalse();
        assertThat(TurMcpToolCallbackService.isStdioCommandAllowed("bash", allowed)).isFalse();
        assertThat(TurMcpToolCallbackService.isStdioCommandAllowed("/bin/sh", allowed)).isFalse();
    }

    private static TurMcpServerDomain serverDomain(String id, String title) {
        return serverDomain(id, title, null);
    }

    /**
     * (id, title, description, icon, url, command, args, type, connectionType,
     * transportType, authTokenEncrypted, enabled)
     */
    private static TurMcpServerDomain serverDomain(String id, String title,
            String authTokenEncrypted) {
        return new TurMcpServerDomain(id, title, null, null, "https://mcp.example.com/mcp", null,
                null, null, TurMcpServerConnectionType.HTTP,
                TurMcpServerTransportType.STREAMABLE_HTTP, authTokenEncrypted, 1);
    }

    @Test
    void getToolCallbacksWithNullSetShouldReturnEmpty() {
        ToolCallback[] callbacks = service.getToolCallbacks((Set<TurMcpServer>) null);
        assertThat(callbacks).isEmpty();
    }

    @Test
    void getToolCallbacksWithEmptySetShouldReturnEmpty() {
        ToolCallback[] callbacks = service.getToolCallbacks(new HashSet<>());
        assertThat(callbacks).isEmpty();
    }

    @Test
    void getToolCallbacksNoArgShouldReturnEmptyWhenNoEnabledServers() {
        when(mcpServerRepositoryPort.findAllEnabled()).thenReturn(Collections.emptyList());
        ToolCallback[] callbacks = service.getToolCallbacks();
        assertThat(callbacks).isEmpty();
    }

    @Test
    void getToolCallbacksWithFilterShouldFilterByAllowedIds() {
        // Only include mcp-1 in the filter set; mcp-2 must be excluded.
        TurMcpServer filterServer = new TurMcpServer();
        filterServer.setId("mcp-1");

        when(mcpServerRepositoryPort.findAllEnabled())
                .thenReturn(List.of(serverDomain("mcp-1", "Server 1"),
                        serverDomain("mcp-2", "Server 2")));

        // The transport setup will fail to connect to a real MCP server in
        // unit-test context — exercising the no-callbacks fall-back path.
        ToolCallback[] callbacks = service.getToolCallbacks(Set.of(filterServer));
        assertThat(callbacks).isEmpty();
    }

    @Test
    void cleanupShouldNotThrowWhenEmpty() {
        // Should not throw even with no cached clients
        assertDoesNotThrow(() -> service.cleanup());
    }

    // ---- T1032 / §D.12 — per-server credential -----------------------------

    /** Applies the customizer to a real request builder and reads the header back. */
    private String authorizationHeaderFor(TurMcpServerDomain server) {
        var customizer = service.authorizationCustomizer(server);
        if (customizer == null) {
            return null;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(server.url()));
        customizer.customize(builder, "POST", URI.create(server.url()), "{}", null);
        return builder.build().headers().firstValue("Authorization").orElse(null);
    }

    @Test
    @DisplayName("A stored token becomes an Authorization: Bearer header on the request")
    void storedTokenBecomesABearerHeader() {
        when(secretCryptoService.decrypt("cipher")).thenReturn("tok-123");

        assertThat(authorizationHeaderFor(serverDomain("mcp-1", "Hosted", "cipher")))
                .isEqualTo("Bearer tok-123");
    }

    @Test
    @DisplayName("A token stored with surrounding whitespace is trimmed, not sent as-is")
    void storedTokenIsTrimmed() {
        when(secretCryptoService.decrypt("cipher")).thenReturn("  tok-123\n");

        assertThat(authorizationHeaderFor(serverDomain("mcp-1", "Hosted", "cipher")))
                .isEqualTo("Bearer tok-123");
    }

    @Test
    @DisplayName("A server with no credential gets no customizer, so its request is unchanged")
    void serverWithoutTokenGetsNoCustomizer() {
        assertThat(service.authorizationCustomizer(serverDomain("mcp-1", "Anonymous", null)))
                .isNull();
        assertThat(service.authorizationCustomizer(serverDomain("mcp-1", "Anonymous", "  ")))
                .isNull();
    }

    @Test
    @DisplayName("A credential that will not decrypt yields no header rather than a broken one")
    void undecryptableTokenYieldsNoHeader() {
        when(secretCryptoService.decrypt("cipher"))
                .thenThrow(new IllegalStateException("key rotated"));

        assertThat(service.authorizationCustomizer(serverDomain("mcp-1", "Hosted", "cipher")))
                .isNull();
    }

    @Test
    @DisplayName("A credential that decrypts to nothing yields no header")
    void emptyDecryptedTokenYieldsNoHeader() {
        lenient().when(secretCryptoService.decrypt("cipher")).thenReturn("   ");

        assertThat(service.authorizationCustomizer(serverDomain("mcp-1", "Hosted", "cipher")))
                .isNull();
    }

    @Test
    @DisplayName("The connection fingerprint changes when the credential is rotated")
    void fingerprintTracksTheCredential() {
        String before = TurMcpToolCallbackService
                .connectionFingerprint(serverDomain("mcp-1", "Hosted", "cipher-old"));
        String after = TurMcpToolCallbackService
                .connectionFingerprint(serverDomain("mcp-1", "Hosted", "cipher-new"));

        assertThat(before).isNotEqualTo(after);
    }

    @Test
    @DisplayName("The fingerprint also tracks the URL and the transport, so an edit reconnects")
    void fingerprintTracksTheEndpoint() {
        var base = serverDomain("mcp-1", "Hosted", "cipher");
        var movedUrl = new TurMcpServerDomain("mcp-1", "Hosted", null, null,
                "https://elsewhere.example.com/mcp", null, null, null,
                TurMcpServerConnectionType.HTTP, TurMcpServerTransportType.STREAMABLE_HTTP,
                "cipher", 1);
        var otherTransport = new TurMcpServerDomain("mcp-1", "Hosted", null, null,
                "https://mcp.example.com/mcp", null, null, null,
                TurMcpServerConnectionType.HTTP, TurMcpServerTransportType.SSE, "cipher", 1);

        assertThat(TurMcpToolCallbackService.connectionFingerprint(base))
                .isNotEqualTo(TurMcpToolCallbackService.connectionFingerprint(movedUrl))
                .isNotEqualTo(TurMcpToolCallbackService.connectionFingerprint(otherTransport));
    }

    @Test
    @DisplayName("An unchanged server keeps the same fingerprint, so the client is reused")
    void fingerprintIsStableForAnUnchangedServer() {
        assertThat(TurMcpToolCallbackService
                .connectionFingerprint(serverDomain("mcp-1", "Hosted", "cipher")))
                .isEqualTo(TurMcpToolCallbackService
                        .connectionFingerprint(serverDomain("mcp-1", "Hosted", "cipher")));
    }

    @Test
    @DisplayName("hasAuthToken distinguishes a configured credential from a blank one")
    void hasAuthTokenIsHonest() {
        assertThat(serverDomain("mcp-1", "Hosted", "cipher").hasAuthToken()).isTrue();
        assertThat(serverDomain("mcp-1", "Hosted", null).hasAuthToken()).isFalse();
        assertThat(serverDomain("mcp-1", "Hosted", "   ").hasAuthToken()).isFalse();
    }
}
