package com.viglet.turing.genai.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

/**
 * Decorator that overrides a {@link ToolCallback}'s description with content
 * loaded from a {@code .md} file via {@link TurToolDescriptionService}.
 * <p>
 * If no override exists for a tool, the original description is preserved.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public class TurToolDescriptionCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolDefinition overriddenDefinition;

    private TurToolDescriptionCallback(ToolCallback delegate, String newDescription) {
        this.delegate = delegate;
        ToolDefinition original = delegate.getToolDefinition();
        this.overriddenDefinition = DefaultToolDefinition.builder()
                .name(original.name())
                .description(newDescription)
                .inputSchema(original.inputSchema())
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return overriddenDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(toolInput, toolContext);
    }

    /**
     * Wraps an array of callbacks, replacing descriptions where .md files exist.
     */
    public static ToolCallback[] wrap(ToolCallback[] callbacks, TurToolDescriptionService descriptionService) {
        ToolCallback[] result = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            String toolName = callbacks[i].getToolDefinition().name();
            String override = descriptionService.getDescription(toolName);
            result[i] = override != null
                    ? new TurToolDescriptionCallback(callbacks[i], override)
                    : callbacks[i];
        }
        return result;
    }
}
