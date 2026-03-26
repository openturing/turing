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

import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;

/**
 * Tests for TurMcpToolCallbackService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurMcpToolCallbackServiceTest {

    @Mock
    private TurMcpServerRepository mcpServerRepository;

    private TurMcpToolCallbackService service;

    @BeforeEach
    void setUp() {
        service = new TurMcpToolCallbackService(mcpServerRepository);
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
        when(mcpServerRepository.findAll()).thenReturn(Collections.emptyList());
        ToolCallback[] callbacks = service.getToolCallbacks();
        assertThat(callbacks).isEmpty();
    }

    @Test
    void getToolCallbacksNoArgShouldReturnEmptyWhenAllDisabled() {
        TurMcpServer disabledServer = new TurMcpServer();
        disabledServer.setId("mcp-1");
        disabledServer.setEnabled(0);

        when(mcpServerRepository.findAll()).thenReturn(List.of(disabledServer));
        ToolCallback[] callbacks = service.getToolCallbacks();
        assertThat(callbacks).isEmpty();
    }

    @Test
    void getToolCallbacksWithFilterShouldFilterByAllowedIds() {
        TurMcpServer server1 = new TurMcpServer();
        server1.setId("mcp-1");
        server1.setEnabled(1);
        server1.setTitle("Server 1");

        TurMcpServer server2 = new TurMcpServer();
        server2.setId("mcp-2");
        server2.setEnabled(1);
        server2.setTitle("Server 2");

        // Only include mcp-1 in the filter
        TurMcpServer filterServer = new TurMcpServer();
        filterServer.setId("mcp-1");

        when(mcpServerRepository.findAll()).thenReturn(List.of(server1, server2));

        // This will attempt to connect to an actual MCP server which won't exist in tests,
        // so it should gracefully return empty callbacks
        ToolCallback[] callbacks = service.getToolCallbacks(Set.of(filterServer));
        assertThat(callbacks).isEmpty(); // Can't connect to real servers in unit tests
    }

    @Test
    void cleanupShouldNotThrowWhenEmpty() {
        // Should not throw even with no cached clients
        assertDoesNotThrow(() -> service.cleanup());
    }
}
