package com.viglet.turing.connector.sprinklr.commons.tools;

import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrKeyValueTransformer;
import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import lombok.extern.slf4j.Slf4j;

/**
 * KeyValueTransformerTool is a tool that transforms a value into another. It uses the `TurSprinklrKeyValueTransformer`
 * plugin to perform the transformation. Use this tool in your custom classes extensions.
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * KeyValueTransformerTool transformerTool = new KeyValueTransformerTool(pluginContext);
 * transformerTool.configure(TurSprinklrNotFoundAction.DEFAULT_VALUE, "Key not found");
 * String transformedValue = transformerTool.transform("someKey", "mappingFile.txt");
 * }
 * </pre>
 * 
 * <p>Configuration options for handling keys not found in the mapping:</p>
 * <ul>
 *   <li>{@link TurSprinklrNotFoundAction#DEFAULT_VALUE} - Returns a default text when the key is not found.</li>
 *   <li>{@link TurSprinklrNotFoundAction#KEEP_KEY} - Returns the key itself when it is not found in the mapping.</li>
 *  <li>{@link TurSprinklrNotFoundAction#NULL} - Returns null when the key is not found.</li>
 * </ul>
 * 
 * <p>By default, the action is set to {@link TurSprinklrNotFoundAction#KEEP_KEY}.</p>
 * 
 * @see TurSprinklrPluginContext
 * @see TurSprinklrKeyValueTransformer
 * @see TurSprinklrNotFoundAction
 * 
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@Slf4j
public class KeyValueTransformerTool {
    private final TurSprinklrPluginContext pluginContext;

    // See treatNotFound method
    TurSprinklrNotFoundAction notFoundAction = TurSprinklrNotFoundAction.NULL;
    String defaultNotFoundText = "Key Value Transformer Plugin: Key not found";

    /**
     * Constructs a new KeyValueTransformerTool with the specified plugin context.
     * See {@link TurSprinklrPluginContext}.
     *
     * @param pluginContext the context of the TurSprinklr plugin
     */
    public KeyValueTransformerTool(TurSprinklrPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }
    /**
     * Transforms a key into a value using the `KeyValueTransformer` and the specified file.
     *
     * @param key The key that will be transformed. Key can be null.
     * @param file The name of the file containing the mapping.
     * @return The transformed value, or if the key is not found the return value will depend on the configuration.
     * @Throws IllegalArgumentException if the file is not found.
     */
    public String transform(String key, String file) {
        // Retrieves the KeyValueTransformer plugin
        TurSprinklrKeyValueTransformer transformer = pluginContext.getPlugin(TurSprinklrKeyValueTransformer.class);
        if (transformer == null) {
            log.error("KeyValueTransformer not found in PluginContext.");
            return null;
        }
        if (key == null) {
            log.warn("Key is Null");
        }
        transformer.loadMapping(file);
        // Perform the transformation
        var transformed = transformer.transform(key, file);

        if (transformed == null) {
            return treatNotFound(key, file);
        }
        return transformed;
    }


    private String treatNotFound(String key, String file) {
        log.warn("Key not found: {} in file: {}", key, file);
        return switch (notFoundAction) {
            case DEFAULT_VALUE -> defaultNotFoundText;
            case KEEP_KEY -> key;
            default -> null;
        };
    }

    /**
     * Configures the action to be taken when a key is not found and sets the default text for such cases.
     *
     * @param action the action to be taken when a key is not found
     */
    public void configure(TurSprinklrNotFoundAction action) {
        notFoundAction = action;
    }

    /**
     * Configures the action to be taken when a key is not found and sets the default text for such cases.
     *
     * @param action the action to be taken when a key is not found
     * @param defaultText the default text to be used when a key is not found if action is DEFAULT_VALUE
     */
    public void configure(TurSprinklrNotFoundAction action, String defaultText) {
        notFoundAction = action;
        defaultNotFoundText = defaultText;
    }

}
