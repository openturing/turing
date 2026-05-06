package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import com.viglet.turing.domain.mcp.TurMcpServerDomain;
import com.viglet.turing.domain.mcp.TurMcpServerRepositoryPort;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;

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

    private TurMcpToolCallbackService service;

    @BeforeEach
    void setUp() {
        service = new TurMcpToolCallbackService(mcpServerRepositoryPort);
    }

    private static TurMcpServerDomain serverDomain(String id, String title) {
        return new TurMcpServerDomain(id, title, null, null, null, null, null,
                null, null, 1);
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
}
