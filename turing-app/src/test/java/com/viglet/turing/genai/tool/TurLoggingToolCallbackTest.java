package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Tests for TurLoggingToolCallback.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurLoggingToolCallbackTest {

    @Mock
    private ToolCallback delegate;
    @Mock
    private ToolDefinition toolDefinition;
    @Mock
    private ToolMetadata toolMetadata;

    private TurLoggingToolCallback callback;

    @BeforeEach
    void setUp() {
        callback = new TurLoggingToolCallback(delegate);
    }

    @Test
    void getToolDefinitionShouldDelegateToWrappedCallback() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        assertThat(callback.getToolDefinition()).isSameAs(toolDefinition);
    }

    @Test
    void getToolMetadataShouldDelegateToWrappedCallback() {
        when(delegate.getToolMetadata()).thenReturn(toolMetadata);
        assertThat(callback.getToolMetadata()).isSameAs(toolMetadata);
    }

    @Test
    void callShouldDelegateAndReturnResult() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input")).thenReturn("output");

        String result = callback.call("input");

        assertThat(result).isEqualTo("output");
        verify(delegate).call("input");
    }

    @Test
    void callShouldHandleNullResult() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input")).thenReturn(null);

        String result = callback.call("input");

        assertThat(result).isNull();
    }

    @Test
    void callShouldHandleLongResult() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        String longOutput = "x".repeat(600);
        when(delegate.call("input")).thenReturn(longOutput);

        String result = callback.call("input");

        assertThat(result).isEqualTo(longOutput);
    }

    @Test
    void callShouldRethrowException() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input")).thenThrow(new RuntimeException("tool error"));

        assertThatThrownBy(() -> callback.call("input"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("tool error");
    }

    @Test
    void callWithContextShouldDelegateAndReturnResult() {
        ToolContext context = mock(ToolContext.class);
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input", context)).thenReturn("output-ctx");

        String result = callback.call("input", context);

        assertThat(result).isEqualTo("output-ctx");
        verify(delegate).call("input", context);
    }

    @Test
    void callWithContextShouldHandleNullResult() {
        ToolContext context = mock(ToolContext.class);
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input", context)).thenReturn(null);

        String result = callback.call("input", context);

        assertThat(result).isNull();
    }

    @Test
    void callWithContextShouldHandleLongResult() {
        ToolContext context = mock(ToolContext.class);
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        String longOutput = "y".repeat(600);
        when(delegate.call("input", context)).thenReturn(longOutput);

        String result = callback.call("input", context);

        assertThat(result).isEqualTo(longOutput);
    }

    @Test
    void callWithContextShouldRethrowException() {
        ToolContext context = mock(ToolContext.class);
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input", context)).thenThrow(new RuntimeException("ctx error"));

        assertThatThrownBy(() -> callback.call("input", context))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ctx error");
    }

    @Test
    void wrapShouldWrapAllCallbacks() {
        ToolCallback cb1 = mock(ToolCallback.class);
        ToolCallback cb2 = mock(ToolCallback.class);
        ToolCallback cb3 = mock(ToolCallback.class);

        ToolCallback[] wrapped = TurLoggingToolCallback.wrap(new ToolCallback[]{cb1, cb2, cb3});

        assertThat(wrapped).hasSize(3);
        for (ToolCallback w : wrapped) {
            assertThat(w).isInstanceOf(TurLoggingToolCallback.class);
        }
    }

    @Test
    void wrapShouldReturnEmptyArrayForEmptyInput() {
        ToolCallback[] wrapped = TurLoggingToolCallback.wrap(new ToolCallback[0]);
        assertThat(wrapped).isEmpty();
    }

    @Test
    void callShouldHandleShortResult() {
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input")).thenReturn("short");

        String result = callback.call("input");

        assertThat(result).isEqualTo("short");
    }

    @Test
    void callWithContextShouldHandleShortResult() {
        ToolContext context = mock(ToolContext.class);
        when(delegate.getToolDefinition()).thenReturn(toolDefinition);
        when(toolDefinition.name()).thenReturn("test_tool");
        when(delegate.call("input", context)).thenReturn("short");

        String result = callback.call("input", context);

        assertThat(result).isEqualTo("short");
    }
}
